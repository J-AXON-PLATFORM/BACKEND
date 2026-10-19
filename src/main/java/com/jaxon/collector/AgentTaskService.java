package com.jaxon.collector;

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
class AgentTaskService {

    static final Duration TASK_TTL = Duration.ofMinutes(15);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AgentTaskContract contract;
    private final AgentTaskFilePublisher publisher;
    private final AgentTaskMetrics metrics;

    AgentTaskService(
            JdbcTemplate jdbc,
            Clock clock,
            TransactionTemplate transactions,
            AgentTaskContract contract,
            AgentTaskFilePublisher publisher,
            AgentTaskMetrics metrics) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactions = transactions;
        this.contract = contract;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    AgentTaskResponse create(UUID agentId, AgentTaskCreateRequest request, UUID actorId, UUID correlationId) {
        if (request.taskType() != AgentTaskCreateRequest.TaskType.COLLECT_SNAPSHOT) {
            throw new AgentTaskTypeNotAllowedException();
        }
        TaskRow pending;
        try {
            pending = transactions.execute(status -> createPending(agentId, actorId, correlationId));
        } catch (DataIntegrityViolationException exception) {
            throw new AgentTaskConflictException();
        }
        if (pending == null) {
            throw new IllegalStateException("Task transaction returned no result");
        }
        metrics.created();
        dispatch(pending.taskId());
        return find(agentId, pending.taskId()).orElseThrow(IllegalStateException::new);
    }

    Optional<AgentTaskResponse> find(UUID agentId, UUID taskId) {
        return findRow(agentId, taskId, false).map(AgentTaskService::response);
    }

    void reconcilePending() {
        expireEligible();
        List<UUID> pending = jdbc.queryForList(
                "SELECT task_id FROM collector.agent_tasks WHERE status = 'PENDING' ORDER BY created_at LIMIT 100",
                UUID.class);
        for (UUID taskId : pending) {
            try {
                dispatch(taskId);
            } catch (TaskPublicationUnavailableException | TaskPublicationConflictException exception) {
                metrics.reconciliation("dispatch_failed");
            }
        }
    }

    private TaskRow createPending(UUID agentId, UUID actorId, UUID correlationId) {
        AgentState agent = jdbc.query(
                        "SELECT status, sftp_username FROM collector.agents WHERE id = ? FOR UPDATE",
                        (resultSet, row) -> new AgentState(
                                resultSet.getString("status"), resultSet.getString("sftp_username")),
                        agentId)
                .stream()
                .findFirst()
                .orElseThrow(AgentNotFoundException::new);
        if (!"ACTIVE".equals(agent.status()) || !("agent-" + agentId).equals(agent.sftpUsername())) {
            throw new AgentNotActiveException();
        }
        Instant now = now();
        expireEligible(agentId, now);
        Integer active = jdbc.queryForObject(
                """
                SELECT count(*) FROM collector.agent_tasks
                WHERE agent_id = ? AND status IN ('PENDING', 'DISPATCHED', 'RUNNING')
                """,
                Integer.class,
                agentId);
        if (active != null && active > 0) {
            throw new AgentTaskConflictException();
        }
        UUID taskId = UUID.randomUUID();
        UUID messageId = differentUuid(taskId);
        Instant expiresAt = now.plus(TASK_TTL);
        jdbc.update(
                """
                INSERT INTO collector.agent_tasks
                    (task_id, dispatch_message_id, agent_id, task_type, status, payload,
                     actor_id, correlation_id, created_at, expires_at, updated_at)
                VALUES (?, ?, ?, 'COLLECT_SNAPSHOT', 'PENDING', '{}'::jsonb, ?, ?, ?, ?, ?)
                """,
                taskId,
                messageId,
                agentId,
                actorId,
                correlationId,
                Timestamp.from(now),
                Timestamp.from(expiresAt),
                Timestamp.from(now));
        return findRow(agentId, taskId, false).orElseThrow(IllegalStateException::new);
    }

    private void dispatch(UUID taskId) {
        try {
            transactions.executeWithoutResult(status -> {
                TaskRow task = findByTaskId(taskId, true).orElseThrow(IllegalStateException::new);
                if (task.status() != AgentTaskStatus.PENDING) {
                    return;
                }
                Instant now = now();
                if (!now.isBefore(task.expiresAt())) {
                    markExpired(task, now);
                    return;
                }
                byte[] document = contract.serialize(new AgentTaskMessage(
                        "1.0",
                        "AGENT_TASK",
                        task.messageId(),
                        task.agentId(),
                        task.createdAt(),
                        task.taskId(),
                        task.taskType(),
                        task.expiresAt()));
                publisher.publish(task.agentId(), task.taskId(), document);
                int updated = jdbc.update(
                        """
                        UPDATE collector.agent_tasks
                        SET status = 'DISPATCHED', dispatched_at = ?, updated_at = ?, version = version + 1
                        WHERE task_id = ? AND status = 'PENDING' AND version = ?
                        """,
                        Timestamp.from(now),
                        Timestamp.from(now),
                        task.taskId(),
                        task.version());
                if (updated != 1) {
                    throw new IllegalStateException("Task dispatch state changed concurrently");
                }
            });
            metrics.dispatch("succeeded");
        } catch (TaskPublicationConflictException exception) {
            metrics.dispatch("conflict");
            throw exception;
        } catch (TaskPublicationUnavailableException exception) {
            metrics.dispatch("failed");
            throw exception;
        }
    }

