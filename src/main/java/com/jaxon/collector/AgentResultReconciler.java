package com.jaxon.collector;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class AgentResultReconciler {

    private static final Pattern MAILBOX = Pattern.compile(
            "^agent-([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})-responses$");
    private static final Pattern RESULT = Pattern.compile(
            "^([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\\.json$");

    private final Path mailboxRoot;
    private final Duration stabilityAge;
    private final int batchSize;
    private final AgentResultFileReader fileReader;
    private final AgentTaskContract contract;
    private final AgentResultIngestionService ingestion;
    private final AgentTaskMetrics metrics;
    private final Clock clock;

    AgentResultReconciler(
            @Value("${jaxon.collector.task-results.mailbox-root:}") String mailboxRoot,
            @Value("${jaxon.collector.task-results.stability-age:2s}") Duration stabilityAge,
            @Value("${jaxon.collector.task-results.batch-size:100}") int batchSize,
            AgentResultFileReader fileReader,
            AgentTaskContract contract,
            AgentResultIngestionService ingestion,
            AgentTaskMetrics metrics,
            Clock clock) {
        this.mailboxRoot = mailboxRoot.isBlank() ? null : Path.of(mailboxRoot).toAbsolutePath().normalize();
        this.stabilityAge = stabilityAge;
        this.batchSize = batchSize;
        this.fileReader = fileReader;
        this.contract = contract;
        this.ingestion = ingestion;
        this.metrics = metrics;
        this.clock = clock;
        if (batchSize < 1 || batchSize > 1000 || stabilityAge.isNegative()) {
            throw new IllegalArgumentException("Invalid task result reconciliation limits");
        }
    }

    void reconcile() {
        if (mailboxRoot == null) {
            return;
        }
        try {
            scan();
        } catch (IOException exception) {
            metrics.reconciliation("scan_error");
        }
    }

    private void scan() throws IOException {
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
                        Matcher result = RESULT.matcher(file.getFileName().toString());
                        if (result.matches()) {
                            process(agentId, UUID.fromString(result.group(1)), file);
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

    private void process(UUID agentId, UUID messageId, Path file) {
        try {
            AgentResultFileReader.ReadResult read =
                    fileReader.read(mailboxRoot, file, stabilityAge, Instant.now(clock));
            AgentResultMessage message;
            try {
                message = contract.validateResult(read.json());
            } catch (AgentTaskContract.InvalidAgentMessageException exception) {
                ingestion.reject(agentId, messageId, relative(file), read.sha256(), read.size(), exception.reasonCode());
                return;
            }
            ingestion.ingest(agentId, messageId, relative(file), read.sha256(), read.size(), message);
        } catch (AgentResultFileReader.ResultNotStableException exception) {
            metrics.reconciliation("not_stable");
        } catch (AgentResultFileReader.UnsafeResultFileException exception) {
            rejectMetadata(agentId, messageId, file, exception.reasonCode());
        } catch (RuntimeException exception) {
            metrics.reconciliation("error");
        }
    }

    private void rejectMetadata(UUID agentId, UUID messageId, Path file, String reason) {
        try {
            BasicFileAttributes attributes =
                    Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            String fingerprint = SnapshotFileReader.metadataFingerprint(
                    attributes.size(), attributes.lastModifiedTime().toInstant());
            ingestion.reject(agentId, messageId, relative(file), fingerprint, attributes.size(), reason);
        } catch (IOException exception) {
            metrics.reconciliation("rejected");
        }
    }

    private String relative(Path path) {
        return mailboxRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}
