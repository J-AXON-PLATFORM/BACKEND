package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class AuditMigrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @BeforeEach
    void resetDatabase() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.execute(
                """
                DROP SCHEMA IF EXISTS inventory, discovery, collector, support, ai, identity,
                    automation, audit_reporting CASCADE
                """);
    }

    @Test
    void upgradesFromAiSourceDisableAndEnforcesDeliveryAndAuditRules() {
        migrate("20261125000000");
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        UUID sourceId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO ai.document_sources
                    (id, name, media_type, size_bytes, sha256, storage_key, status,
                     created_by_user_id, created_at, updated_at, version)
                VALUES (?, 'Historical', 'text/plain', 7, ?, ?, 'DISABLED', ?, ?, ?, 3)
                """,
                sourceId, "0".repeat(64), UUID.randomUUID() + ".txt", UUID.randomUUID(),
                Timestamp.from(NOW), Timestamp.from(NOW));
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO ai.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module, actor_type, actor_id,
                     resource_type, resource_id, resource_version, result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'AI_SOURCE_DISABLED', '1.0', ?, 'AI', 'HUMAN_USER', ?, 'AI_SOURCE', ?, 3, 'SUCCESS', ?,
                        '{"previousStatus":"READY","newStatus":"DISABLED"}'::jsonb, ?)
                """,
                eventId, Timestamp.from(NOW), UUID.randomUUID(), sourceId, UUID.randomUUID(), Timestamp.from(NOW));

        migrate(null);

        assertThat(jdbc.queryForObject("SELECT status FROM ai.outbox_events WHERE event_id = ?", String.class, eventId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.outbox_deliveries", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_reporting.audit_events", Integer.class)).isZero();

        delivery(jdbc, eventId, "PENDING", 3, null, null);
        assertThatThrownBy(() -> delivery(jdbc, eventId, "PENDING", 1, null, null))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        jdbc.update("DELETE FROM ai.outbox_deliveries");
        for (Object[] invalid : new Object[][] {
            {"DELIVERED", 1, null, null},
            {"PENDING", 10, null, "AUDIT_UNAVAILABLE"},
            {"FAILED", 9, null, "AUDIT_UNAVAILABLE"},
            {"FAILED", 10, null, null},
            {"PENDING", 1, null, "OTHER"},
            {"PENDING", 11, null, null},
            {"UNKNOWN", 1, null, null}
        }) {
            assertThatThrownBy(() -> delivery(jdbc, eventId, (String) invalid[0], (Integer) invalid[1],
                            (Timestamp) invalid[2], (String) invalid[3]))
                    .as(String.valueOf(invalid[0]) + invalid[1] + invalid[3])
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO ai.outbox_deliveries
                            (event_id, consumer, status, attempts, next_attempt_at, updated_at)
                        VALUES (?, 'AUTOMATION', 'PENDING', 0, now(), now())
                        """,
                        eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
        delivery(jdbc, eventId, "FAILED", 10, null, "AUDIT_CONFLICT");
        jdbc.update("DELETE FROM ai.outbox_events WHERE event_id = ?", eventId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.outbox_deliveries", Integer.class)).isZero();

        audit(jdbc, "AI_SOURCE_DISABLED", "AI", "AI_SOURCE", "SUCCESS", 3L,
                "{\"previousStatus\":\"FAILED\",\"newStatus\":\"DISABLED\"}");
        for (Object[] invalid : new Object[][] {
            {"AI_SOURCE_INDEXED", "AI", "AI_SOURCE", "SUCCESS", 3L, "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "IDENTITY", "AI_SOURCE", "SUCCESS", 3L, "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "AI", "HUMAN_USER", "SUCCESS", 3L, "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "AI", "AI_SOURCE", "DENIED", 3L, "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "AI", "AI_SOURCE", "SUCCESS", null, "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "AI", "AI_SOURCE", "SUCCESS", 3L, "{\"previousStatus\":\"PENDING\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "AI", "AI_SOURCE", "SUCCESS", 3L, "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\",\"name\":\"x\"}"},
            {"AI_SOURCE_DISABLED", "AI", "AI_SOURCE", "SUCCESS", 3L, "{\"status\":\"DISABLED\"}"}
        }) {
            assertThatThrownBy(() -> audit(jdbc, (String) invalid[0], (String) invalid[1], (String) invalid[2],
                            (String) invalid[3], (Long) invalid[4], (String) invalid[5]))
                    .as(String.valueOf(invalid[0]) + invalid[1] + invalid[2] + invalid[3] + invalid[5])
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        auditIdentity(jdbc, "HUMAN_USER_CREATED", "2.0", "IDENTITY", "HUMAN_USER", "SUCCESS", null,
                "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR|END_USER|IT_OPERATOR|SUPPORT_TECHNICIAN|SYSTEM_ADMIN\"}");
        for (Object[] invalid : new Object[][] {
            {"1.0", "IDENTITY", "HUMAN_USER", "SUCCESS", null, "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR\"}"},
            {"2.0", "AI", "HUMAN_USER", "SUCCESS", null, "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR\"}"},
            {"2.0", "IDENTITY", "AI_SOURCE", "SUCCESS", null, "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR\"}"},
            {"2.0", "IDENTITY", "HUMAN_USER", "DENIED", null, "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR\"}"},
            {"2.0", "IDENTITY", "HUMAN_USER", "SUCCESS", 1L, "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR\"}"},
            {"2.0", "IDENTITY", "HUMAN_USER", "SUCCESS", null, "{\"status\":\"DISABLED\",\"roles\":\"AUDITOR\"}"},
            {"2.0", "IDENTITY", "HUMAN_USER", "SUCCESS", null, "{\"status\":\"ACTIVE\",\"roles\":\"END_USER|AUDITOR\"}"},
            {"2.0", "IDENTITY", "HUMAN_USER", "SUCCESS", null, "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR|AUDITOR\"}"},
            {"2.0", "IDENTITY", "HUMAN_USER", "SUCCESS", null, "{\"status\":\"ACTIVE\",\"roles\":\"SUPERUSER\"}"},
            {"2.0", "IDENTITY", "HUMAN_USER", "SUCCESS", null, "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR,END_USER\"}"},
            {"2.0", "IDENTITY", "HUMAN_USER", "SUCCESS", null, "{\"status\":\"ACTIVE\",\"roles\":\"AUDITOR\",\"extra\":\"x\"}"}
        }) {
            assertThatThrownBy(() -> auditIdentity(
                            jdbc,
                            "HUMAN_USER_CREATED",
                            (String) invalid[0],
                            (String) invalid[1],
                            (String) invalid[2],
                            (String) invalid[3],
                            (Long) invalid[4],
                            (String) invalid[5]))
                    .as(java.util.Arrays.toString(invalid))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        auditIdentity(jdbc, "HUMAN_LOGIN_SUCCEEDED", "2.0", "IDENTITY",
                "AUTHENTICATION_ATTEMPT", "SUCCESS", null, "{}");
        auditIdentity(jdbc, "HUMAN_LOGOUT_SUCCEEDED", "2.0", "IDENTITY",
                "HUMAN_SESSION", "SUCCESS", null, "{}");
        auditIdentityWithActor(jdbc, "HUMAN_LOGIN_FAILED", "2.0", "IDENTITY",
                null, "AUTHENTICATION_ATTEMPT", "DENIED", null,
                "{\"reason\":\"INVALID_CREDENTIALS\"}");
        auditIdentity(jdbc, "AGENT_PUBLIC_KEY_REGISTERED", "2.0", "IDENTITY",
                "AGENT_TECHNICAL_IDENTITY", "SUCCESS", null, "{}");
        auditIdentity(jdbc, "DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED", "2.0", "IDENTITY",
                "DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY", "SUCCESS", null, "{}");
        assertThatThrownBy(() -> auditIdentity(
                        jdbc,
                        "HUMAN_LOGIN_SUCCEEDED",
                        "2.0",
                        "IDENTITY",
                        "AUTHENTICATION_ATTEMPT",
                        "SUCCESS",
                        null,
                        "{\"username\":\"forbidden\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> auditIdentity(
                        jdbc,
                        "HUMAN_LOGOUT_SUCCEEDED",
                        "1.0",
                        "IDENTITY",
                        "HUMAN_SESSION",
                        "SUCCESS",
                        null,
                        "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> auditIdentityWithActor(
                        jdbc,
                        "HUMAN_LOGIN_FAILED",
                        "2.0",
                        "IDENTITY",
                        null,
                        "AUTHENTICATION_ATTEMPT",
                        "DENIED",
                        null,
                        "{\"reason\":\"RATE_LIMITED\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> auditIdentityWithActor(
                        jdbc,
                        "AGENT_PUBLIC_KEY_REGISTERED",
                        "2.0",
                        "IDENTITY",
                        null,
                        "AGENT_TECHNICAL_IDENTITY",
                        "SUCCESS",
                        null,
                        "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> auditIdentity(
                        jdbc,
                        "DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED",
                        "2.0",
                        "IDENTITY",
                        "DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY",
                        "SUCCESS",
                        null,
                        "{\"fingerprint\":\"forbidden\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_reporting.audit_events", Integer.class)).isEqualTo(7);
    }

    @Test
    void upgradesInventoryAndCollectorWithoutChangingHistoricalEventsAndEnforcesNewCatalogs() {
        migrate("20261129010000");
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        UUID actorId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID assetEvent = UUID.randomUUID();
        UUID agentEvent = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO inventory.assets
                    (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, 'Historical asset', 'DESKTOP', 'ACTIVE', ?, ?, 0)
                """,
                assetId, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update(
                """
                INSERT INTO collector.agents
                    (id, asset_id, status, created_at, updated_at, version)
                VALUES (?, ?, 'PENDING', ?, ?, 0)
                """,
                agentId, assetId, Timestamp.from(NOW), Timestamp.from(NOW));
        insertProducerEvent(jdbc, "inventory", assetEvent, "ASSET_CREATED", assetId, 0, actorId);
        insertProducerEvent(jdbc, "collector", agentEvent, "AGENT_PRE_REGISTERED", agentId, 0, actorId);

        migrate(null);

        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_version, payload::text AS payload, status, attempts
                        FROM inventory.outbox_events WHERE event_id = ?
                        """,
                        assetEvent))
                .containsEntry("event_version", "1.0")
                .containsEntry("payload", "{}")
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 0);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_version, payload::text AS payload, status, attempts
                        FROM collector.outbox_events WHERE event_id = ?
                        """,
                        agentEvent))
                .containsEntry("event_version", "1.0")
                .containsEntry("payload", "{}")
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.outbox_deliveries", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM collector.outbox_deliveries", Integer.class)).isZero();

        insertAuditFact(jdbc, "ASSET_CREATED", "INVENTORY", "ASSET", 0);
        insertAuditFact(jdbc, "AGENT_PRE_REGISTERED", "COLLECTOR", "AGENT", 0);
        insertAuditFact(jdbc, "AGENT_ACTIVATED", "COLLECTOR", "AGENT", 1);
        assertThatThrownBy(() -> insertAuditFact(jdbc, "ASSET_CREATED", "INVENTORY", "ASSET", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAuditFact(jdbc, "AGENT_ACTIVATED", "COLLECTOR", "AGENT", 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertProducerDelivery(jdbc, "inventory", assetEvent, "FAILED", 10, null, "AUDIT_CONFLICT");
        insertProducerDelivery(jdbc, "collector", agentEvent, "DELIVERED", 1, Timestamp.from(NOW), null);
        assertThatThrownBy(() ->
                        insertProducerDelivery(jdbc, "inventory", assetEvent, "FAILED", 9, null, "AUDIT_CONFLICT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
                        insertProducerDelivery(jdbc, "collector", agentEvent, "PENDING", 10, null, "AUDIT_UNAVAILABLE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void upgradesDiscoveryWithoutChangingHistoricalEventsAndEnforcesDiscoveryCatalogs() {
        migrate("20261130020000");
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        UUID actorId = UUID.randomUUID();
        UUID historical = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'DISCOVERY_SCOPE_CREATED', '1.0', ?, 'DISCOVERY',
                        'HUMAN_USER', ?, 'AUTHORIZED_SCOPE', ?, 0,
                        'SUCCESS', ?, '{"status":"ACTIVE"}'::jsonb, ?)
                """,
                historical,
                Timestamp.from(NOW),
                actorId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Timestamp.from(NOW));
        UUID historicalCreated = insertHistoricalDiscoveryEvent(
                jdbc,
                "DISCOVERY_JOB_CREATED",
                "HUMAN_USER",
                "DISCOVERY_JOB",
                0,
                "SUCCESS",
                "{\"status\":\"PENDING\",\"profile\":\"BASIC\"}");
        UUID historicalDispatched = insertHistoricalDiscoveryEvent(
                jdbc,
                "DISCOVERY_JOB_DISPATCHED",
                "HUMAN_USER",
                "DISCOVERY_JOB",
                1,
                "SUCCESS",
                "{\"status\":\"DISPATCHED\"}");
        UUID historicalCompleted = insertHistoricalDiscoveryEvent(
                jdbc,
                "DISCOVERY_JOB_COMPLETED",
                "DISCOVERY_EXECUTOR",
                "DISCOVERY_JOB",
                2,
                "SUCCESS",
                "{\"status\":\"SUCCEEDED\",\"observationsFound\":1}");
        UUID historicalIgnored = insertHistoricalDiscoveryEvent(
                jdbc,
                "DISCOVERED_DEVICE_IGNORED",
                "HUMAN_USER",
                "DISCOVERED_DEVICE",
                1,
                "SUCCESS",
                "{\"previousStatus\":\"UNREVIEWED\",\"newStatus\":\"IGNORED\"}");
        UUID historicalIncorporated = insertHistoricalDiscoveryEvent(
                jdbc,
                "DISCOVERED_DEVICE_INCORPORATED",
                "HUMAN_USER",
                "DISCOVERED_DEVICE",
                1,
                "SUCCESS",
                "{\"previousStatus\":\"UNREVIEWED\",\"newStatus\":\"INCORPORATED\","
                        + "\"assetId\":\"" + UUID.randomUUID() + "\"}");
        String historicalDiscoverySql =
                """
                SELECT event_id, event_type, event_version, occurred_at, producer_module,
                       actor_type, actor_id, resource_type, resource_id, resource_version,
                       result, correlation_id, payload::text AS payload, status, attempts,
                       next_attempt_at
                FROM discovery.outbox_events
                WHERE event_id IN (?, ?, ?, ?, ?)
                ORDER BY event_type, resource_version
                """;
        var historicalDiscoveryBefore = jdbc.queryForList(
                historicalDiscoverySql,
                historicalCreated,
                historicalDispatched,
                historicalCompleted,
                historicalIgnored,
                historicalIncorporated);

        migrate(null);

        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_version, payload::text AS payload, status, attempts
                        FROM discovery.outbox_events WHERE event_id = ?
                        """,
                        historical))
                .containsEntry("event_version", "1.0")
                .containsEntry("payload", "{\"status\": \"ACTIVE\"}")
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery.outbox_deliveries", Integer.class))
                .isZero();
        assertThat(jdbc.queryForList(
                        historicalDiscoverySql,
                        historicalCreated,
                        historicalDispatched,
                        historicalCompleted,
                        historicalIgnored,
                        historicalIncorporated))
                .isEqualTo(historicalDiscoveryBefore);

        insertDiscoveryAuditFact(jdbc, "DISCOVERY_SCOPE_CREATED", "AUTHORIZED_SCOPE", 0, "{}");
        insertDiscoveryAuditFact(
                jdbc,
                "DISCOVERY_SCOPE_STATUS_CHANGED",
                "AUTHORIZED_SCOPE",
                3,
                "{\"previousStatus\":\"ACTIVE\",\"newStatus\":\"DISABLED\"}");
        insertDiscoveryAuditFact(jdbc, "DISCOVERY_EXECUTOR_PRE_REGISTERED", "DISCOVERY_EXECUTOR", 0, "{}");
        insertDiscoveryAuditFact(jdbc, "DISCOVERY_EXECUTOR_ACTIVATED", "DISCOVERY_EXECUTOR", 1, "{}");
        insertDiscoveryAuditFact(jdbc, "DISCOVERY_JOB_CREATED", "HUMAN_USER",
                "DISCOVERY_JOB", 0, "SUCCESS", "{}");
        insertDiscoveryAuditFact(jdbc, "DISCOVERY_JOB_DISPATCHED", "HUMAN_USER",
                "DISCOVERY_JOB", 1, "SUCCESS", "{}");
        insertDiscoveryAuditFact(jdbc, "DISCOVERY_JOB_COMPLETED", "DISCOVERY_EXECUTOR",
                "DISCOVERY_JOB", 2, "SUCCESS", "{}");
        insertDiscoveryAuditFact(jdbc, "DISCOVERY_JOB_COMPLETED", "DISCOVERY_EXECUTOR",
                "DISCOVERY_JOB", 2, "FAILED", "{}");
        UUID assetId = UUID.randomUUID();
        insertDiscoveryAuditFact(
                jdbc,
                "DISCOVERED_DEVICE_IGNORED",
                "DISCOVERED_DEVICE",
                1,
                "{\"previousStatus\":\"UNREVIEWED\",\"newStatus\":\"IGNORED\"}");
        insertDiscoveryAuditFact(
                jdbc,
                "DISCOVERED_DEVICE_INCORPORATED",
                "DISCOVERED_DEVICE",
                1,
                "{\"previousStatus\":\"UNREVIEWED\",\"newStatus\":\"INCORPORATED\","
                        + "\"assetId\":\"" + assetId + "\"}");
        UUID supportActor = UUID.randomUUID();
        UUID lifecycleId = UUID.randomUUID();
        insertSupportAuditFact(jdbc, supportActor, "TICKET_CREATED", 0,
                "{\"type\":\"INCIDENT\",\"status\":\"OPEN\",\"priority\":\"NORMAL\",\"source\":\"WEB\"}");
        insertSupportAuditFact(jdbc, supportActor, "TICKET_COMMENT_ADDED", 0,
                "{\"commentId\":\"" + UUID.randomUUID() + "\",\"visibility\":\"INTERNAL\"}");
        insertSupportAuditFact(jdbc, supportActor, "TICKET_ASSIGNED", 1,
                "{\"assigneeUserId\":\"" + supportActor + "\"}");
        insertSupportAuditFact(jdbc, supportActor, "TICKET_PRIORITY_CHANGED", 2,
                "{\"previousPriority\":\"NORMAL\",\"newPriority\":\"URGENT\"}");
        insertSupportAuditFact(jdbc, supportActor, "TICKET_STATUS_CHANGED", 3,
                "{\"previousStatus\":\"OPEN\",\"newStatus\":\"IN_PROGRESS\"}");
        insertSupportAuditFact(jdbc, supportActor, "TICKET_RESOLVED", 4,
                "{\"lifecycleEventId\":\"" + lifecycleId
                        + "\",\"fromStatus\":\"IN_PROGRESS\",\"toStatus\":\"RESOLVED\"}");
        insertSupportAuditFact(jdbc, supportActor, "TICKET_CLOSED", 5,
                "{\"lifecycleEventId\":\"" + lifecycleId
                        + "\",\"fromStatus\":\"RESOLVED\",\"toStatus\":\"CLOSED\","
                        + "\"closeReason\":\"RESOLVED\"}");
        insertSupportAuditFact(jdbc, supportActor, "TICKET_REOPENED", 6,
                "{\"lifecycleEventId\":\"" + lifecycleId
                        + "\",\"fromStatus\":\"CLOSED\",\"toStatus\":\"OPEN\"}");
        assertThatThrownBy(() -> insertSupportAuditFact(
                        jdbc,
                        supportActor,
                        "TICKET_STATUS_CHANGED",
                        7,
                        "{\"previousStatus\":\"OPEN\",\"newStatus\":\"WAITING_FOR_REQUESTER\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (Object[] invalid : new Object[][] {
            {"DISCOVERY_SCOPE_CREATED", "AUTHORIZED_SCOPE", 1L, "{}"},
            {"DISCOVERY_SCOPE_STATUS_CHANGED", "AUTHORIZED_SCOPE", 0L,
                    "{\"previousStatus\":\"ACTIVE\",\"newStatus\":\"DISABLED\"}"},
            {"DISCOVERY_SCOPE_STATUS_CHANGED", "AUTHORIZED_SCOPE", 1L,
                    "{\"previousStatus\":\"ACTIVE\",\"newStatus\":\"ACTIVE\"}"},
            {"DISCOVERY_SCOPE_STATUS_CHANGED", "AUTHORIZED_SCOPE", 1L,
                    "{\"previousStatus\":\"ACTIVE\",\"newStatus\":\"DISABLED\",\"name\":\"forbidden\"}"},
            {"DISCOVERY_EXECUTOR_PRE_REGISTERED", "DISCOVERY_EXECUTOR", 1L, "{}"},
            {"DISCOVERY_EXECUTOR_ACTIVATED", "DISCOVERY_EXECUTOR", 0L, "{}"}
        }) {
            assertThatThrownBy(() -> insertDiscoveryAuditFact(
                            jdbc,
                            (String) invalid[0],
                            (String) invalid[1],
                            (Long) invalid[2],
                            (String) invalid[3]))
                    .as(java.util.Arrays.toString(invalid))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        UUID future = insertDiscoveryProducerEvent(
                jdbc, "DISCOVERY_EXECUTOR_ACTIVATED", "DISCOVERY_EXECUTOR", 1, "{}");
        insertProducerDelivery(jdbc, "discovery", future, "FAILED", 10, null, "AUDIT_REJECTED");
        assertThatThrownBy(() ->
                        insertProducerDelivery(jdbc, "discovery", future, "PENDING", 10, null, "AUDIT_UNAVAILABLE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDiscoveryProducerEvent(
                        jdbc, "DISCOVERY_EXECUTOR_ACTIVATED", "DISCOVERY_EXECUTOR", 0, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertDiscoveryProducerEvent(jdbc, "DISCOVERY_JOB_CREATED", "DISCOVERY_JOB", 0, "{}");
        insertDiscoveryProducerEvent(jdbc, "DISCOVERY_JOB_DISPATCHED", "DISCOVERY_JOB", 1, "{}");
        insertDiscoveryProducerEvent(
                jdbc,
                "DISCOVERY_JOB_COMPLETED",
                "DISCOVERY_EXECUTOR",
                "DISCOVERY_JOB",
                2,
                "FAILED",
                "{}");
        insertDiscoveryProducerEvent(
                jdbc,
                "DISCOVERED_DEVICE_IGNORED",
                "DISCOVERED_DEVICE",
                1,
                "{\"previousStatus\":\"UNREVIEWED\",\"newStatus\":\"IGNORED\"}");
        insertDiscoveryProducerEvent(
                jdbc,
                "DISCOVERED_DEVICE_INCORPORATED",
                "DISCOVERED_DEVICE",
                1,
                "{\"previousStatus\":\"UNREVIEWED\",\"newStatus\":\"INCORPORATED\","
                        + "\"assetId\":\"" + assetId + "\"}");
        assertThatThrownBy(() -> insertDiscoveryProducerEvent(
                        jdbc, "DISCOVERY_JOB_CREATED", "DISCOVERY_JOB", 0,
                        "{\"status\":\"PENDING\",\"profile\":\"BASIC\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDiscoveryAuditFact(
                        jdbc,
                        "DISCOVERY_JOB_COMPLETED",
                        "HUMAN_USER",
                        "DISCOVERY_JOB",
                        2,
                        "SUCCESS",
                        "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDiscoveryProducerEvent(
                        jdbc,
                        "DISCOVERED_DEVICE_INCORPORATED",
                        "DISCOVERED_DEVICE",
                        1,
                        "{\"previousStatus\":\"UNREVIEWED\",\"newStatus\":\"INCORPORATED\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDiscoveryAuditFact(
                        jdbc,
                        "DISCOVERED_DEVICE_INCORPORATED",
                        "DISCOVERED_DEVICE",
                        1,
                        "{\"previousStatus\":\"UNREVIEWED\",\"newStatus\":\"INCORPORATED\","
                                + "\"assetId\":\"" + assetId.toString().toUpperCase(java.util.Locale.ROOT) + "\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void migrate(String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private static void delivery(
            JdbcTemplate jdbc, UUID eventId, String status, int attempts, Timestamp deliveredAt, String error) {
        jdbc.update(
                """
                INSERT INTO ai.outbox_deliveries
                    (event_id, consumer, status, attempts, next_attempt_at, delivered_at, last_error_code, updated_at)
                VALUES (?, 'AUDIT', ?, ?, now(), ?, ?, now())
                """,
                eventId, status, attempts, deliveredAt, error);
    }

    private static void audit(
            JdbcTemplate jdbc, String type, String module, String resourceType, String result, Long version,
            String metadata) {
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module, occurred_at,
                     actor_type, actor_id, resource_type, resource_id, resource_version, result,
                     correlation_id, metadata)
                VALUES (?, ?, ?, '1.0', ?, now(), 'HUMAN_USER', ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                UUID.randomUUID(), UUID.randomUUID(), type, module, UUID.randomUUID(), resourceType,
                UUID.randomUUID(), version, result, UUID.randomUUID(), metadata);
    }

    private static void auditIdentity(
            JdbcTemplate jdbc,
            String eventType,
            String eventVersion,
            String module,
            String resourceType,
            String result,
            Long resourceVersion, String metadata) {
        auditIdentityWithActor(
                jdbc,
                eventType,
                eventVersion,
                module,
                UUID.randomUUID(),
                resourceType,
                result,
                resourceVersion,
                metadata);
    }

    private static void auditIdentityWithActor(
            JdbcTemplate jdbc,
            String eventType,
            String eventVersion,
            String module,
            UUID actorId,
            String resourceType,
            String result,
            Long resourceVersion,
            String metadata) {
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module, occurred_at,
                     actor_type, actor_id, resource_type, resource_id, resource_version, result,
                     correlation_id, metadata)
                VALUES (?, ?, ?, ?, ?, now(), 'HUMAN_USER', ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                eventVersion,
                module,
                actorId,
                resourceType,
                UUID.randomUUID(),
                resourceVersion,
                result,
                UUID.randomUUID(),
                metadata);
    }

    private static void insertProducerEvent(
            JdbcTemplate jdbc,
            String schema,
            UUID eventId,
            String eventType,
            UUID resourceId,
            long resourceVersion,
            UUID actorId) {
        jdbc.update(
                """
                INSERT INTO %s.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '1.0', ?, ?, 'HUMAN_USER', ?, ?, ?, ?,
                        'SUCCESS', ?, '{}'::jsonb, ?)
                """.formatted(schema),
                eventId,
                eventType,
                Timestamp.from(NOW),
                schema.toUpperCase(java.util.Locale.ROOT),
                actorId,
                "inventory".equals(schema) ? "ASSET" : "AGENT",
                resourceId,
                resourceVersion,
                UUID.randomUUID(),
                Timestamp.from(NOW));
    }

    private static void insertProducerDelivery(
            JdbcTemplate jdbc,
            String schema,
            UUID eventId,
            String status,
            int attempts,
            Timestamp deliveredAt,
            String error) {
        jdbc.update(
                """
                INSERT INTO %s.outbox_deliveries
                    (event_id, consumer, status, attempts, next_attempt_at, delivered_at,
                     last_error_code, updated_at)
                VALUES (?, 'AUDIT', ?, ?, ?, ?, ?, ?)
                """.formatted(schema),
                eventId,
                status,
                attempts,
                Timestamp.from(NOW),
                deliveredAt,
                error,
                Timestamp.from(NOW));
    }

    private static void insertAuditFact(
            JdbcTemplate jdbc, String eventType, String producer, String resourceType, long resourceVersion) {
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, ?, '2.0', ?, ?, 'HUMAN_USER', ?, ?, ?, ?,
                        'SUCCESS', ?, '{}'::jsonb)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                producer,
                Timestamp.from(NOW),
                UUID.randomUUID(),
                resourceType,
                UUID.randomUUID(),
                resourceVersion,
                UUID.randomUUID());
    }

    private static UUID insertDiscoveryProducerEvent(
            JdbcTemplate jdbc, String eventType, String resourceType, long resourceVersion, String payload) {
        return insertDiscoveryProducerEvent(
                jdbc,
                eventType,
                "HUMAN_USER",
                resourceType,
                resourceVersion,
                "SUCCESS",
                payload);
    }

    private static UUID insertDiscoveryProducerEvent(
            JdbcTemplate jdbc,
            String eventType,
            String actorType,
            String resourceType,
            long resourceVersion,
            String result,
            String payload) {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '2.0', ?, 'DISCOVERY', ?, ?, ?, ?, ?,
                        ?, ?, ?::jsonb, ?)
                """,
                eventId,
                eventType,
                Timestamp.from(NOW),
                actorType,
                UUID.randomUUID(),
                resourceType,
                UUID.randomUUID(),
                resourceVersion,
                result,
                UUID.randomUUID(),
                payload,
                Timestamp.from(NOW));
        return eventId;
    }

    private static void insertDiscoveryAuditFact(
            JdbcTemplate jdbc, String eventType, String resourceType, long resourceVersion, String metadata) {
        insertDiscoveryAuditFact(
                jdbc,
                eventType,
                "HUMAN_USER",
                resourceType,
                resourceVersion,
                "SUCCESS",
                metadata);
    }

    private static void insertDiscoveryAuditFact(
            JdbcTemplate jdbc,
            String eventType,
            String actorType,
            String resourceType,
            long resourceVersion,
            String result,
            String metadata) {
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, ?, '2.0', 'DISCOVERY', ?, ?, ?, ?, ?, ?,
                        ?, ?, ?::jsonb)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                Timestamp.from(NOW),
                actorType,
                UUID.randomUUID(),
                resourceType,
                UUID.randomUUID(),
                resourceVersion,
                result,
                UUID.randomUUID(),
                metadata);
    }

    private static UUID insertHistoricalDiscoveryEvent(
            JdbcTemplate jdbc,
            String eventType,
            String actorType,
            String resourceType,
            long resourceVersion,
            String result,
            String payload) {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '1.0', ?, 'DISCOVERY', ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                eventId,
                eventType,
                Timestamp.from(NOW),
                actorType,
                UUID.randomUUID(),
                resourceType,
                UUID.randomUUID(),
                resourceVersion,
                result,
                UUID.randomUUID(),
                payload,
                Timestamp.from(NOW));
        return eventId;
    }

    private static void insertSupportAuditFact(
            JdbcTemplate jdbc,
            UUID actorId,
            String eventType,
            long resourceVersion,
            String metadata) {
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, ?, '2.0', 'SUPPORT', ?, 'HUMAN_USER', ?, 'TICKET', ?, ?,
                        'SUCCESS', ?, ?::jsonb)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                Timestamp.from(NOW),
                actorId,
                UUID.randomUUID(),
                resourceVersion,
                UUID.randomUUID(),
                metadata);
    }
}
