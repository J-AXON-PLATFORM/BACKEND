package com.jaxon.support;

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
class SupportMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void upgradesHistoricalSupportRowsAndEnforcesTheAssignmentPayload() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("20261020010000")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        UUID ticketId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-21T10:00:00Z");
        jdbc.update(
                """
                INSERT INTO support.tickets
                    (id, type, summary, description, requester_user_id, created_by_user_id,
                     created_at, updated_at)
                VALUES (?, 'INCIDENT', 'Historical ticket', 'Preserved description', ?, ?, ?, ?)
                """,
                ticketId,
                actorId,
                actorId,
                Timestamp.from(now),
                Timestamp.from(now));
        insertEvent(
                jdbc,
                ticketId,
                actorId,
                "TICKET_CREATED",
                "{\"type\":\"INCIDENT\",\"status\":\"OPEN\",\"priority\":\"NORMAL\",\"source\":\"WEB\"}",
                now);
        insertEvent(
                jdbc,
                ticketId,
                actorId,
                "TICKET_COMMENT_ADDED",
                "{\"commentId\":\"" + commentId + "\",\"visibility\":\"PUBLIC\"}",
                now);

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM support.outbox_events WHERE resource_id = ?", Integer.class, ticketId))
                .isEqualTo(2);
        assertThat(jdbc.queryForList(
                        """
                        SELECT conname FROM pg_constraint
                        WHERE conrelid = 'support.outbox_events'::regclass AND convalidated
                        """,
                        String.class))
                .contains("outbox_events_event_type_check", "outbox_events_payload_check");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM pg_indexes
                            WHERE schemaname = 'support'
                              AND indexname = 'support_tickets_technician_queue_idx'
                        )
                        """,
                        Boolean.class))
                .isTrue();
        assertThatThrownBy(() -> insertEvent(
                        jdbc, ticketId, actorId, "TICKET_ASSIGNED", "{}", now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertEvent(
            JdbcTemplate jdbc,
            UUID ticketId,
            UUID actorId,
            String eventType,
            String payload,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO support.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '1.0', ?, 'SUPPORT', 'HUMAN_USER', ?, 'TICKET', ?, 0,
                        'SUCCESS', ?, CAST(? AS jsonb), ?)
                """,
                UUID.randomUUID(),
                eventType,
                Timestamp.from(now),
                actorId,
                ticketId,
                UUID.randomUUID(),
                payload,
                Timestamp.from(now));
    }
}
