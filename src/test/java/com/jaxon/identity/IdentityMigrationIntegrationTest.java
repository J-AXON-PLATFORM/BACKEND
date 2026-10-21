package com.jaxon.identity;

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
class IdentityMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void upgradesHistoricalIdentityOutboxRowsAndEnforcesTheExtendedClosedCatalog() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("20261020000000")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        jdbc.update(
                """
                INSERT INTO identity.human_users
                    (id, username, normalized_username, password_hash, status, created_at)
                VALUES (?, 'Historical.Admin', 'historical.admin', 'not-a-runtime-credential', 'ACTIVE', ?)
                """,
                actorId,
                Timestamp.from(now));
        insertHistoricalEvent(jdbc, "HUMAN_LOGIN_SUCCEEDED", actorId, "AUTHENTICATION_ATTEMPT", "SUCCESS", "{}", now);
        insertHistoricalEvent(jdbc, "HUMAN_LOGIN_FAILED", null, "AUTHENTICATION_ATTEMPT", "DENIED", "{\"reason\":\"INVALID_CREDENTIALS\"}", now);
        insertHistoricalEvent(jdbc, "AGENT_PUBLIC_KEY_REGISTERED", actorId, "AGENT_TECHNICAL_IDENTITY", "SUCCESS", "{}", now);

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.outbox_events", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList(
                        """
                        SELECT conname FROM pg_constraint
                        WHERE conrelid = 'identity.outbox_events'::regclass AND convalidated
                        """,
                        String.class))
                .contains(
                        "outbox_events_event_type_check",
                        "outbox_events_resource_type_check",
                        "identity_outbox_event_rule");
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO identity.outbox_events
                            (event_id, event_type, event_version, occurred_at, producer_module,
                             actor_type, actor_id, resource_type, resource_id, result,
                             correlation_id, payload, next_attempt_at)
                        VALUES (?, 'HUMAN_LOGOUT_SUCCEEDED', '1.0', ?, 'IDENTITY',
                                'HUMAN_USER', ?, 'HUMAN_USER', ?, 'SUCCESS', ?, '{}'::jsonb, ?)
                        """,
                        UUID.randomUUID(),
                        Timestamp.from(now),
                        actorId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Timestamp.from(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertHistoricalEvent(
            JdbcTemplate jdbc,
            String eventType,
            UUID actorId,
            String resourceType,
            String result,
            String payload,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO identity.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, result,
                     correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '1.0', ?, 'IDENTITY', 'HUMAN_USER', ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """,
                UUID.randomUUID(),
                eventType,
                Timestamp.from(now),
                actorId,
                resourceType,
                UUID.randomUUID(),
                result,
                UUID.randomUUID(),
                payload,
                Timestamp.from(now));
    }
}
