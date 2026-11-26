package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_reporting.audit_events", Integer.class)).isEqualTo(4);
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
                UUID.randomUUID(),
                resourceType,
                UUID.randomUUID(),
                resourceVersion,
                result,
                UUID.randomUUID(),
                metadata);
    }
}
