package com.jaxon.discovery;

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
@SpringBootTest(properties = "jaxon.discovery.audit-dispatch.enabled=false")
class DiscoveryAuditDispatchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DiscoveryAuditDispatcher dispatcher;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM discovery.outbox_events");
    }

    @Test
    void materializesOnlyTheFourVersionTwoEventsAndRedeliveryIsIdempotent() {
        UUID actorId = UUID.randomUUID();
        UUID historical = insertEvent(
                "DISCOVERY_SCOPE_CREATED", "1.0", "AUTHORIZED_SCOPE", 0,
                "{\"status\":\"ACTIVE\"}", actorId);
        List<UUID> future = List.of(
                insertEvent("DISCOVERY_SCOPE_CREATED", "2.0", "AUTHORIZED_SCOPE", 0, "{}", actorId),
                insertEvent(
                        "DISCOVERY_SCOPE_STATUS_CHANGED",
                        "2.0",
                        "AUTHORIZED_SCOPE",
                        2,
                        "{\"previousStatus\":\"DISABLED\",\"newStatus\":\"ACTIVE\"}",
                        actorId),
                insertEvent(
                        "DISCOVERY_EXECUTOR_PRE_REGISTERED",
                        "2.0",
                        "DISCOVERY_EXECUTOR",
                        0,
                        "{}",
                        actorId),
                insertEvent(
                        "DISCOVERY_EXECUTOR_ACTIVATED",
                        "2.0",
                        "DISCOVERY_EXECUTOR",
                        1,
                        "{}",
                        actorId));

        assertThat(dispatcher.dispatchDue(10))
                .isEqualTo(new DiscoveryAuditDispatcher.DispatchSummary(4, 0, 0, 0));
        assertThat(future.stream().map(this::audit).toList())
                .hasSize(4)
                .allSatisfy(row -> {
                    assertThat(row).containsEntry("resource_version",
                            "DISCOVERY_SCOPE_STATUS_CHANGED".equals(row.get("event_type")) ? 2L
                                    : "DISCOVERY_EXECUTOR_ACTIVATED".equals(row.get("event_type")) ? 1L : 0L);
                    if ("DISCOVERY_SCOPE_STATUS_CHANGED".equals(row.get("event_type"))) {
                        assertThat(row).containsEntry(
                                "metadata", "{\"newStatus\": \"ACTIVE\", \"previousStatus\": \"DISABLED\"}");
                    } else {
                        assertThat(row).containsEntry("metadata", "{}");
                    }
                });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_deliveries WHERE event_id = ?",
                        Integer.class,
                        historical))
                .isZero();

        UUID duplicate = future.getFirst();
        jdbc.update("DELETE FROM discovery.outbox_deliveries WHERE event_id = ?", duplicate);
        assertThat(dispatcher.dispatchDue(1))
                .isEqualTo(new DiscoveryAuditDispatcher.DispatchSummary(0, 1, 0, 0));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        Integer.class,
                        duplicate))
                .isEqualTo(1);
    }

    @Test
    void conflictRetriesExactlyTenTimesWithoutOverwritingAudit() {
        UUID actorId = UUID.randomUUID();
        UUID eventId = insertEvent(
                "DISCOVERY_EXECUTOR_ACTIVATED", "2.0", "DISCOVERY_EXECUTOR", 1, "{}", actorId);
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, 'DISCOVERY_EXECUTOR_ACTIVATED', '2.0', 'DISCOVERY', now(),
                        'HUMAN_USER', ?, 'DISCOVERY_EXECUTOR', ?, 1, 'SUCCESS', ?, '{}'::jsonb)
                """,
                UUID.randomUUID(), eventId, actorId, UUID.randomUUID(), UUID.randomUUID());

        for (int attempt = 1; attempt <= 10; attempt++) {
            makeDue(eventId);
            assertThat(dispatcher.dispatchDue(1)).isEqualTo(attempt == 10
                    ? new DiscoveryAuditDispatcher.DispatchSummary(0, 0, 0, 1)
                    : new DiscoveryAuditDispatcher.DispatchSummary(0, 0, 1, 0));
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
    void unavailableAuditDoesNotMutateTheProducerFactOrBlockTheNextEvent() {
        UUID actorId = UUID.randomUUID();
        UUID first = insertEvent(
                "DISCOVERY_SCOPE_CREATED", "2.0", "AUTHORIZED_SCOPE", 0, "{}", actorId);
        jdbc.execute(
                """
                CREATE FUNCTION audit_reporting.reject_discovery_audit_insert() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.source_event_id = '%s'::uuid THEN RAISE EXCEPTION 'audit unavailable'; END IF;
                    RETURN NEW;
                END $$
                """.formatted(first));
        jdbc.execute(
                """
                CREATE TRIGGER reject_discovery_audit_insert BEFORE INSERT ON audit_reporting.audit_events
                FOR EACH ROW EXECUTE FUNCTION audit_reporting.reject_discovery_audit_insert()
                """);
        UUID second = insertEvent(
                "DISCOVERY_EXECUTOR_PRE_REGISTERED", "2.0", "DISCOVERY_EXECUTOR", 0, "{}", actorId);
        try {
            assertThat(dispatcher.dispatchDue(2))
                    .isEqualTo(new DiscoveryAuditDispatcher.DispatchSummary(1, 0, 1, 0));
            assertThat(delivery(first))
                    .containsEntry("status", "PENDING")
                    .containsEntry("attempts", 1)
                    .containsEntry("last_error_code", "AUDIT_UNAVAILABLE");
            assertThat(delivery(second)).containsEntry("status", "DELIVERED");
            assertThat(jdbc.queryForObject(
                            "SELECT event_version FROM discovery.outbox_events WHERE event_id = ?",
                            String.class,
                            first))
                    .isEqualTo("2.0");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_discovery_audit_insert ON audit_reporting.audit_events");
            jdbc.execute("DROP FUNCTION IF EXISTS audit_reporting.reject_discovery_audit_insert()");
        }
    }

    private UUID insertEvent(
            String type, String version, String resourceType, long resourceVersion, String payload, UUID actorId) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, ?, ?, 'DISCOVERY', 'HUMAN_USER', ?, ?, ?, ?,
                        'SUCCESS', ?, ?::jsonb, ?)
                """,
                eventId,
                type,
                version,
                Timestamp.from(now),
                actorId,
                resourceType,
                UUID.randomUUID(),
                resourceVersion,
                UUID.randomUUID(),
                payload,
                Timestamp.from(now));
        return eventId;
    }

    private Map<String, Object> delivery(UUID eventId) {
        return jdbc.queryForMap(
                "SELECT * FROM discovery.outbox_deliveries WHERE event_id = ? AND consumer = 'AUDIT'",
                eventId);
    }

    private Map<String, Object> audit(UUID eventId) {
        return jdbc.queryForMap(
                """
                SELECT event_type, resource_type, resource_version, metadata::text AS metadata
                FROM audit_reporting.audit_events WHERE source_event_id = ?
                """,
                eventId);
    }

    private void makeDue(UUID eventId) {
        jdbc.update(
                """
                UPDATE discovery.outbox_deliveries
                SET next_attempt_at = now() - interval '1 second'
                WHERE event_id = ?
                """,
                eventId);
    }
}
