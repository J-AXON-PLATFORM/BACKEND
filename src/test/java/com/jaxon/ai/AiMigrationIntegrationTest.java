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
class AiMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void upgradesPendingSourcesAndEnforcesTheIndexingSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("20261031000000")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        UUID sourceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        jdbc.update(
                """
                INSERT INTO ai.document_sources
                    (id, name, media_type, size_bytes, sha256, storage_key, status,
                     created_by_user_id, created_at, updated_at, version)
                VALUES (?, 'Historical source', 'text/plain', 7, ?, ?, 'PENDING', ?, ?, ?, 0)
                """,
                sourceId,
                "0".repeat(64),
                UUID.randomUUID() + ".txt",
                actorId,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO ai.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'AI_SOURCE_REGISTERED', '1.0', ?, 'AI', 'HUMAN_USER', ?,
                        'AI_SOURCE', ?, 0, 'SUCCESS', ?, '{"status":"PENDING"}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                sourceId,
                UUID.randomUUID(),
                Timestamp.from(now));

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForMap(
                        "SELECT status, version FROM ai.document_sources WHERE id = ?", sourceId))
                .containsEntry("status", "PENDING")
                .containsEntry("version", 0L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.outbox_events", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM pg_indexes
                        WHERE schemaname = 'ai' AND indexname = 'ai_source_chunks_embedding_hnsw_idx'
                        """,
                        Integer.class))
                .isEqualTo(1);
        jdbc.update(
                "UPDATE ai.document_sources SET status = 'INDEXING', version = 1, updated_at = ? WHERE id = ?",
                Timestamp.from(now.plusSeconds(1)),
                sourceId);
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE ai.document_sources SET status = 'UNKNOWN' WHERE id = ?", sourceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
