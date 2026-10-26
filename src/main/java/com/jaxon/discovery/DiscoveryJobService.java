package com.jaxon.discovery;

import com.jaxon.identity.DiscoveryExecutorIdentityRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class DiscoveryJobService {

    static final Duration JOB_TTL = Duration.ofHours(1);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final DiscoveryExecutorIdentityRegistry identities;
    private final DiscoverySftpGoProvisioner provisioner;
    private final DiscoveryJobContract contract;
    private final DiscoveryJobFilePublisher publisher;

    DiscoveryJobService(
            JdbcTemplate jdbc,
            Clock clock,
            TransactionTemplate transactions,
            DiscoveryExecutorIdentityRegistry identities,
            DiscoverySftpGoProvisioner provisioner,
            DiscoveryJobContract contract,
            DiscoveryJobFilePublisher publisher) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactions = transactions;
        this.identities = identities;
        this.provisioner = provisioner;
        this.contract = contract;
        this.publisher = publisher;
    }

    DiscoveryJobResponse create(
            DiscoveryJobCreateRequest request, UUID actorId, UUID correlationId) {
        UUID executorId = uuidV4(request.executorId());
        UUID scopeId = uuidV4(request.scopeId());
        expireEligible(executorId);
        JobRow pending;
        try {
            pending = transactions.execute(
                    status -> createPending(executorId, scopeId, actorId, correlationId));
        } catch (DataIntegrityViolationException exception) {
            throw new DiscoveryJobConflictException();
        }
        if (pending == null) throw new IllegalStateException("Job transaction returned no result");
        try {
            if (!dispatch(pending.jobId())) throw new DiscoveryJobConflictException();
        } catch (DiscoveryJobPublicationUnavailableException
                | DiscoverySftpProvisioningUnavailableException
                | DiscoverySftpHostKeyMismatchException exception) {
            throw new DiscoveryJobPendingException(pending.jobId(), exception);
        }
        return find(pending.jobId()).orElseThrow(IllegalStateException::new);
    }

    Optional<DiscoveryJobResponse> find(UUID jobId) {
        return findRow(jobId, false).map(DiscoveryJobService::response);
    }

    void reconcilePending() {
        expireEligible();
        List<UUID> pending = jdbc.queryForList(
                "SELECT job_id FROM discovery.discovery_jobs "
                        + "WHERE status = 'PENDING' ORDER BY created_at LIMIT 100",
                UUID.class);
        for (UUID jobId : pending) {
            try {
                dispatch(jobId);
            } catch (DiscoveryJobPublicationUnavailableException
                    | DiscoveryJobPublicationConflictException
                    | DiscoverySftpProvisioningUnavailableException
                    | DiscoverySftpProvisioningConflictException
                    | DiscoveryJobScopeNotActiveException exception) {
                // Keep PENDING for the next bounded reconciliation.
            }
        }
    }

    private JobRow createPending(
            UUID executorId, UUID scopeId, UUID actorId, UUID correlationId) {
        ExecutorState executor = executor(executorId);
        if (!"ACTIVE".equals(executor.status())
                || !("discovery-executor-" + executorId).equals(executor.sftpUsername())) {
            throw new DiscoveryJobExecutorNotActiveException();
        }
        String scopeCidr = jdbc.query(
                        """
                        SELECT scope.cidr::text
                        FROM discovery.discovery_executor_scopes association
                        JOIN discovery.authorized_scopes scope ON scope.id = association.scope_id
                        WHERE association.executor_id = ? AND association.scope_id = ?
                        FOR SHARE OF scope
                        """,
                        (resultSet, row) -> new ScopeState(
                                resultSet.getString(1), "ACTIVE"),
                        executorId,
                        scopeId)
                .stream()
                .findFirst()
                .map(ScopeState::cidr)
                .orElseThrow(DiscoveryJobScopeNotFoundException::new);
        String status = jdbc.queryForObject(
                "SELECT status FROM discovery.authorized_scopes WHERE id = ?", String.class, scopeId);
        if (!"ACTIVE".equals(status)) throw new DiscoveryJobScopeNotActiveException();

        Instant now = now();
        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM discovery.discovery_jobs "
                        + "WHERE executor_id = ? AND status IN ('PENDING','DISPATCHED','RUNNING')",
                Integer.class,
                executorId);
        if (active != null && active > 0) throw new DiscoveryJobConflictException();
        UUID jobId = UUID.randomUUID();
        Instant expiresAt = now.plus(JOB_TTL);
        jdbc.update(
                """
                INSERT INTO discovery.discovery_jobs
                    (job_id, executor_id, scope_id, profile, status, actor_id, correlation_id,
                     created_at, expires_at, updated_at, version)
                VALUES (?, ?, ?, 'BASIC', 'PENDING', ?, ?, ?, ?, ?, 0)
                """,
                jobId,
                executorId,
                scopeId,
                actorId,
                correlationId,
                Timestamp.from(now),
                Timestamp.from(expiresAt),
                Timestamp.from(now));
        outbox(jobId, 0, actorId, correlationId, now,
                "DISCOVERY_JOB_CREATED", "{\"status\":\"PENDING\",\"profile\":\"BASIC\"}");
        JobRow row = findRow(jobId, false).orElseThrow();
        return row.withScopeCidr(scopeCidr);
    }

    private boolean dispatch(UUID jobId) {
        Boolean dispatched = transactions.execute(status -> {
            JobRow job = findRow(jobId, true).orElseThrow();
            if (job.status() != DiscoveryJobStatus.PENDING) return false;
            Instant now = now();
            if (!now.isBefore(job.expiresAt())) {
                publisher.removeExpired(job.executorId(), job.jobId());
                markExpired(job, now);
                return false;
            }
            ExecutorState executor = executor(job.executorId());
            if (!"ACTIVE".equals(executor.status())
                    || !("discovery-executor-" + job.executorId()).equals(executor.sftpUsername())) {
                throw new DiscoveryJobExecutorNotActiveException();
            }
            String scopeCidr;
            try {
                scopeCidr = activeAssociatedScope(job.executorId(), job.scopeId());
            } catch (DiscoveryJobScopeNotActiveException exception) {
                markExpired(job, now);
                return false;
            }
            DiscoveryExecutorIdentityRegistry.RegisteredPublicKey key =
                    identities.requireRegisteredPublicKey(job.executorId());
            provisioner.ensureProvisioned(job.executorId(), key.publicKey());
            byte[] document = contract.serialize(new DiscoveryJobMessage(
                    "1.0", "DISCOVERY_JOB", job.jobId(), job.executorId(), job.scopeId(),
                    scopeCidr, "BASIC", job.createdAt(), job.expiresAt()));
            Instant dispatchedAt;
            try {
                dispatchedAt = publisher.publish(
                        job.executorId(), job.jobId(), document, job.expiresAt(), clock);
            } catch (DiscoveryJobPublicationExpiredException exception) {
                markExpired(job, now());
                return false;
            }
            int updated = jdbc.update(
                    """
                    UPDATE discovery.discovery_jobs
                    SET status = 'DISPATCHED', dispatched_at = ?, updated_at = ?, version = 1
                    WHERE job_id = ? AND status = 'PENDING' AND version = 0
                    """,
                    Timestamp.from(dispatchedAt), Timestamp.from(dispatchedAt), job.jobId());
            if (updated != 1) throw new IllegalStateException("Job changed concurrently");
            outbox(job.jobId(), 1, job.actorId(), job.correlationId(), dispatchedAt,
                    "DISCOVERY_JOB_DISPATCHED", "{\"status\":\"DISPATCHED\"}");
            return true;
        });
        return Boolean.TRUE.equals(dispatched);
    }

    private ExecutorState executor(UUID executorId) {
        return jdbc.query(
                        "SELECT status, sftp_username FROM discovery.discovery_executors "
                                + "WHERE id = ? FOR UPDATE",
                        (resultSet, row) -> new ExecutorState(
                                resultSet.getString(1), resultSet.getString(2)),
                        executorId)
                .stream()
                .findFirst()
                .orElseThrow(DiscoveryJobNotFoundException::new);
    }

    private String activeAssociatedScope(UUID executorId, UUID scopeId) {
        return jdbc.query(
                        """
                        SELECT scope.cidr::text, scope.status
                        FROM discovery.discovery_executor_scopes association
                        JOIN discovery.authorized_scopes scope ON scope.id = association.scope_id
                        WHERE association.executor_id = ? AND association.scope_id = ?
                        FOR SHARE OF scope
                        """,
                        (resultSet, row) -> new ScopeState(
                                resultSet.getString(1), resultSet.getString(2)),
                        executorId,
                        scopeId)
                .stream()
                .filter(scope -> "ACTIVE".equals(scope.status()))
                .findFirst()
                .map(ScopeState::cidr)
                .orElseThrow(DiscoveryJobScopeNotActiveException::new);
    }

    private void expireEligible() {
        expireEligible(null);
    }

    private void expireEligible(UUID executorId) {
        Instant now = now();
        String sql = "SELECT job_id FROM discovery.discovery_jobs "
                + "WHERE status IN ('PENDING','DISPATCHED') AND expires_at <= ?"
                + (executorId == null ? "" : " AND executor_id = ?")
                + " ORDER BY created_at LIMIT 100";
        List<UUID> expired = executorId == null
                ? jdbc.queryForList(sql, UUID.class, Timestamp.from(now))
                : jdbc.queryForList(sql, UUID.class, Timestamp.from(now), executorId);
        for (UUID jobId : expired) {
            try {
                expire(jobId);
            } catch (DiscoveryJobPublicationUnavailableException
                    | DiscoveryJobPublicationConflictException exception) {
                // Keep it non-terminal until its deterministic files are safely removed.
            }
        }
    }

    private void expire(UUID jobId) {
        transactions.executeWithoutResult(status -> {
            JobRow job = findRow(jobId, true).orElseThrow();
            Instant now = now();
            if ((job.status() != DiscoveryJobStatus.PENDING
                            && job.status() != DiscoveryJobStatus.DISPATCHED)
                    || now.isBefore(job.expiresAt())) {
                return;
            }
            publisher.removeExpired(job.executorId(), job.jobId());
            markExpired(job, now);
        });
    }

    private void markExpired(JobRow job, Instant now) {
        jdbc.update(
                """
                UPDATE discovery.discovery_jobs
                SET status = 'EXPIRED', updated_at = ?, version = version + 1
                WHERE job_id = ? AND status IN ('PENDING', 'DISPATCHED') AND version = ?
                """,
                Timestamp.from(now), job.jobId(), job.version());
    }

    private void outbox(UUID jobId, long version, UUID actorId, UUID correlationId,
            Instant now, String eventType, String payload) {
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '1.0', ?, 'DISCOVERY', 'HUMAN_USER', ?, 'DISCOVERY_JOB', ?, ?,
                        'SUCCESS', ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(), eventType, Timestamp.from(now), actorId, jobId, version,
                correlationId, payload, Timestamp.from(now));
    }

    private Optional<JobRow> findRow(UUID jobId, boolean lock) {
        return jdbc.query(
                        "SELECT * FROM discovery.discovery_jobs WHERE job_id = ?"
                                + (lock ? " FOR UPDATE" : ""),
                        DiscoveryJobService::row,
                        jobId)
                .stream().findFirst();
    }

    private static JobRow row(ResultSet resultSet, int ignored) throws SQLException {
        Timestamp dispatched = resultSet.getTimestamp("dispatched_at");
        return new JobRow(
                resultSet.getObject("job_id", UUID.class),
                resultSet.getObject("executor_id", UUID.class),
                resultSet.getObject("scope_id", UUID.class),
                null,
                resultSet.getString("profile"),
                DiscoveryJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("actor_id", UUID.class),
                resultSet.getObject("correlation_id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                dispatched == null ? null : dispatched.toInstant(),
                resultSet.getLong("version"));
    }

    private static DiscoveryJobResponse response(JobRow row) {
        return new DiscoveryJobResponse(row.jobId(), row.executorId(), row.scopeId(), row.profile(),
                row.status(), row.createdAt(), row.expiresAt(), row.dispatchedAt(), row.version());
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static UUID uuidV4(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (parsed.version() != 4 || parsed.variant() != 2 || !parsed.toString().equals(value)) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new InvalidDiscoveryJobRequestException();
        }
    }

    private record ExecutorState(String status, String sftpUsername) {}
    private record ScopeState(String cidr, String status) {}
    private record JobRow(UUID jobId, UUID executorId, UUID scopeId, String scopeCidr,
            String profile, DiscoveryJobStatus status, UUID actorId, UUID correlationId,
            Instant createdAt, Instant expiresAt, Instant dispatchedAt, long version) {
        JobRow withScopeCidr(String value) {
            return new JobRow(jobId, executorId, scopeId, value, profile, status, actorId,
                    correlationId, createdAt, expiresAt, dispatchedAt, version);
        }
    }
}

final class DiscoveryJobNotFoundException extends RuntimeException {}
final class DiscoveryJobExecutorNotActiveException extends RuntimeException {}
final class DiscoveryJobScopeNotFoundException extends RuntimeException {}
final class DiscoveryJobScopeNotActiveException extends RuntimeException {}
final class DiscoveryJobConflictException extends RuntimeException {}
final class InvalidDiscoveryJobRequestException extends RuntimeException {}
final class DiscoveryJobPendingException extends RuntimeException {
    private final UUID jobId;

    DiscoveryJobPendingException(UUID jobId, RuntimeException cause) {
        super(cause);
        this.jobId = jobId;
    }

    UUID jobId() {
        return jobId;
    }
}
