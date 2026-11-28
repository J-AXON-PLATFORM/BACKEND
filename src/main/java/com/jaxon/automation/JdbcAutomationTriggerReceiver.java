package com.jaxon.automation;

import com.jaxon.AutomationTrigger;
import com.jaxon.AutomationTriggerOutcome;
import com.jaxon.AutomationTriggerReceiver;
import com.jaxon.AutomationTriggerRejectedException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcAutomationTriggerReceiver implements AutomationTriggerReceiver {

    static final UUID RULE_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    static final String RULE_CODE = "SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER";
    static final String ACTION_TYPE = "IN_APP_NOTIFICATION";

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcAutomationTriggerReceiver(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AutomationTriggerOutcome accept(AutomationTrigger trigger) {
        if (!valid(trigger)) {
            throw new AutomationTriggerRejectedException(
                    AutomationTriggerRejectedException.Reason.INVALID);
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        UUID executionId = UUID.randomUUID();
        int inserted = jdbc.update(
                """
                INSERT INTO automation.executions
                    (execution_id, rule_id, source_event_id, source_event_type,
                     source_event_version, source_occurred_at, producer_module,
                     resource_type, resource_id, resource_version, actor_type, actor_id,
                     correlation_id, recipient_user_id, previous_status, new_status,
                     status, attempts, next_attempt_at, expires_at, completed_at,
                     last_error_code, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        'PENDING', 0, ?, NULL, NULL, NULL, ?, ?)
                ON CONFLICT (rule_id, source_event_id) DO NOTHING
                """,
                executionId,
                RULE_ID,
                trigger.sourceEventId(),
                trigger.sourceEventType(),
                trigger.sourceEventVersion(),
                Timestamp.from(trigger.sourceOccurredAt()),
                trigger.producerModule(),
                trigger.resourceType(),
                trigger.resourceId(),
                trigger.resourceVersion(),
                trigger.actorType(),
                trigger.actorId(),
                trigger.correlationId(),
                trigger.recipientUserId(),
                trigger.previousStatus(),
                trigger.newStatus(),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
        if (inserted == 1) {
            return AutomationTriggerOutcome.ACCEPTED;
        }
        ExistingTrigger existing = jdbc.query(
                        """
                        SELECT source_event_type, source_event_version, source_occurred_at,
                               producer_module, resource_type, resource_id, resource_version,
                               actor_type, actor_id, correlation_id, recipient_user_id,
                               previous_status, new_status
                        FROM automation.executions
                        WHERE rule_id = ? AND source_event_id = ?
                        """,
                        (resultSet, row) -> new ExistingTrigger(
                                resultSet.getString("source_event_type"),
                                resultSet.getString("source_event_version"),
                                resultSet.getTimestamp("source_occurred_at").toInstant(),
                                resultSet.getString("producer_module"),
                                resultSet.getString("resource_type"),
                                resultSet.getObject("resource_id", UUID.class),
                                resultSet.getLong("resource_version"),
                                resultSet.getString("actor_type"),
                                resultSet.getObject("actor_id", UUID.class),
                                resultSet.getObject("correlation_id", UUID.class),
                                resultSet.getObject("recipient_user_id", UUID.class),
                                resultSet.getString("previous_status"),
                                resultSet.getString("new_status")),
                        RULE_ID,
                        trigger.sourceEventId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new AutomationTriggerRejectedException(
                        AutomationTriggerRejectedException.Reason.CONFLICT));
        if (!existing.matches(trigger)) {
            throw new AutomationTriggerRejectedException(
                    AutomationTriggerRejectedException.Reason.CONFLICT);
        }
        return AutomationTriggerOutcome.DUPLICATE;
    }

    private static boolean valid(AutomationTrigger trigger) {
        return trigger != null
                && uuidV4(trigger.sourceEventId())
                && "TICKET_STATUS_CHANGED".equals(trigger.sourceEventType())
                && "2.0".equals(trigger.sourceEventVersion())
                && trigger.sourceOccurredAt() != null
                && "SUPPORT".equals(trigger.producerModule())
                && "TICKET".equals(trigger.resourceType())
                && uuidV4(trigger.resourceId())
                && trigger.resourceVersion() >= 1
                && "HUMAN_USER".equals(trigger.actorType())
                && uuidV4(trigger.actorId())
                && uuidV4(trigger.correlationId())
                && uuidV4(trigger.recipientUserId())
                && "IN_PROGRESS".equals(trigger.previousStatus())
                && "WAITING_FOR_REQUESTER".equals(trigger.newStatus());
    }

    private static boolean uuidV4(UUID value) {
        return value != null && UUID_V4.matcher(value.toString()).matches();
    }

    private record ExistingTrigger(
            String sourceEventType,
            String sourceEventVersion,
            Instant sourceOccurredAt,
            String producerModule,
            String resourceType,
            UUID resourceId,
            long resourceVersion,
            String actorType,
            UUID actorId,
            UUID correlationId,
            UUID recipientUserId,
            String previousStatus,
            String newStatus) {

        boolean matches(AutomationTrigger trigger) {
            return sourceEventType.equals(trigger.sourceEventType())
                    && sourceEventVersion.equals(trigger.sourceEventVersion())
                    && sourceOccurredAt.equals(trigger.sourceOccurredAt())
                    && producerModule.equals(trigger.producerModule())
                    && resourceType.equals(trigger.resourceType())
                    && resourceId.equals(trigger.resourceId())
                    && resourceVersion == trigger.resourceVersion()
                    && actorType.equals(trigger.actorType())
                    && Objects.equals(actorId, trigger.actorId())
                    && correlationId.equals(trigger.correlationId())
                    && recipientUserId.equals(trigger.recipientUserId())
                    && previousStatus.equals(trigger.previousStatus())
                    && newStatus.equals(trigger.newStatus());
        }
    }
}
