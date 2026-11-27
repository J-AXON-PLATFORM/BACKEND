package com.jaxon.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = "jaxon.support.audit-dispatch.enabled=false")
class SupportAuditDispatchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SupportAuditDispatcher dispatcher;

    UUID ticketId;
    UUID actorId;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM support.outbox_events");
        jdbc.update("DELETE FROM support.ticket_lifecycle_events");
        jdbc.update("DELETE FROM support.ticket_comments");
        jdbc.update("DELETE FROM support.tickets");
        ticketId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        insertTicket(ticketId, actorId);
    }

    @Test
    void materializesOnlyTheEightVersionTwoTicketEventsAndRedeliveryIsIdempotent() {
        UUID historical = insertEvent(
                "TICKET_CREATED",
                "1.0",
                0,
                "{\"type\":\"INCIDENT\",\"status\":\"OPEN\",\"priority\":\"NORMAL\",\"source\":\"WEB\"}");
        UUID commentId = UUID.randomUUID();
        UUID lifecycleId = UUID.randomUUID();
        List<UUID> future = List.of(
                insertEvent(
                        "TICKET_CREATED",
                        "2.0",
                        0,
                        "{\"type\":\"INCIDENT\",\"status\":\"OPEN\","
                                + "\"priority\":\"NORMAL\",\"source\":\"WEB\"}"),
                insertEvent(
                        "TICKET_COMMENT_ADDED",
                        "2.0",
                        1,
                        "{\"commentId\":\"" + commentId + "\",\"visibility\":\"PUBLIC\"}"),
                insertEvent(
                        "TICKET_ASSIGNED",
                        "2.0",
                        1,
                        "{\"assigneeUserId\":\"" + actorId + "\"}"),
                insertEvent(
                        "TICKET_PRIORITY_CHANGED",
                        "2.0",
                        2,
                        "{\"previousPriority\":\"NORMAL\",\"newPriority\":\"HIGH\"}"),
                insertEvent(
                        "TICKET_STATUS_CHANGED",
                        "2.0",
                        3,
                        "{\"previousStatus\":\"OPEN\",\"newStatus\":\"IN_PROGRESS\"}"),
                insertEvent(
                        "TICKET_RESOLVED",
                        "2.0",
                        4,
                        lifecycle(lifecycleId, "IN_PROGRESS", "RESOLVED")),
                insertEvent(
                        "TICKET_CLOSED",
                        "2.0",
                        5,
                        lifecycleWithReason(lifecycleId, "RESOLVED", "CLOSED", "RESOLVED")),
                insertEvent(
                        "TICKET_REOPENED",
                        "2.0",
                        6,
                        lifecycle(lifecycleId, "CLOSED", "OPEN")));

        assertThat(dispatcher.dispatchDue(20))
                .isEqualTo(new SupportAuditDispatcher.DispatchSummary(8, 0, 0, 0));
        assertThat(future.stream().map(this::audit).toList())
                .hasSize(8)
                .allSatisfy(row -> assertThat(row)
                        .containsEntry("event_version", "2.0")
                        .containsEntry("producer_module", "SUPPORT")
                        .containsEntry("actor_type", "HUMAN_USER")
                        .containsEntry("actor_id", actorId)
                        .containsEntry("resource_type", "TICKET")
                        .containsEntry("resource_id", ticketId)
                        .containsEntry("result", "SUCCESS"));
        assertThat(audit(future.getFirst()).get("metadata").toString())
                .contains("\"type\": \"INCIDENT\"", "\"status\": \"OPEN\"");
        assertThat(audit(future.get(1)).get("metadata").toString())
                .contains(commentId.toString(), "\"visibility\": \"PUBLIC\"");
        assertThat(audit(future.get(3)).get("metadata").toString())
                .contains("\"previousPriority\": \"NORMAL\"", "\"newPriority\": \"HIGH\"");
        assertThat(audit(future.get(6)).get("metadata").toString())
                .contains("\"closeReason\": \"RESOLVED\"");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM support.outbox_deliveries WHERE event_id = ?",
                        Integer.class,
                        historical))
                .isZero();

        UUID duplicate = future.getFirst();
        jdbc.update("DELETE FROM support.outbox_deliveries WHERE event_id = ?", duplicate);
        assertThat(dispatcher.dispatchDue(1))
                .isEqualTo(new SupportAuditDispatcher.DispatchSummary(0, 1, 0, 0));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        Integer.class,
                        duplicate))
                .isEqualTo(1);
    }

    @Test
    void conflictRetriesExactlyTenTimesWithoutOverwritingAudit() {
        UUID eventId = insertEvent(
                "TICKET_CREATED",
                "2.0",
                0,
                "{\"type\":\"QUESTION\",\"status\":\"OPEN\",\"priority\":\"NORMAL\",\"source\":\"WEB\"}");
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, 'TICKET_CREATED', '2.0', 'SUPPORT', now(),
                        'HUMAN_USER', ?, 'TICKET', ?, 0, 'SUCCESS', ?, ?::jsonb)
                """,
                UUID.randomUUID(),
                eventId,
                actorId,
                ticketId,
                UUID.randomUUID(),
                "{\"type\":\"INCIDENT\",\"status\":\"OPEN\",\"priority\":\"NORMAL\",\"source\":\"WEB\"}");

        for (int attempt = 1; attempt <= 10; attempt++) {
            makeDue(eventId);
            assertThat(dispatcher.dispatchDue(1)).isEqualTo(attempt == 10
                    ? new SupportAuditDispatcher.DispatchSummary(0, 0, 0, 1)
                    : new SupportAuditDispatcher.DispatchSummary(0, 0, 1, 0));
            assertThat(delivery(eventId))
                    .containsEntry("attempts", attempt)
                    .containsEntry("last_error_code", "AUDIT_CONFLICT")
                    .containsEntry("status", attempt == 10 ? "FAILED" : "PENDING");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        Integer.class,
                        eventId))
                .isEqualTo(1);
    }

    @Test
    void unavailableAuditRollsBackTheFactAndDoesNotBlockTheNextEvent() {
        UUID first = insertEvent(
                "TICKET_CREATED",
                "2.0",
                0,
                "{\"type\":\"OTHER\",\"status\":\"OPEN\",\"priority\":\"NORMAL\",\"source\":\"WEB\"}");
        jdbc.execute(
                """
                CREATE FUNCTION audit_reporting.reject_support_audit_insert()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.source_event_id = '%s'::uuid THEN
                        RAISE EXCEPTION 'audit unavailable';
                    END IF;
                    RETURN NEW;
                END $$
                """.formatted(first));
        jdbc.execute(
                """
                CREATE TRIGGER reject_support_audit_insert
                BEFORE INSERT ON audit_reporting.audit_events
                FOR EACH ROW EXECUTE FUNCTION audit_reporting.reject_support_audit_insert()
                """);
        UUID second = insertEvent(
                "TICKET_ASSIGNED",
                "2.0",
                1,
                "{\"assigneeUserId\":\"" + actorId + "\"}");
        try {
            assertThat(dispatcher.dispatchDue(2))
                    .isEqualTo(new SupportAuditDispatcher.DispatchSummary(1, 0, 1, 0));
            assertThat(delivery(first))
                    .containsEntry("status", "PENDING")
                    .containsEntry("attempts", 1)
                    .containsEntry("last_error_code", "AUDIT_UNAVAILABLE");
            assertThat(delivery(second)).containsEntry("status", "DELIVERED");
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                            Integer.class,
                            first))
                    .isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_support_audit_insert ON audit_reporting.audit_events");
            jdbc.execute("DROP FUNCTION IF EXISTS audit_reporting.reject_support_audit_insert()");
        }
    }

    private UUID insertEvent(String type, String version, long resourceVersion, String payload) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO support.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, ?, ?, 'SUPPORT', 'HUMAN_USER', ?, 'TICKET', ?, ?,
                        'SUCCESS', ?, ?::jsonb, ?)
                """,
                eventId,
                type,
                version,
                Timestamp.from(now),
                actorId,
                ticketId,
                resourceVersion,
                UUID.randomUUID(),
                payload,
                Timestamp.from(now));
        return eventId;
    }

    private void insertTicket(UUID id, UUID requesterId) {
        jdbc.update(
                """
                INSERT INTO support.tickets
                    (id, type, summary, description, status, priority, source,
                     requester_user_id, created_by_user_id, created_at, updated_at, version)
                VALUES (?, 'INCIDENT', 'Audit test', 'Audit delivery test', 'OPEN', 'NORMAL',
                        'WEB', ?, ?, now(), now(), 0)
                """,
                id,
                requesterId,
                requesterId);
    }

    private Map<String, Object> delivery(UUID eventId) {
        return jdbc.queryForMap(
                "SELECT * FROM support.outbox_deliveries WHERE event_id = ? AND consumer = 'AUDIT'",
                eventId);
    }

    private Map<String, Object> audit(UUID eventId) {
        return jdbc.queryForMap(
                """
                SELECT event_version, producer_module, actor_type, actor_id, resource_type,
                       resource_id, resource_version, result, metadata::text AS metadata
                FROM audit_reporting.audit_events WHERE source_event_id = ?
                """,
                eventId);
    }

    private void makeDue(UUID eventId) {
        jdbc.update(
                """
                UPDATE support.outbox_deliveries
                SET next_attempt_at = now() - interval '1 second'
                WHERE event_id = ?
                """,
                eventId);
    }

    private static String lifecycle(UUID id, String from, String to) {
        return "{\"lifecycleEventId\":\"" + id + "\",\"fromStatus\":\"" + from
                + "\",\"toStatus\":\"" + to + "\"}";
    }

    private static String lifecycleWithReason(
            UUID id, String from, String to, String reason) {
        return "{\"lifecycleEventId\":\"" + id + "\",\"fromStatus\":\"" + from
                + "\",\"toStatus\":\"" + to + "\",\"closeReason\":\"" + reason + "\"}";
    }
}
