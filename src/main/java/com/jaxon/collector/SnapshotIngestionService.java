package com.jaxon.collector;

import com.jaxon.identity.AgentIdentityRegistry;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class SnapshotIngestionService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final AgentIdentityRegistry identities;
    private final Clock clock;

    SnapshotIngestionService(JdbcTemplate jdbc, AgentIdentityRegistry identities, Clock clock) {
        this.jdbc = jdbc;
        this.identities = identities;
        this.clock = clock;
    }

    @Transactional
    IngestionOutcome ingest(
            UUID pathAgentId,
            UUID pathMessageId,
            String sourcePath,
            String sha256,
            long size,
            SnapshotMessage message) {
        Optional<String> previous = previousStatus(sourcePath, sha256);
        if (previous.isPresent()) {
            return IngestionOutcome.ALREADY_PROCESSED;
        }
        if (!pathAgentId.equals(message.agentId())) {
            reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "PATH_AGENT_ID_MISMATCH");
            return IngestionOutcome.REJECTED;
        }
        if (!pathMessageId.equals(message.messageId())) {
            reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "PATH_MESSAGE_ID_MISMATCH");
            return IngestionOutcome.REJECTED;
        }
        Integer active = jdbc.queryForObject(
                """
                SELECT count(*) FROM collector.agents
                WHERE id = ? AND status = 'ACTIVE' AND sftp_username = ?
                """,
                Integer.class,
                message.agentId(),
                "agent-" + message.agentId());
        if (active == null || active != 1) {
            reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "AGENT_NOT_ACTIVE");
            return IngestionOutcome.REJECTED;
        }
        boolean identityRegistered = identities.find(message.agentId())
                .map(AgentIdentityRegistry.KeyState::publicKeyRegistered)
                .orElse(false);
        if (!identityRegistered) {
            reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "AGENT_IDENTITY_INVALID");
            return IngestionOutcome.REJECTED;
        }

        List<String> lockKeys = java.util.stream.Stream.of(
                        "message:" + message.messageId(), "snapshot:" + message.snapshotId())
                .sorted()
                .toList();
        for (String lockKey : lockKeys) {
            jdbc.query(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                    resultSet -> null,
                    lockKey);
        }

        ExistingSnapshot byMessage = findByMessageId(message.messageId()).orElse(null);
        ExistingSnapshot bySnapshot = findBySnapshotId(message.snapshotId()).orElse(null);
        if (byMessage != null || bySnapshot != null) {
            ExistingSnapshot existing = byMessage != null ? byMessage : bySnapshot;
            boolean duplicate = existing.messageId().equals(message.messageId())
                    && existing.snapshotId().equals(message.snapshotId())
                    && existing.sha256().equals(sha256);
            if (duplicate) {
                return IngestionOutcome.DUPLICATE;
            }
            conflict(pathAgentId, pathMessageId, sourcePath, sha256, size,
                    byMessage != null ? "MESSAGE_ID_CONFLICT" : "SNAPSHOT_ID_CONFLICT");
            return IngestionOutcome.CONFLICT;
        }

        UUID ingestionId = UUID.randomUUID();
        Instant receivedAt = now();
        int ledgerInserted = insertLedger(
                ingestionId, pathAgentId, pathMessageId, sourcePath, sha256, size,
                receivedAt, "ACCEPTED", null, message.snapshotId());
        if (ledgerInserted == 0) {
            return IngestionOutcome.DUPLICATE;
        }
        int accepted = jdbc.update(
                """
                INSERT INTO collector.accepted_snapshots
                    (snapshot_id, message_id, ingestion_id, agent_id, contract_version,
                     created_at, captured_at, received_at, content_sha256, hostname,
                     os_family, os_name, os_version, architecture, logical_cpu_count,
                     memory_bytes, storage_volumes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT DO NOTHING
                """,
                message.snapshotId(),
                message.messageId(),
                ingestionId,
                message.agentId(),
                message.contractVersion(),
                Timestamp.from(message.createdAt()),
                Timestamp.from(message.capturedAt()),
                Timestamp.from(receivedAt),
                sha256,
                message.hostname(),
                message.operatingSystem().family(),
                message.operatingSystem().name(),
                message.operatingSystem().version(),
                message.architecture(),
                message.logicalCpuCount(),
                message.memoryBytes(),
                storageJson(message.storageVolumes()));
        if (accepted == 0) {
            throw new SnapshotRaceException();
        }
        return IngestionOutcome.ACCEPTED;
    }

    @Transactional
    IngestionOutcome reject(
            UUID pathAgentId,
            UUID pathMessageId,
            String sourcePath,
            String sha256,
            long size,
            String reason) {
        if (previousStatus(sourcePath, sha256).isPresent()) {
            return IngestionOutcome.ALREADY_PROCESSED;
        }
        int inserted = insertLedger(
                UUID.randomUUID(), pathAgentId, pathMessageId, sourcePath, sha256, size,
                now(), "REJECTED", reason, null);
        return inserted == 1 ? IngestionOutcome.REJECTED : IngestionOutcome.ALREADY_PROCESSED;
    }

    private void conflict(
            UUID pathAgentId,
            UUID pathMessageId,
            String sourcePath,
            String sha256,
            long size,
            String reason) {
        insertLedger(
                UUID.randomUUID(), pathAgentId, pathMessageId, sourcePath, sha256, size,
                now(), "CONFLICT", reason, null);
    }

    private int insertLedger(
            UUID ingestionId,
            UUID agentId,
            UUID messageId,
            String sourcePath,
            String sha256,
            long size,
            Instant receivedAt,
            String status,
            String reason,
            UUID snapshotId) {
        return jdbc.update(
                """
                INSERT INTO collector.snapshot_ingestions
                    (ingestion_id, claimed_agent_id, claimed_message_id, source_path,
                      file_fingerprint, file_size, received_at, status, reason_code, snapshot_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_path, file_fingerprint) DO NOTHING
                """,
                ingestionId,
                agentId,
                messageId,
                sourcePath,
                sha256,
                size,
                Timestamp.from(receivedAt),
                status,
                reason,
                snapshotId);
    }

    private Optional<String> previousStatus(String sourcePath, String sha256) {
        return jdbc.query(
                        "SELECT status FROM collector.snapshot_ingestions WHERE source_path = ? AND file_fingerprint = ?",
                        (resultSet, row) -> resultSet.getString("status"),
                        sourcePath,
                        sha256)
                .stream()
                .findFirst();
    }

    private Optional<ExistingSnapshot> findByMessageId(UUID messageId) {
        return findExisting("message_id", messageId);
    }

    private Optional<ExistingSnapshot> findBySnapshotId(UUID snapshotId) {
        return findExisting("snapshot_id", snapshotId);
    }

    private Optional<ExistingSnapshot> findExisting(String column, UUID id) {
        return jdbc.query(
                        "SELECT message_id, snapshot_id, content_sha256 FROM collector.accepted_snapshots WHERE "
                                + column + " = ?",
                        (resultSet, row) -> new ExistingSnapshot(
                                resultSet.getObject("message_id", UUID.class),
                                resultSet.getObject("snapshot_id", UUID.class),
                                resultSet.getString("content_sha256")),
                        id)
                .stream()
                .findFirst();
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static String storageJson(List<SnapshotMessage.StorageVolume> volumes) {
        try {
            return JSON.writeValueAsString(volumes);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize validated storage volumes", exception);
        }
    }

    enum IngestionOutcome {
        ACCEPTED,
        REJECTED,
        CONFLICT,
        DUPLICATE,
        ALREADY_PROCESSED
    }

    private record ExistingSnapshot(UUID messageId, UUID snapshotId, String sha256) {}

    static final class SnapshotRaceException extends RuntimeException {}
}
