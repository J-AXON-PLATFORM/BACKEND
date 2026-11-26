package com.jaxon.discovery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DiscoveryResultIngestionService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    DiscoveryResultIngestionService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    IngestionOutcome ingest(
            UUID pathExecutorId,
            UUID pathJobId,
            String sourcePath,
            String sha256,
            long size,
            DiscoveryResultMessage message) {
        lockPath(sourcePath);
        if (previousStatus(sourcePath, sha256).isPresent()) return IngestionOutcome.ALREADY_PROCESSED;
        if (!pathExecutorId.equals(message.executorId())) {
            return reject(pathExecutorId, pathJobId, sourcePath, sha256, size,
                    "PATH_EXECUTOR_ID_MISMATCH", message.resultId(), message.jobId());
        }
        if (!pathJobId.equals(message.jobId())) {
            return reject(pathExecutorId, pathJobId, sourcePath, sha256, size,
                    "PATH_JOB_ID_MISMATCH", message.resultId(), message.jobId());
        }
        if (message.resultId().equals(message.jobId())
                || message.resultId().equals(message.executorId())
                || message.resultId().equals(message.scopeId())) {
            return reject(pathExecutorId, pathJobId, sourcePath, sha256, size,
                    "RESULT_IDENTIFIERS_NOT_INDEPENDENT", message.resultId(), message.jobId());
        }

        for (String key : List.of("job:" + message.jobId(), "result:" + message.resultId())
                .stream().sorted().toList()) {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", resultSet -> null, key);
        }
        ExistingResult existing = findExisting(message).orElse(null);
        if (existing != null) {
            if (existing.resultId().equals(message.resultId())
                    && existing.jobId().equals(message.jobId())
                    && existing.sha256().equals(sha256)) {
                record(pathExecutorId, pathJobId, sourcePath, sha256, size, "ACCEPTED", null,
                        message.resultId(), message.jobId());
                return IngestionOutcome.DUPLICATE;
            }
            record(pathExecutorId, pathJobId, sourcePath, sha256, size, "CONFLICT",
                    "RESULT_IDENTIFIER_CONFLICT", message.resultId(), message.jobId());
            return IngestionOutcome.CONFLICT;
        }

        JobRow job = findJob(message.jobId()).orElse(null);
        if (job == null) {
            return reject(pathExecutorId, pathJobId, sourcePath, sha256, size,
                    "JOB_NOT_FOUND", message.resultId(), message.jobId());
        }
        if (!job.executorId().equals(message.executorId())
                || !job.scopeId().equals(message.scopeId())
                || !job.profile().equals(message.profile())) {
            return reject(pathExecutorId, pathJobId, sourcePath, sha256, size,
                    "RESULT_JOB_MISMATCH", message.resultId(), message.jobId());
        }
        if (job.status() != DiscoveryJobStatus.DISPATCHED || job.version() != 1) {
            return reject(pathExecutorId, pathJobId, sourcePath, sha256, size,
                    "JOB_TERMINAL_OR_NOT_DISPATCHED", message.resultId(), message.jobId());
        }
        Instant receivedAt = now();
        if (message.startedAt().isBefore(job.dispatchedAt())
                || !message.startedAt().isBefore(job.expiresAt())
                || message.completedAt().isAfter(receivedAt)
                || expectedTargets(job.scopeCidr()).compareTo(message.summary().targetsConsidered()) != 0
                || !observationsBelongToScope(message.observations(), job.scopeCidr())) {
            return reject(pathExecutorId, pathJobId, sourcePath, sha256, size,
                    "RESULT_SEMANTICS_INVALID", message.resultId(), message.jobId());
        }

        UUID ingestionId = UUID.randomUUID();
        int ledger = record(pathExecutorId, pathJobId, sourcePath, sha256, size, "ACCEPTED", null,
                message.resultId(), message.jobId(), ingestionId, receivedAt);
        if (ledger != 1) return IngestionOutcome.ALREADY_PROCESSED;

        jdbc.update(
                """
                INSERT INTO discovery.accepted_discovery_results
                    (result_id, job_id, ingestion_id, executor_id, scope_id, schema_version,
                     profile, result_status, failure_code, started_at, completed_at, received_at,
                     content_sha256, content_size, targets_considered, targets_scanned,
                     observations_found)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                message.resultId(), message.jobId(), ingestionId, message.executorId(), message.scopeId(),
                message.schemaVersion(), message.profile(), message.status(), message.failureCode(),
                Timestamp.from(message.startedAt()), Timestamp.from(message.completedAt()),
                Timestamp.from(receivedAt), sha256, size, message.summary().targetsConsidered(),
                message.summary().targetsScanned(), message.summary().observationsFound());

        if ("SUCCEEDED".equals(message.status())) {
            for (int index = 0; index < message.observations().size(); index++) {
                DiscoveryResultMessage.Observation observation = message.observations().get(index);
                jdbc.update(
                        """
                        INSERT INTO discovery.discovered_devices
                            (discovered_device_id, result_id, job_id, result_status,
                             observation_index, status,
                             ip_address, mac_address, hostname, evidence, open_tcp_ports,
                             observed_at, created_at)
                        VALUES (?, ?, ?, 'SUCCEEDED', ?, 'UNREVIEWED', ?::inet, ?::macaddr, ?,
                                ?::varchar[], ?::integer[], ?, ?)
                        """,
                        UUID.randomUUID(), message.resultId(), message.jobId(), index,
                        observation.ipAddress(), observation.macAddress(), observation.hostname(),
                        postgresArray(observation.evidence()), postgresArray(observation.openTcpPorts()),
                        Timestamp.from(message.completedAt()), Timestamp.from(receivedAt));
            }
        }

        int updated = jdbc.update(
                """
                UPDATE discovery.discovery_jobs
                SET status = ?, started_at = ?, completed_at = ?, failure_code = ?,
                    targets_considered = ?, targets_scanned = ?, observations_found = ?,
                    updated_at = ?, version = 2
                WHERE job_id = ? AND status = 'DISPATCHED' AND version = 1
                """,
                message.status(), Timestamp.from(message.startedAt()), Timestamp.from(message.completedAt()),
                message.failureCode(), message.summary().targetsConsidered(),
                message.summary().targetsScanned(), message.summary().observationsFound(),
                Timestamp.from(receivedAt), message.jobId());
        if (updated != 1) throw new IllegalStateException("Discovery Job changed concurrently");

        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'DISCOVERY_JOB_COMPLETED', '2.0', ?, 'DISCOVERY',
                        'DISCOVERY_EXECUTOR', ?, 'DISCOVERY_JOB', ?, 2,
                        ?, ?, '{}'::jsonb, ?)
                """,
                UUID.randomUUID(), Timestamp.from(receivedAt), message.executorId(), message.jobId(),
                "SUCCEEDED".equals(message.status()) ? "SUCCESS" : "FAILED", job.correlationId(),
                Timestamp.from(receivedAt));
        return IngestionOutcome.ACCEPTED;
    }

    @Transactional
    IngestionOutcome reject(
            UUID pathExecutorId,
            UUID pathJobId,
            String sourcePath,
            String sha256,
            long size,
            String reason) {
        lockPath(sourcePath);
        return reject(pathExecutorId, pathJobId, sourcePath, sha256, size, reason, null, null);
    }

    private IngestionOutcome reject(
            UUID pathExecutorId, UUID pathJobId, String sourcePath, String sha256, long size,
            String reason, UUID resultId, UUID jobId) {
        if (previousStatus(sourcePath, sha256).isPresent()) return IngestionOutcome.ALREADY_PROCESSED;
        if (acceptedFingerprint(sourcePath).filter(accepted -> !accepted.equals(sha256)).isPresent()) {
            int inserted = record(pathExecutorId, pathJobId, sourcePath, sha256, size, "CONFLICT",
                    "RESULT_CONTENT_CONFLICT", resultId, jobId);
            return inserted == 1 ? IngestionOutcome.CONFLICT : IngestionOutcome.ALREADY_PROCESSED;
        }
        int inserted = record(pathExecutorId, pathJobId, sourcePath, sha256, size, "REJECTED",
                reason, resultId, jobId);
        return inserted == 1 ? IngestionOutcome.REJECTED : IngestionOutcome.ALREADY_PROCESSED;
    }

    private int record(
            UUID pathExecutorId, UUID pathJobId, String sourcePath, String sha256, long size,
            String status, String reason, UUID resultId, UUID jobId) {
        return record(pathExecutorId, pathJobId, sourcePath, sha256, size, status, reason,
                resultId, jobId, UUID.randomUUID(), now());
    }

    private int record(
            UUID pathExecutorId, UUID pathJobId, String sourcePath, String sha256, long size,
            String status, String reason, UUID resultId, UUID jobId, UUID ingestionId,
            Instant receivedAt) {
        return jdbc.update(
                """
                INSERT INTO discovery.discovery_result_ingestions
                    (ingestion_id, path_executor_id, path_job_id, source_path, file_fingerprint,
                     file_size, received_at, status, reason_code, claimed_result_id, claimed_job_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_path, file_fingerprint) DO NOTHING
                """,
                ingestionId, pathExecutorId, pathJobId, sourcePath, sha256, size,
                Timestamp.from(receivedAt), status, reason, resultId, jobId);
    }

    private Optional<String> previousStatus(String sourcePath, String sha256) {
        return jdbc.query(
                        "SELECT status FROM discovery.discovery_result_ingestions "
                                + "WHERE source_path = ? AND file_fingerprint = ?",
                        (resultSet, row) -> resultSet.getString(1), sourcePath, sha256)
                .stream().findFirst();
    }

    private void lockPath(String sourcePath) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", resultSet -> null,
                "discovery-result-path:" + sourcePath);
    }

    private Optional<String> acceptedFingerprint(String sourcePath) {
        return jdbc.query(
                        """
                        SELECT accepted.content_sha256
                        FROM discovery.discovery_result_ingestions ingestion
                        JOIN discovery.accepted_discovery_results accepted
                          ON accepted.ingestion_id = ingestion.ingestion_id
                        WHERE ingestion.source_path = ? AND ingestion.status = 'ACCEPTED'
                        """,
                        (resultSet, row) -> resultSet.getString(1), sourcePath)
                .stream().findFirst();
    }

    private Optional<ExistingResult> findExisting(DiscoveryResultMessage message) {
        return jdbc.query(
                        """
                        SELECT result_id, job_id, content_sha256
                        FROM discovery.accepted_discovery_results
                        WHERE result_id = ? OR job_id = ?
                        """,
                        (resultSet, row) -> new ExistingResult(
                                resultSet.getObject("result_id", UUID.class),
                                resultSet.getObject("job_id", UUID.class),
                                resultSet.getString("content_sha256")),
                        message.resultId(), message.jobId())
                .stream().findFirst();
    }

    private Optional<JobRow> findJob(UUID jobId) {
        return jdbc.query(
                        """
                        SELECT job.*, scope.cidr::text AS scope_cidr
                        FROM discovery.discovery_jobs job
                        JOIN discovery.authorized_scopes scope ON scope.id = job.scope_id
                        WHERE job.job_id = ?
                        FOR UPDATE OF job
                        """,
                        DiscoveryResultIngestionService::jobRow,
                        jobId)
                .stream().findFirst();
    }

    private java.math.BigInteger expectedTargets(String cidr) {
        return jdbc.queryForObject(
                "SELECT power(2::numeric, (CASE family(?::inet) WHEN 4 THEN 32 ELSE 128 END) - masklen(?::inet))",
                java.math.BigDecimal.class, cidr, cidr).toBigIntegerExact();
    }

    private boolean observationsBelongToScope(
            List<DiscoveryResultMessage.Observation> observations, String cidr) {
        for (DiscoveryResultMessage.Observation observation : observations) {
            Boolean valid = jdbc.queryForObject(
                    "SELECT ?::inet <<= ?::inet AND host(?::inet) = ?",
                    Boolean.class, observation.ipAddress(), cidr,
                    observation.ipAddress(), observation.ipAddress());
            if (!Boolean.TRUE.equals(valid)) return false;
        }
        return true;
    }

    private static JobRow jobRow(ResultSet resultSet, int ignored) throws SQLException {
        return new JobRow(
                resultSet.getObject("executor_id", UUID.class),
                resultSet.getObject("scope_id", UUID.class),
                resultSet.getString("scope_cidr"),
                resultSet.getString("profile"),
                DiscoveryJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("correlation_id", UUID.class),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getTimestamp("dispatched_at").toInstant(),
                resultSet.getLong("version"));
    }

    private static String postgresArray(List<?> values) {
        return "{" + values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")) + "}";
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    enum IngestionOutcome { ACCEPTED, REJECTED, CONFLICT, DUPLICATE, ALREADY_PROCESSED }

    private record ExistingResult(UUID resultId, UUID jobId, String sha256) {}
    private record JobRow(UUID executorId, UUID scopeId, String scopeCidr, String profile,
            DiscoveryJobStatus status, UUID correlationId, Instant expiresAt,
            Instant dispatchedAt, long version) {}
}
