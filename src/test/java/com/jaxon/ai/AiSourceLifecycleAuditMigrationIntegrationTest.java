package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
class AiSourceLifecycleAuditMigrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-30T11:00:00Z");

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
        jdbc().execute(
                """
                DROP SCHEMA IF EXISTS inventory, discovery, collector, support, ai, identity,
                    automation, audit_reporting CASCADE
                """);
    }

    @Test
    void preservesAllHistoricalAiEventsAndEnforcesBothClosedCatalogs() {
        migrate("20261130100000");
        JdbcTemplate jdbc = jdbc();
        UUID sourceId = insertSource(jdbc);
        UUID actorId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        insertProducer(jdbc, sourceId, "AI_SOURCE_REGISTERED", "1.0", "HUMAN_USER", actorId,
                0, "SUCCESS", "{\"status\":\"PENDING\"}", correlationId);
        insertProducer(jdbc, sourceId, "AI_SOURCE_INDEXING_REQUESTED", "1.0", "HUMAN_USER", actorId,
                1, "SUCCESS", "{\"status\":\"INDEXING\"}", correlationId);
        insertProducer(jdbc, sourceId, "AI_SOURCE_INDEXED", "1.0", "HUMAN_USER", actorId,
                2, "SUCCESS", "{\"status\":\"READY\"}", correlationId);
        insertProducer(jdbc, sourceId, "AI_SOURCE_INDEXING_FAILED", "1.0", "HUMAN_USER", actorId,
                2, "SUCCESS", "{\"status\":\"FAILED\"}", correlationId);
        insertProducer(jdbc, sourceId, "AI_SOURCE_DISABLED", "1.0", "HUMAN_USER", actorId,
                3, "SUCCESS", "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}", correlationId);
        String historicalSql =
                """
                SELECT event_id, event_type, event_version, occurred_at, producer_module,
                       actor_type, actor_id, resource_type, resource_id, resource_version,
                       result, correlation_id, payload::text AS payload, status, attempts,
                       next_attempt_at, delivered_at
                FROM ai.outbox_events
                WHERE resource_id = ?
                ORDER BY event_type
                """;
        List<java.util.Map<String, Object>> before = jdbc.queryForList(historicalSql, sourceId);

        migrate(null);

        assertThat(jdbc.queryForList(historicalSql, sourceId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.outbox_deliveries", Integer.class)).isZero();
        insertProducer(jdbc, sourceId, "AI_SOURCE_REGISTERED", "2.0", "HUMAN_USER", actorId,
                0, "SUCCESS", "{\"status\":\"PENDING\"}", correlationId);
        insertProducer(jdbc, sourceId, "AI_SOURCE_INDEXING_REQUESTED", "2.0", "HUMAN_USER", actorId,
                1, "SUCCESS", "{\"status\":\"INDEXING\"}", correlationId);
        insertProducer(jdbc, sourceId, "AI_SOURCE_INDEXED", "2.0", "SYSTEM", null,
                2, "SUCCESS", "{\"status\":\"READY\"}", correlationId);
        insertProducer(jdbc, sourceId, "AI_SOURCE_INDEXING_FAILED", "2.0", "SYSTEM", null,
                2, "FAILED", "{\"status\":\"FAILED\"}", correlationId);

        assertThatThrownBy(() -> insertProducer(
                        jdbc, sourceId, "AI_SOURCE_INDEXED", "2.0", "SYSTEM", actorId,
                        2, "SUCCESS", "{\"status\":\"READY\"}", correlationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertProducer(
                        jdbc, sourceId, "AI_SOURCE_INDEXING_FAILED", "2.0", "SYSTEM", null,
                        2, "SUCCESS", "{\"status\":\"FAILED\"}", correlationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertProducer(
                        jdbc, sourceId, "AI_SOURCE_REGISTERED", "2.0", "HUMAN_USER", actorId,
                        0, "SUCCESS", "{\"status\":\"PENDING\",\"name\":\"forbidden\"}", correlationId))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertAudit(jdbc, "AI_SOURCE_REGISTERED", "HUMAN_USER", actorId, 0, "SUCCESS", "{\"status\":\"PENDING\"}");
        insertAudit(jdbc, "AI_SOURCE_INDEXING_REQUESTED", "HUMAN_USER", actorId,
                1, "SUCCESS", "{\"status\":\"INDEXING\"}");
        insertAudit(jdbc, "AI_SOURCE_INDEXED", "SYSTEM", null, 2, "SUCCESS", "{\"status\":\"READY\"}");
        insertAudit(jdbc, "AI_SOURCE_INDEXING_FAILED", "SYSTEM", null,
                2, "FAILED", "{\"status\":\"FAILED\"}");
        assertThatThrownBy(() -> insertAudit(
                        jdbc, "AI_SOURCE_INDEXED", "SYSTEM", actorId, 2, "SUCCESS", "{\"status\":\"READY\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAudit(
                        jdbc, "AI_SOURCE_INDEXING_FAILED", "SYSTEM", null, 2, "FAILED",
                        "{\"status\":\"FAILED\",\"error\":\"forbidden\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void migrate(String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    private static UUID insertSource(JdbcTemplate jdbc) {
        UUID sourceId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO ai.document_sources
                    (id, name, media_type, size_bytes, sha256, storage_key, status,
                     created_by_user_id, created_at, updated_at, version)
                VALUES (?, 'Historical source', 'text/plain', 7, ?, ?, 'DISABLED', ?, ?, ?, 3)
                """,
                sourceId,
                "0".repeat(64),
                UUID.randomUUID() + ".txt",
                UUID.randomUUID(),
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        return sourceId;
    }

    private static void insertProducer(
            JdbcTemplate jdbc,
            UUID sourceId,
            String eventType,
            String eventVersion,
            String actorType,
            UUID actorId,
            long resourceVersion,
            String result,
            String payload,
            UUID correlationId) {
        jdbc.update(
                """
                INSERT INTO ai.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, ?, ?, 'AI', ?, ?, 'AI_SOURCE', ?, ?, ?, ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                eventType,
                eventVersion,
                Timestamp.from(NOW),
                actorType,
                actorId,
                sourceId,
                resourceVersion,
                result,
                correlationId,
                payload,
                Timestamp.from(NOW));
    }

    private static void insertAudit(
            JdbcTemplate jdbc,
            String eventType,
            String actorType,
            UUID actorId,
            long resourceVersion,
            String result,
            String metadata) {
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, ?, '2.0', 'AI', ?, ?, ?, 'AI_SOURCE', ?, ?, ?, ?, ?::jsonb)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                Timestamp.from(NOW),
                actorType,
                actorId,
                UUID.randomUUID(),
                resourceVersion,
                result,
                UUID.randomUUID(),
                metadata);
    }
}
