package com.jaxon.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class DiscoveryResultReconciler {

    private static final Pattern CLAIM = Pattern.compile(
            "^discovery-executor-([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})-results\\."
                    + "([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\\."
                    + "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.json$");
    private final Path mailboxRoot;
    private final Duration stabilityAge;
    private final int batchSize;
    private final DiscoveryResultFileReader fileReader;
    private final DiscoveryResultContract contract;
    private final DiscoveryResultIngestionService ingestion;
    private final DiscoverySftpGoProvisioner sftpGo;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    DiscoveryResultReconciler(
            @Value("${jaxon.discovery.results.mailbox-root:}") String mailboxRoot,
            @Value("${jaxon.discovery.results.stability-age:2s}") Duration stabilityAge,
            @Value("${jaxon.discovery.results.batch-size:100}") int batchSize,
            DiscoveryResultFileReader fileReader,
            DiscoveryResultContract contract,
            DiscoveryResultIngestionService ingestion,
            DiscoverySftpGoProvisioner sftpGo,
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            Clock clock) {
        this.mailboxRoot = mailboxRoot.isBlank() ? null : Path.of(mailboxRoot).toAbsolutePath().normalize();
        this.stabilityAge = stabilityAge;
        this.batchSize = batchSize;
        this.fileReader = fileReader;
        this.contract = contract;
        this.ingestion = ingestion;
        this.sftpGo = sftpGo;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
        if (batchSize < 1 || batchSize > 1000 || stabilityAge.isNegative()) {
            throw new IllegalArgumentException("Invalid Discovery Result reconciliation limits");
        }
    }

    void reconcile() {
        if (mailboxRoot == null) return;
        try {
            scan();
        } catch (IOException ignored) {
            // A later bounded reconciliation retries transient mailbox failures.
        }
    }

    private void scan() throws IOException {
        if (Files.isSymbolicLink(mailboxRoot)
                || !Files.isDirectory(mailboxRoot, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(mailboxRoot)) {
            throw new IOException("Discovery result root is not a readable real directory");
        }
        scanClaims();
        for (Candidate candidate : candidates()) {
            Path mailbox = mailboxRoot.resolve(
                    "discovery-executor-" + candidate.executorId() + "-results");
            Path file = mailbox.resolve(candidate.jobId() + ".json");
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Path claimed = claim(candidate, mailbox.getFileName().toString());
                    if (claimed != null && sftpGo.startResultQuotaScan(candidate.executorId())) {
                        process(candidate.executorId(), candidate.jobId(),
                                sourcePath(mailbox, candidate.jobId()), claimed);
                    }
                } catch (IOException ignored) {
                    // A concurrent reconciler may have claimed this candidate first.
                }
            }
            jdbc.update("UPDATE discovery.discovery_jobs SET result_checked_at = ? WHERE job_id = ?",
                    Timestamp.from(Instant.now(clock)), candidate.jobId());
        }
    }

    private void scanClaims() throws IOException {
        for (Path file : fileReader.claims(mailboxRoot, batchSize)) {
            Matcher result = CLAIM.matcher(file.getFileName().toString());
            if (!result.matches()) continue;
            UUID executorId = UUID.fromString(result.group(1));
            UUID jobId = UUID.fromString(result.group(2));
            if (sftpGo.startResultQuotaScan(executorId)) {
                process(executorId, jobId, sourcePath(executorId, jobId), file);
            }
        }
    }

    private java.util.List<Candidate> candidates() {
        return jdbc.query(
                """
                SELECT job_id, executor_id
                FROM discovery.discovery_jobs
                WHERE status IN ('DISPATCHED', 'SUCCEEDED', 'FAILED', 'EXPIRED')
                ORDER BY result_checked_at NULLS FIRST, result_checked_at, job_id
                LIMIT ?
                """,
                (resultSet, row) -> new Candidate(
                        resultSet.getObject("job_id", UUID.class),
                        resultSet.getObject("executor_id", UUID.class)),
                batchSize);
    }

    private Path claim(Candidate candidate, String mailbox) throws IOException {
        try {
            return transactions.execute(status -> {
                String jobStatus = jdbc.query(
                                "SELECT status FROM discovery.discovery_jobs WHERE job_id = ? FOR UPDATE",
                                (resultSet, row) -> resultSet.getString(1), candidate.jobId())
                        .stream().findFirst().orElse(null);
                if (!java.util.Set.of("DISPATCHED", "SUCCEEDED", "FAILED", "EXPIRED")
                        .contains(jobStatus)) return null;
                String fileName = candidate.jobId() + ".json";
                String claimName = mailbox + "." + candidate.jobId() + "."
                        + UUID.randomUUID() + ".json";
                try {
                    Path claimed = fileReader.claim(mailboxRoot, mailbox, fileName, claimName);
                    return claimed;
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private boolean process(UUID executorId, UUID jobId, String sourcePath, Path file) {
        try {
            DiscoveryResultFileReader.ReadResult read =
                    fileReader.read(mailboxRoot, file, stabilityAge, Instant.now(clock));
            try {
                boolean processed = ingestion.ingest(executorId, jobId, sourcePath, read.sha256(), read.size(),
                                contract.validate(read.json()))
                        != DiscoveryResultIngestionService.IngestionOutcome.ALREADY_PROCESSED;
                fileReader.delete(mailboxRoot, file);
                return processed;
            } catch (InvalidDiscoveryResultException exception) {
                boolean processed = ingestion.reject(executorId, jobId, sourcePath, read.sha256(), read.size(),
                                 exception.reasonCode())
                        != DiscoveryResultIngestionService.IngestionOutcome.ALREADY_PROCESSED;
                fileReader.delete(mailboxRoot, file);
                return processed;
            }
        } catch (DiscoveryResultFileReader.ResultNotStableException ignored) {
            // The file remains eligible for the next reconciliation.
            return false;
        } catch (DiscoveryResultFileReader.UnsafeResultFileException exception) {
            Optional<Boolean> processed = rejectMetadata(
                    executorId, jobId, sourcePath, file, exception.reasonCode());
            if (processed.isPresent()) {
                try {
                    fileReader.delete(mailboxRoot, file);
                } catch (IOException ignored) {
                    // The durable ledger makes a later deletion retry idempotent.
                }
            }
            return processed.orElse(false);
        } catch (IOException | RuntimeException ignored) {
            // Fail closed for this file without preventing the next bounded scan.
            return false;
        }
    }

    private Optional<Boolean> rejectMetadata(
            UUID executorId, UUID jobId, String sourcePath, Path file, String reason) {
        try {
            BasicFileAttributes attributes = fileReader.attributes(mailboxRoot, file);
            return Optional.of(ingestion.reject(executorId, jobId, sourcePath,
                             DiscoveryResultFileReader.metadataFingerprint(
                                     attributes.size(), attributes.lastModifiedTime().toInstant()),
                             attributes.size(), reason)
                    != DiscoveryResultIngestionService.IngestionOutcome.ALREADY_PROCESSED);
        } catch (IOException ignored) {
            // No trustworthy metadata is available to persist.
            return Optional.empty();
        }
    }

    private String sourcePath(Path mailbox, UUID jobId) {
        return mailbox.getFileName() + "/" + jobId + ".json";
    }

    private String sourcePath(UUID executorId, UUID jobId) {
        return "discovery-executor-" + executorId + "-results/" + jobId + ".json";
    }

    private record Candidate(UUID jobId, UUID executorId) {}
}
