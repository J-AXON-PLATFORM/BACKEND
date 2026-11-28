package com.jaxon.automation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(
        name = "jaxon.automation.execution.enabled",
        havingValue = "true",
        matchIfMissing = true)
class AutomationExecutionWorker {

    static final int MAX_ATTEMPTS = 10;
    static final Duration BASE_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_DELAY = Duration.ofMinutes(5);
    static final String FAILURE_CODE = "NOTIFICATION_PERSISTENCE_FAILED";

    private static final Logger LOG = LoggerFactory.getLogger(AutomationExecutionWorker.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MeterRegistry meters;
    private final Clock clock;
    private final int batchSize;
    private final LongUnaryOperator jitter;

    @Autowired
    AutomationExecutionWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            MeterRegistry meters,
            Clock clock,
            @Value("${jaxon.automation.execution.batch-size:50}") int batchSize) {
        this(jdbc, transactionManager, meters, clock, batchSize,
                bound -> ThreadLocalRandom.current().nextLong(bound + 1));
    }

    AutomationExecutionWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            MeterRegistry meters,
            Clock clock,
            int batchSize,
            LongUnaryOperator jitter) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("Automation execution batch size must be between 1 and 200");
        }
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.meters = meters;
        this.clock = clock;
        this.batchSize = batchSize;
        this.jitter = jitter;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.automation.execution.poll-interval:1s}",
            initialDelayString = "${jaxon.automation.execution.initial-delay:1s}")
    void poll() {
        processDue(batchSize);
    }

    ExecutionSummary processDue(int limit) {
        int succeeded = 0;
        int retried = 0;
        int failed = 0;
        for (int index = 0; index < limit; index++) {
            Instant now = Instant.now(clock);
            UUID[] claimed = new UUID[1];
            try {
                Optional<UUID> processed = transactions.execute(status -> completeNext(now, claimed));
                if (processed == null || processed.isEmpty()) break;
                succeeded++;
                count("succeeded");
            } catch (RuntimeException exception) {
                if (claimed[0] == null) {
                    LOG.warn("Automation execution could not claim pending work");
                    break;
                }
                FailureState state = transactions.execute(status -> recordFailure(claimed[0], now));
                if (state == null) continue;
                if ("FAILED".equals(state.status())) {
                    failed++;
                    count("failed");
                } else {
                    retried++;
                    count("retry");
                }
                LOG.warn("Automation execution attempt failed executionId={} attempts={} code={}",
                        claimed[0], state.attempts(), FAILURE_CODE);
            }
        }
        return new ExecutionSummary(succeeded, retried, failed);
    }

    private Optional<UUID> completeNext(Instant now, UUID[] claimed) {
        Optional<PendingExecution> pending = jdbc.query(
                        """
                        SELECT execution_id, recipient_user_id, resource_id, correlation_id
                        FROM automation.executions
                        WHERE status = 'PENDING' AND next_attempt_at <= ?
                        ORDER BY next_attempt_at, created_at, execution_id
                        FOR UPDATE SKIP LOCKED
                        LIMIT 1
                        """,
                        (resultSet, row) -> new PendingExecution(
                                resultSet.getObject("execution_id", UUID.class),
                                resultSet.getObject("recipient_user_id", UUID.class),
                                resultSet.getObject("resource_id", UUID.class),
                                resultSet.getObject("correlation_id", UUID.class)),
                        Timestamp.from(now))
                .stream()
                .findFirst();
        if (pending.isEmpty()) return Optional.empty();
        PendingExecution execution = pending.orElseThrow();
        claimed[0] = execution.executionId();
        jdbc.update(
                """
                INSERT INTO automation.notifications
                    (notification_id, execution_id, recipient_user_id, notification_type,
                     resource_type, resource_id, status, version, created_at, read_at)
                VALUES (?, ?, ?, 'TICKET_WAITING_FOR_REQUESTER',
                        'TICKET', ?, 'UNREAD', 0, ?, NULL)
                """,
                UUID.randomUUID(),
                execution.executionId(),
                execution.recipientUserId(),
                execution.ticketId(),
                Timestamp.from(now));
        jdbc.update(
                """
                UPDATE automation.executions
                SET status = 'SUCCEEDED', attempts = attempts + 1, completed_at = ?,
                    last_error_code = NULL, updated_at = ?
                WHERE execution_id = ? AND status = 'PENDING'
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                execution.executionId());
        addTerminalEvent(execution.executionId(), execution.correlationId(), true, now);
        return Optional.of(execution.executionId());
    }

    private FailureState recordFailure(UUID executionId, Instant now) {
        Optional<FailureState> current = jdbc.query(
                        """
                        SELECT status, attempts
                        FROM automation.executions
                        WHERE execution_id = ?
                        FOR UPDATE
                        """,
                        (resultSet, row) -> new FailureState(
                                resultSet.getString("status"), resultSet.getInt("attempts")),
                        executionId)
                .stream()
                .findFirst();
        if (current.isEmpty() || !"PENDING".equals(current.orElseThrow().status())) return null;
        int attempts = current.orElseThrow().attempts() + 1;
        boolean terminal = attempts >= MAX_ATTEMPTS;
        Instant next = terminal ? now : now.plus(delay(attempts));
        jdbc.update(
                """
                UPDATE automation.executions
                SET status = ?, attempts = ?, next_attempt_at = ?, completed_at = ?,
                    last_error_code = ?, updated_at = ?
                WHERE execution_id = ?
                """,
                terminal ? "FAILED" : "PENDING",
                attempts,
                Timestamp.from(next),
                terminal ? Timestamp.from(now) : null,
                FAILURE_CODE,
                Timestamp.from(now),
                executionId);
        if (terminal) {
            UUID correlationId = jdbc.queryForObject(
                    "SELECT correlation_id FROM automation.executions WHERE execution_id = ?",
                    UUID.class,
                    executionId);
            addTerminalEvent(executionId, correlationId, false, now);
        }
        return new FailureState(terminal ? "FAILED" : "PENDING", attempts);
    }

    Duration delay(int attempts) {
        long exponential = BASE_DELAY.toMillis() << Math.min(Math.max(attempts - 1, 0), 20);
        long capped = Math.min(exponential, MAX_DELAY.toMillis());
        long half = capped / 2;
        return Duration.ofMillis(half + jitter.applyAsLong(capped - half));
    }

    private void addTerminalEvent(UUID executionId, UUID correlationId, boolean success, Instant now) {
        String eventType = success
                ? "AUTOMATION_EXECUTION_SUCCEEDED"
                : "AUTOMATION_EXECUTION_FAILED";
        String result = success ? "SUCCESS" : "FAILED";
        String payload = success
                ? "{\"ruleCode\":\"SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER\","
                        + "\"actionType\":\"IN_APP_NOTIFICATION\"}"
                : "{\"ruleCode\":\"SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER\","
                        + "\"actionType\":\"IN_APP_NOTIFICATION\","
                        + "\"failureCode\":\"NOTIFICATION_PERSISTENCE_FAILED\"}";
        jdbc.update(
                """
                INSERT INTO automation.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '1.0', ?, 'AUTOMATION', 'AUTOMATION', ?,
                        'AUTOMATION_EXECUTION', ?, 1, ?, ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                eventType,
                Timestamp.from(now),
                executionId,
                executionId,
                result,
                correlationId,
                payload,
                Timestamp.from(now));
    }

    private void count(String outcome) {
        Counter.builder("jaxon.automation.execution")
                .tag("action", JdbcAutomationTriggerReceiver.ACTION_TYPE)
                .tag("outcome", outcome)
                .register(meters)
                .increment();
    }

    record ExecutionSummary(int succeeded, int retried, int failed) {}

    private record PendingExecution(
            UUID executionId, UUID recipientUserId, UUID ticketId, UUID correlationId) {}

    private record FailureState(String status, int attempts) {}
}
