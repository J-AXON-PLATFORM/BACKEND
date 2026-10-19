package com.jaxon.collector;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AgentResultIngestionService {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AgentTaskMetrics metrics;

    AgentResultIngestionService(JdbcTemplate jdbc, Clock clock, AgentTaskMetrics metrics) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    IngestionOutcome ingest(
            UUID pathAgentId,
            UUID pathMessageId,
            String sourcePath,
            String sha256,
            long size,
            AgentResultMessage message) {
        if (previousStatus(sourcePath, sha256).isPresent()) {
            return IngestionOutcome.ALREADY_PROCESSED;
        }
        if (!pathAgentId.equals(message.agentId())) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "PATH_AGENT_ID_MISMATCH");
        }
        if (!pathMessageId.equals(message.messageId())) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "PATH_MESSAGE_ID_MISMATCH");
        }
        if (message.messageId().equals(message.resultId())
                || message.messageId().equals(message.taskId())
                || message.resultId().equals(message.taskId())) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size,
                    "RESULT_IDENTIFIERS_NOT_INDEPENDENT", message.resultId(), message.taskId());
        }
        for (String key : java.util.stream.Stream.of(
                        "message:" + message.messageId(),
                        "result:" + message.resultId(),
                        "task:" + message.taskId())
                .sorted()
                .toList()) {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", resultSet -> null, key);
        }
        ExistingResult existing = findExisting(message).orElse(null);
        if (existing != null) {
            boolean duplicate = existing.messageId().equals(message.messageId())
                    && existing.resultId().equals(message.resultId())
                    && existing.taskId().equals(message.taskId())
                    && existing.sha256().equals(sha256);
            if (duplicate) {
                metrics.reconciliation("duplicate");
                return IngestionOutcome.DUPLICATE;
            }
            record(pathAgentId, pathMessageId, sourcePath, sha256, size, "CONFLICT",
                    "RESULT_IDENTIFIER_CONFLICT", message.resultId(), message.taskId());
            metrics.reconciliation("conflict");
            return IngestionOutcome.CONFLICT;
        }
        AgentTaskService.TaskRow task = findTask(message.taskId()).orElse(null);
        if (task == null) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "TASK_NOT_FOUND",
                    message.resultId(), message.taskId());
        }
        if (!task.agentId().equals(message.agentId())) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "RESULT_AGENT_MISMATCH",
                    message.resultId(), message.taskId());
        }
        if (!task.taskType().equals(message.taskType()) || !"COLLECT_SNAPSHOT".equals(message.taskType())) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "RESULT_TASK_TYPE_MISMATCH",
                    message.resultId(), message.taskId());
        }
        if (task.status().terminal()) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "TASK_TERMINAL_IMMUTABLE",
                    message.resultId(), message.taskId());
        }
        if (task.status() != AgentTaskStatus.DISPATCHED) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "TASK_NOT_DISPATCHED",
                    message.resultId(), message.taskId());
        }
        if (message.messageId().equals(task.messageId())
                || message.resultId().equals(task.messageId())
                || message.completedAt().isBefore(task.createdAt())) {
            return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, "RESULT_SEMANTICS_INVALID",
                    message.resultId(), message.taskId());
        }
        Instant receivedAt = now();
        UUID ingestionId = UUID.randomUUID();
        int ledger = record(pathAgentId, pathMessageId, sourcePath, sha256, size, "ACCEPTED", null,
                message.resultId(), message.taskId(), ingestionId, receivedAt);
        if (ledger != 1) {
            return IngestionOutcome.ALREADY_PROCESSED;
        }
        String safeMessage = safeMessage(message.status());
        String errorCode = message.error() == null ? null : message.error().code();
        jdbc.update(
                """
                INSERT INTO collector.accepted_task_results
                    (result_id, message_id, task_id, ingestion_id, agent_id, task_type,
                     result_status, contract_version, created_at, completed_at, received_at,
                     content_sha256, error_code, error_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                message.resultId(),
                message.messageId(),
                message.taskId(),
                ingestionId,
                message.agentId(),
                message.taskType(),
                message.status(),
                message.contractVersion(),
                Timestamp.from(message.createdAt()),
                Timestamp.from(message.completedAt()),
                Timestamp.from(receivedAt),
                sha256,
                errorCode,
                errorCode == null ? null : safeMessage);
        int updated = jdbc.update(
                """
                UPDATE collector.agent_tasks
                SET status = ?, completed_at = ?, error_code = ?, error_message = ?,
                    updated_at = ?, version = version + 1
                WHERE task_id = ? AND status = 'DISPATCHED' AND version = ?
                """,
                message.status(),
                Timestamp.from(message.completedAt()),
                errorCode,
                errorCode == null ? null : safeMessage,
                Timestamp.from(receivedAt),
                message.taskId(),
                task.version());
        if (updated != 1) {
            throw new IllegalStateException("Task result transition changed concurrently");
        }
        metrics.result(message.status());
        metrics.executionDuration(Duration.between(task.dispatchedAt(), receivedAt), message.status());
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
        return reject(pathAgentId, pathMessageId, sourcePath, sha256, size, reason, null, null);
    }

    private IngestionOutcome reject(
            UUID pathAgentId,
            UUID pathMessageId,
            String sourcePath,
            String sha256,
            long size,
            String reason,
            UUID resultId,
            UUID taskId) {
        if (previousStatus(sourcePath, sha256).isPresent()) {
            return IngestionOutcome.ALREADY_PROCESSED;
        }
        int inserted = record(pathAgentId, pathMessageId, sourcePath, sha256, size, "REJECTED", reason,
                resultId, taskId);
        metrics.reconciliation("rejected");
        return inserted == 1 ? IngestionOutcome.REJECTED : IngestionOutcome.ALREADY_PROCESSED;
    }

    private int record(
            UUID pathAgentId,
            UUID pathMessageId,
            String sourcePath,
            String sha256,
            long size,
            String status,
            String reason,
            UUID resultId,
            UUID taskId) {
        return record(pathAgentId, pathMessageId, sourcePath, sha256, size, status, reason, resultId, taskId,
                UUID.randomUUID(), now());
    }

    private int record(
            UUID pathAgentId,
            UUID pathMessageId,
            String sourcePath,
            String sha256,
            long size,
            String status,
            String reason,
            UUID resultId,
            UUID taskId,
            UUID ingestionId,
            Instant receivedAt) {
        return jdbc.update(
                """
                INSERT INTO collector.task_result_ingestions
                    (ingestion_id, path_agent_id, path_message_id, source_path, file_fingerprint,
                     file_size, received_at, status, reason_code, claimed_result_id, claimed_task_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_path, file_fingerprint) DO NOTHING
                """,
                ingestionId,
                pathAgentId,
                pathMessageId,
                sourcePath,
                sha256,
                size,
                Timestamp.from(receivedAt),
                status,
                reason,
                resultId,
                taskId);
    }

    private Optional<String> previousStatus(String sourcePath, String sha256) {
        return jdbc.query(
                        "SELECT status FROM collector.task_result_ingestions WHERE source_path = ? AND file_fingerprint = ?",
                        (resultSet, row) -> resultSet.getString("status"),
                        sourcePath,
                        sha256)
                .stream()
                .findFirst();
    }

    private Optional<ExistingResult> findExisting(AgentResultMessage message) {
        List<ExistingResult> values = jdbc.query(
                """
                SELECT message_id, result_id, task_id, content_sha256
                FROM collector.accepted_task_results
                WHERE message_id = ? OR result_id = ? OR task_id = ?
                """,
                (resultSet, row) -> new ExistingResult(
                        resultSet.getObject("message_id", UUID.class),
                        resultSet.getObject("result_id", UUID.class),
                        resultSet.getObject("task_id", UUID.class),
                        resultSet.getString("content_sha256")),
                message.messageId(),
                message.resultId(),
                message.taskId());
        return values.stream().findFirst();
    }

    private Optional<AgentTaskService.TaskRow> findTask(UUID taskId) {
        return jdbc.query(
                        "SELECT * FROM collector.agent_tasks WHERE task_id = ? FOR UPDATE",
                        (resultSet, row) -> new AgentTaskService.TaskRow(
                                resultSet.getObject("task_id", UUID.class),
                                resultSet.getObject("dispatch_message_id", UUID.class),
                                resultSet.getObject("agent_id", UUID.class),
                                resultSet.getString("task_type"),
                                AgentTaskStatus.valueOf(resultSet.getString("status")),
                                resultSet.getTimestamp("created_at").toInstant(),
                                resultSet.getTimestamp("expires_at").toInstant(),
                                optionalInstant(resultSet, "dispatched_at"),
                                optionalInstant(resultSet, "completed_at"),
                                resultSet.getString("error_code"),
                                resultSet.getString("error_message"),
                                resultSet.getLong("version")),
                        taskId)
                .stream()
                .findFirst();
    }

    private static Instant optionalInstant(java.sql.ResultSet resultSet, String field)
            throws java.sql.SQLException {
        Timestamp value = resultSet.getTimestamp(field);
        return value == null ? null : value.toInstant();
    }

    private static String safeMessage(String status) {
        return "REJECTED".equals(status)
                ? "El Agent rechazó la tarea sin ejecutarla."
                : "La ejecución controlada de la tarea no pudo completarse.";
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    enum IngestionOutcome {
        ACCEPTED,
        REJECTED,
        CONFLICT,
        DUPLICATE,
        ALREADY_PROCESSED
    }

    private record ExistingResult(UUID messageId, UUID resultId, UUID taskId, String sha256) {}
}