    private void expireEligible() {
        Instant now = now();
        List<TaskRow> tasks = jdbc.query(
                """
                SELECT * FROM collector.agent_tasks
                WHERE status IN ('PENDING', 'DISPATCHED') AND expires_at <= ?
                ORDER BY expires_at LIMIT 100
                """,
                (resultSet, row) -> row(resultSet),
                Timestamp.from(now));
        for (TaskRow task : tasks) {
            transactions.executeWithoutResult(status -> findByTaskId(task.taskId(), true)
                    .filter(current -> !current.status().terminal() && !now.isBefore(current.expiresAt()))
                    .ifPresent(current -> markExpired(current, now)));
        }
    }

    private void expireEligible(UUID agentId, Instant now) {
        int expired = jdbc.update(
                """
                UPDATE collector.agent_tasks
                SET status = 'EXPIRED', completed_at = ?, updated_at = ?, version = version + 1
                WHERE agent_id = ? AND status IN ('PENDING', 'DISPATCHED') AND expires_at <= ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                agentId,
                Timestamp.from(now));
        for (int index = 0; index < expired; index++) {
            metrics.expiration();
        }
    }

    private void markExpired(TaskRow task, Instant now) {
        int updated = jdbc.update(
                """
                UPDATE collector.agent_tasks
                SET status = 'EXPIRED', completed_at = ?, updated_at = ?, version = version + 1
                WHERE task_id = ? AND status IN ('PENDING', 'DISPATCHED') AND version = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                task.taskId(),
                task.version());
        if (updated == 1) {
            metrics.expiration();
        }
    }

    private Optional<TaskRow> findRow(UUID agentId, UUID taskId, boolean lock) {
        return query(
                "SELECT * FROM collector.agent_tasks WHERE agent_id = ? AND task_id = ?"
                        + (lock ? " FOR UPDATE" : ""),
                agentId,
                taskId);
    }

    private Optional<TaskRow> findByTaskId(UUID taskId, boolean lock) {
        return query(
                "SELECT * FROM collector.agent_tasks WHERE task_id = ?" + (lock ? " FOR UPDATE" : ""),
                taskId);
    }

    private Optional<TaskRow> query(String sql, Object... arguments) {
        return jdbc.query(sql, (resultSet, rowNumber) -> row(resultSet), arguments).stream().findFirst();
    }

    private static TaskRow row(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new TaskRow(
                resultSet.getObject("task_id", UUID.class),
                resultSet.getObject("dispatch_message_id", UUID.class),
                resultSet.getObject("agent_id", UUID.class),
                resultSet.getString("task_type"),
                AgentTaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                instant(resultSet, "dispatched_at"),
                instant(resultSet, "completed_at"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                resultSet.getLong("version"));
    }

    private static Instant instant(java.sql.ResultSet resultSet, String field) throws java.sql.SQLException {
        Timestamp value = resultSet.getTimestamp(field);
        return value == null ? null : value.toInstant();
    }

    private static AgentTaskResponse response(TaskRow task) {
        AgentTaskResponse.TaskError error = task.errorCode() == null
                ? null
                : new AgentTaskResponse.TaskError(task.errorCode(), task.errorMessage());
        return new AgentTaskResponse(
                task.taskId(),
                task.agentId(),
                task.taskType(),
                task.status(),
                task.createdAt(),
                task.expiresAt(),
                task.dispatchedAt(),
                task.completedAt(),
                error);
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static UUID differentUuid(UUID other) {
        UUID value;
        do {
            value = UUID.randomUUID();
        } while (value.equals(other));
        return value;
    }

    record TaskRow(
            UUID taskId,
            UUID messageId,
            UUID agentId,
            String taskType,
            AgentTaskStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant dispatchedAt,
            Instant completedAt,
            String errorCode,
            String errorMessage,
            long version) {}

    private record AgentState(String status, String sftpUsername) {}
}

final class AgentNotActiveException extends RuntimeException {}

final class AgentTaskConflictException extends RuntimeException {}

final class AgentTaskTypeNotAllowedException extends RuntimeException {}
