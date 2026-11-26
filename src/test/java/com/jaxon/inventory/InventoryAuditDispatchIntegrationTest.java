package com.jaxon.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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
@SpringBootTest(properties = "jaxon.inventory.audit-dispatch.enabled=false")
class InventoryAuditDispatchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InventoryAuditDispatcher dispatcher;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM inventory.outbox_events");
        jdbc.update("DELETE FROM inventory.assets");
    }

    @Test
    void materializesOnlyVersionTwoAndRedeliveryIsIdempotent() {
        UUID actorId = UUID.randomUUID();
        UUID assetId = insertAsset();
        UUID historical = insertEvent(assetId, actorId, "1.0");
        UUID future = insertEvent(assetId, actorId, "2.0");

        assertThat(dispatcher.dispatchDue(10))
                .isEqualTo(new InventoryAuditDispatcher.DispatchSummary(1, 0, 0, 0));
        assertThat(audit(future))
                .containsEntry("event_type", "ASSET_CREATED")
                .containsEntry("event_version", "2.0")
                .containsEntry("producer_module", "INVENTORY")
                .containsEntry("actor_id", actorId)
                .containsEntry("resource_type", "ASSET")
                .containsEntry("resource_id", assetId)
                .containsEntry("resource_version", 0L)
                .containsEntry("result", "SUCCESS")
                .containsEntry("metadata", "{}");
        assertThat(delivery(future))
                .containsEntry("status", "DELIVERED")
                .containsEntry("attempts", 1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM inventory.outbox_deliveries WHERE event_id = ?",
                        Integer.class,
                        historical))
                .isZero();

        jdbc.update("DELETE FROM inventory.outbox_deliveries WHERE event_id = ?", future);
        assertThat(dispatcher.dispatchDue(1))
                .isEqualTo(new InventoryAuditDispatcher.DispatchSummary(0, 1, 0, 0));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        Integer.class,
                        future))
                .isEqualTo(1);
    }

    @Test
    void conflictRetriesExactlyTenTimesWithoutOverwritingAudit() {
        UUID actorId = UUID.randomUUID();
        UUID assetId = insertAsset();
        UUID eventId = insertEvent(assetId, actorId, "2.0");
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, 'ASSET_CREATED', '2.0', 'INVENTORY', now(), 'HUMAN_USER', ?,
                        'ASSET', ?, 0, 'SUCCESS', ?, '{}'::jsonb)
                """,
                UUID.randomUUID(), eventId, actorId, assetId, UUID.randomUUID());

        for (int attempt = 1; attempt <= 10; attempt++) {
            makeDue(eventId);
            InventoryAuditDispatcher.DispatchSummary summary = dispatcher.dispatchDue(1);
            assertThat(summary).isEqualTo(attempt == 10
                    ? new InventoryAuditDispatcher.DispatchSummary(0, 0, 0, 1)
                    : new InventoryAuditDispatcher.DispatchSummary(0, 0, 1, 0));
            Map<String, Object> delivery = delivery(eventId);
            assertThat(delivery)
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
    void unavailableAuditDoesNotMutateTheProducerFact() {
        UUID actorId = UUID.randomUUID();
        UUID assetId = insertAsset();
        UUID eventId = insertEvent(assetId, actorId, "2.0");
        jdbc.execute(
                """
                CREATE FUNCTION audit_reporting.reject_inventory_audit_insert() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'audit unavailable'; END $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_inventory_audit_insert BEFORE INSERT ON audit_reporting.audit_events
                FOR EACH ROW EXECUTE FUNCTION audit_reporting.reject_inventory_audit_insert()
                """);
        try {
            assertThat(dispatcher.dispatchDue(1))
                    .isEqualTo(new InventoryAuditDispatcher.DispatchSummary(0, 0, 1, 0));
            assertThat(delivery(eventId))
                    .containsEntry("status", "PENDING")
                    .containsEntry("attempts", 1)
                    .containsEntry("last_error_code", "AUDIT_UNAVAILABLE");
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM inventory.assets WHERE id = ?", Integer.class, assetId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT event_version FROM inventory.outbox_events WHERE event_id = ?",
                            String.class,
                            eventId))
                    .isEqualTo("2.0");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_inventory_audit_insert ON audit_reporting.audit_events");
            jdbc.execute("DROP FUNCTION IF EXISTS audit_reporting.reject_inventory_audit_insert()");
        }
    }

    private UUID insertAsset() {
        UUID assetId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO inventory.assets
                    (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, 'Audited asset', 'DESKTOP', 'ACTIVE', now(), now(), 0)
                """,
                assetId);
        return assetId;
    }

    private UUID insertEvent(UUID assetId, UUID actorId, String version) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO inventory.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'ASSET_CREATED', ?, ?, 'INVENTORY', 'HUMAN_USER', ?,
                        'ASSET', ?, 0, 'SUCCESS', ?, '{}'::jsonb, ?)
                """,
                eventId, version, Timestamp.from(now), actorId, assetId, UUID.randomUUID(), Timestamp.from(now));
        return eventId;
    }

    private Map<String, Object> audit(UUID eventId) {
        return jdbc.queryForMap(
                """
                SELECT event_type, event_version, producer_module, actor_id, resource_type,
                       resource_id, resource_version, result, metadata::text AS metadata
                FROM audit_reporting.audit_events WHERE source_event_id = ?
                """,
                eventId);
    }

    private Map<String, Object> delivery(UUID eventId) {
        return jdbc.queryForMap(
                "SELECT * FROM inventory.outbox_deliveries WHERE event_id = ? AND consumer = 'AUDIT'",
                eventId);
    }

    private void makeDue(UUID eventId) {
        jdbc.update(
                """
                UPDATE inventory.outbox_deliveries
                SET next_attempt_at = now() - interval '1 second'
                WHERE event_id = ?
                """,
                eventId);
    }
}
