package com.jaxon.collector;

import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SnapshotReconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotReconciler.class);
    private static final Pattern MAILBOX = Pattern.compile(
            "^agent-([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})-snapshots$");
    private static final Pattern SNAPSHOT = Pattern.compile(
            "^([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\\.json$");

    private final Path mailboxRoot;
    private final Duration stabilityAge;
    private final int batchSize;
    private final SnapshotFileReader fileReader;
    private final SnapshotContractValidator contractValidator;
    private final SnapshotIngestionService ingestionService;
    private final CollectorMetrics metrics;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    SnapshotReconciler(
            @Value("${jaxon.collector.snapshot-ingestion.mailbox-root:}") String mailboxRoot,
            @Value("${jaxon.collector.snapshot-ingestion.stability-age:2s}") Duration stabilityAge,
            @Value("${jaxon.collector.snapshot-ingestion.batch-size:100}") int batchSize,
            SnapshotFileReader fileReader,
            SnapshotContractValidator contractValidator,
            SnapshotIngestionService ingestionService,
            CollectorMetrics metrics,
            Clock clock) {
        this.mailboxRoot = mailboxRoot.isBlank() ? null : Path.of(mailboxRoot).toAbsolutePath().normalize();
        this.stabilityAge = stabilityAge;
        this.batchSize = batchSize;
        this.fileReader = fileReader;
        this.contractValidator = contractValidator;
        this.ingestionService = ingestionService;
        this.metrics = metrics;
        this.clock = clock;
        if (batchSize < 1 || batchSize > 1000 || stabilityAge.isNegative()) {
            throw new IllegalArgumentException("Invalid snapshot reconciliation limits");
        }
    }

    @Scheduled(
            initialDelayString = "${jaxon.collector.snapshot-ingestion.reconciliation-interval:4m}",
            fixedDelayString = "${jaxon.collector.snapshot-ingestion.reconciliation-interval:4m}")
    void reconcile() {
        if (mailboxRoot == null || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            scanAndProcess();
        } catch (IOException exception) {
            metrics.outcome("scan_error");
            LOGGER.warn("Collector snapshot reconciliation could not scan the configured mailbox root");
        } finally {
            running.set(false);
        }
    }

    private void scanAndProcess() throws IOException {
        if (Files.isSymbolicLink(mailboxRoot)
                || !Files.isDirectory(mailboxRoot, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(mailboxRoot)) {
            throw new IOException("Mailbox root is not a readable real directory");
        }
        int processed = 0;
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(mailboxRoot)) {
            for (Path directory : directories) {
                Matcher mailbox = MAILBOX.matcher(directory.getFileName().toString());
                if (!mailbox.matches()
                        || Files.isSymbolicLink(directory)
                        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                UUID agentId = UUID.fromString(mailbox.group(1));
                try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                    for (Path file : files) {
                        if (file.getFileName().toString().endsWith(".part")) {
                            continue;
                        }
                        Matcher snapshot = SNAPSHOT.matcher(file.getFileName().toString());
                        if (snapshot.matches()
                                && process(new Candidate(agentId, UUID.fromString(snapshot.group(1)), file))) {
                            processed++;
                            if (processed >= batchSize) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean process(Candidate candidate) {
        Timer.Sample sample = metrics.start();
        try {
            SnapshotFileReader.ReadSnapshot read = fileReader.read(
                    mailboxRoot, candidate.path(), stabilityAge, Instant.now(clock));
            SnapshotMessage message;
            try {
                message = contractValidator.validate(read.json());
            } catch (SnapshotContractValidator.InvalidSnapshotException exception) {
                recordRejection(candidate, read.sha256(), read.size(), exception.reasonCode());
                return true;
            }
            SnapshotIngestionService.IngestionOutcome outcome = ingestionService.ingest(
                    candidate.agentId(),
                    candidate.messageId(),
                    relativePath(candidate.path()),
                    read.sha256(),
                    read.size(),
                    message);
            metric(outcome);
            return outcome != SnapshotIngestionService.IngestionOutcome.ALREADY_PROCESSED;
        } catch (SnapshotFileReader.SnapshotNotStableException exception) {
            metrics.outcome("not_stable");
            return true;
        } catch (SnapshotFileReader.UnsafeSnapshotFileException exception) {
            rejectFromMetadata(candidate, exception.reasonCode());
            return true;
        } catch (RuntimeException exception) {
            metrics.outcome("error");
            LOGGER.warn("Collector snapshot reconciliation failed safely for one candidate");
            return true;
        } finally {
            metrics.stop(sample);
        }
    }

    private void rejectFromMetadata(Candidate candidate, String reason) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    candidate.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            String fingerprint = SnapshotFileReader.metadataFingerprint(
                    attributes.size(), attributes.lastModifiedTime().toInstant());
            recordRejection(candidate, fingerprint, attributes.size(), reason);
        } catch (IOException exception) {
            metrics.outcome("rejected");
        }
    }

    private void recordRejection(Candidate candidate, String sha256, long size, String reason) {
        SnapshotIngestionService.IngestionOutcome outcome = ingestionService.reject(
                candidate.agentId(),
                candidate.messageId(),
                relativePath(candidate.path()),
                sha256,
                size,
                reason);
        metric(outcome);
    }

    private String relativePath(Path path) {
        return mailboxRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private void metric(SnapshotIngestionService.IngestionOutcome outcome) {
        switch (outcome) {
            case ACCEPTED -> metrics.outcome("accepted");
            case REJECTED -> metrics.outcome("rejected");
            case CONFLICT -> metrics.outcome("conflict");
            case DUPLICATE -> metrics.outcome("duplicate");
            case ALREADY_PROCESSED -> {
                // Previously classified files remain read-only in the mailbox and are silent on later scans.
            }
        }
    }

    private record Candidate(UUID agentId, UUID messageId, Path path) {}
}
