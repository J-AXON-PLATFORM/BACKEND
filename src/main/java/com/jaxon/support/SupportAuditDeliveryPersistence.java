package com.jaxon.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads only Support's outbox and writes only Support's per-consumer delivery state. */
@Component
class SupportAuditDeliveryPersistence {

    static final String CONSUMER = "AUDIT";
    static final int MAX_ATTEMPTS = 10;

    private final JdbcTemplate jdbc;

    SupportAuditDeliveryPersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<DueEvent> claimNextDue(Instant now) {
        return jdbc.query(
                        """
                        SELECT e.event_id, e.event_type, e.event_version, e.occurred_at, e.actor_type,
                               e.actor_id, e.resource_type, e.resource_id, e.resource_version, e.result,
                               e.correlation_id, e.payload::text AS payload
                        FROM support.outbox_events e
                        LEFT JOIN support.outbox_deliveries d
                            ON d.event_id = e.event_id AND d.consumer = 'AUDIT'
                        WHERE e.event_type IN (
                               'TICKET_CREATED',
                               'TICKET_COMMENT_ADDED',
                               'TICKET_ASSIGNED',
                               'TICKET_PRIORITY_CHANGED',
                               'TICKET_STATUS_CHANGED',
                               'TICKET_RESOLVED',
                               'TICKET_CLOSED',
                               'TICKET_REOPENED')
                          AND e.event_version = '2.0'
                          AND (d.event_id IS NULL OR (d.status = 'PENDING' AND d.next_attempt_at <= ?))
                        ORDER BY COALESCE(d.next_attempt_at, e.occurred_at), e.event_id
                        FOR UPDATE OF e SKIP LOCKED
                        LIMIT 1
                        """,
                        (resultSet, index) -> new DueEvent(
                                resultSet.getObject("event_id", UUID.class),
                                resultSet.getString("event_type"),
                                resultSet.getString("event_version"),
                                resultSet.getTimestamp("occurred_at").toInstant(),
                                resultSet.getString("actor_type"),
                                resultSet.getObject("actor_id", UUID.class),
                                resultSet.getString("resource_type"),
                                resultSet.getObject("resource_id", UUID.class),
                                resultSet.getLong("resource_version"),
                                resultSet.getString("result"),
                                resultSet.getObject("correlation_id", UUID.class),
                                resultSet.getString("payload")),
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    void markDelivered(UUID eventId, Instant now) {
        jdbc.update(
                """
                INSERT INTO support.outbox_deliveries
                    (event_id, consumer, status, attempts, next_attempt_at, delivered_at,
                     last_error_code, updated_at)
                VALUES (?, 'AUDIT', 'DELIVERED', 1, ?, ?, NULL, ?)
                ON CONFLICT (event_id, consumer) DO UPDATE
                SET status = 'DELIVERED',
                    attempts = support.outbox_deliveries.attempts + 1,
                    delivered_at = EXCLUDED.delivered_at,
                    last_error_code = NULL,
                    updated_at = EXCLUDED.updated_at
                WHERE support.outbox_deliveries.status = 'PENDING'
                """,
                eventId,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    Optional<FailureState> recordFailure(
            UUID eventId, String errorCode, Instant now, IntFunction<Instant> nextAttempt) {
        boolean eventExists = !jdbc.queryForList(
                        "SELECT event_id FROM support.outbox_events WHERE event_id = ? FOR UPDATE",
                        UUID.class,
                        eventId)
                .isEmpty();
        if (!eventExists) return Optional.empty();
        var current = jdbc.query(
                        """
                        SELECT status, attempts FROM support.outbox_deliveries
                        WHERE event_id = ? AND consumer = 'AUDIT' FOR UPDATE
                        """,
                        (resultSet, index) -> new FailureState(
                                resultSet.getString("status"), resultSet.getInt("attempts")),
                        eventId)
                .stream()
                .findFirst();
        if (current.isPresent() && !"PENDING".equals(current.get().status())) return Optional.empty();
        int attempts = current.map(FailureState::attempts).orElse(0) + 1;
        String status = attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING";
        Instant next = "FAILED".equals(status) ? now : nextAttempt.apply(attempts);
        jdbc.update(
                """
                INSERT INTO support.outbox_deliveries
                    (event_id, consumer, status, attempts, next_attempt_at, delivered_at,
                     last_error_code, updated_at)
                VALUES (?, 'AUDIT', ?, ?, ?, NULL, ?, ?)
                ON CONFLICT (event_id, consumer) DO UPDATE
                SET status = EXCLUDED.status,
                    attempts = EXCLUDED.attempts,
                    next_attempt_at = EXCLUDED.next_attempt_at,
                    last_error_code = EXCLUDED.last_error_code,
                    updated_at = EXCLUDED.updated_at
                """,
                eventId,
                status,
                attempts,
                Timestamp.from(next),
                errorCode,
                Timestamp.from(now));
        return Optional.of(new FailureState(status, attempts));
    }

    record DueEvent(
            UUID eventId,
            String eventType,
            String eventVersion,
            Instant occurredAt,
            String actorType,
            UUID actorId,
            String resourceType,
            UUID resourceId,
            long resourceVersion,
            String result,
            UUID correlationId,
            String payload) {}

    record FailureState(String status, int attempts) {}
}
