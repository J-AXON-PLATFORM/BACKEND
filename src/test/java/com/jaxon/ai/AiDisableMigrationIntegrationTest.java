package com.jaxon.ai;

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
class AiDisableMigrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void upgradesHistoricalEventsAndAcceptsOnlyTheClosedDisableEvent() {
        migrate("20261104000000");
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        UUID sourceId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO ai.document_sources
                    (id, name, media_type, size_bytes, sha256, storage_key, status,
                     created_by_user_id, created_at, updated_at, version)
                VALUES (?, 'Historical source', 'text/plain', 7, ?, ?, 'READY', ?, ?, ?, 2)
                """,
                sourceId,
                "0".repeat(64),
                UUID.randomUUID() + ".txt",
                UUID.randomUUID(),
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        insertEvent(jdbc, sourceId, "AI_SOURCE_REGISTERED", "{\"status\":\"PENDING\"}");
        insertEvent(jdbc, sourceId, "AI_SOURCE_INDEXING_REQUESTED", "{\"status\":\"INDEXING\"}");
        insertEvent(jdbc, sourceId, "AI_SOURCE_INDEXED", "{\"status\":\"READY\"}");
        assertThatThrownBy(() -> insertEvent(
                        jdbc, sourceId, "AI_SOURCE_DISABLED",
                        "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}"))
                .isInstanceOf(DataIntegrityViolationException.class);

        migrate(null);

        assertThat(count(jdbc, sourceId)).isEqualTo(3);
        insertEvent(jdbc, sourceId, "AI_SOURCE_DISABLED", "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}");
        insertEvent(jdbc, sourceId, "AI_SOURCE_DISABLED", "{\"previousStatus\":\"FAILED\",\"newStatus\":\"DISABLED\"}");
        insertEvent(jdbc, sourceId, "AI_SOURCE_INDEXING_FAILED", "{\"status\":\"FAILED\"}");
        String[][] invalid = {
            {"AI_SOURCE_DISABLED", "{\"status\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "{\"previousStatus\":\"PENDING\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "{\"previousStatus\":\"INDEXING\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "{\"previousStatus\":\"DISABLED\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_DISABLED", "{\"previousStatus\":\"READY\",\"newStatus\":\"READY\"}"},
            {"AI_SOURCE_DISABLED", "{\"previousStatus\":\"READY\"}"},
            {"AI_SOURCE_DISABLED", "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\",\"name\":\"x\"}"},
            {"AI_SOURCE_INDEXED", "{\"previousStatus\":\"READY\",\"newStatus\":\"DISABLED\"}"},
            {"AI_SOURCE_REGISTERED", "{\"status\":\"DISABLED\"}"},
            {"AI_SOURCE_REENABLED", "{\"status\":\"READY\"}"}
        };
        for (String[] candidate : invalid) {
            assertThatThrownBy(() -> insertEvent(jdbc, sourceId, candidate[0], candidate[1]))
                    .as(candidate[0] + " " + candidate[1])
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(count(jdbc, sourceId)).isEqualTo(6);
    }

    private void migrate(String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private static int count(JdbcTemplate jdbc, UUID sourceId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM ai.outbox_events WHERE resource_id = ?", Integer.class, sourceId);
    }

    private static void insertEvent(JdbcTemplate jdbc, UUID sourceId, String type, String payload) {
        jdbc.update(
                """
                INSERT INTO ai.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '1.0', ?, 'AI', 'HUMAN_USER', ?, 'AI_SOURCE', ?, 2,
                        'SUCCESS', ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                type,
                Timestamp.from(NOW),
                UUID.randomUUID(),
                sourceId,
                UUID.randomUUID(),
                payload,
                Timestamp.from(NOW));
    }
}
