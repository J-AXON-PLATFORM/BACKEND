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
    void upgradesHistoricalSupportRowsAndEnforcesConversationConstraints() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("20261022000000")
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
        insertEvent(
                jdbc,
                ticketId,
                actorId,
                "TICKET_ASSIGNED",
                "{\"assigneeUserId\":\"" + actorId + "\"}",
                now);
        UUID internalCommentId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO support.ticket_comments
                    (id, ticket_id, visibility, author_user_id, body, created_at)
                VALUES (?, ?, 'INTERNAL', ?, 'Preserved internal note', ?)
                """,
                internalCommentId,
                ticketId,
                actorId,
                Timestamp.from(now));
        insertEvent(
                jdbc,
                ticketId,
                actorId,
                "TICKET_COMMENT_ADDED",
                "{\"commentId\":\"" + internalCommentId + "\",\"visibility\":\"INTERNAL\"}",
                now);

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM support.outbox_events WHERE resource_id = ?", Integer.class, ticketId))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM support.ticket_comments WHERE id = ? AND visibility = 'INTERNAL'",
                        Integer.class,
                        internalCommentId))
                .isOne();
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
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO support.ticket_comments
                            (id, ticket_id, visibility, author_user_id, body, created_at)
                        VALUES (?, ?, 'SECRET', ?, 'Invalid note', ?)
                        """,
                        UUID.randomUUID(),
                        ticketId,
                        actorId,
                        Timestamp.from(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEvent(
                        jdbc,
                        ticketId,
                        actorId,
                        "TICKET_COMMENT_ADDED",
                        "{\"commentId\":\"" + UUID.randomUUID()
                                + "\",\"visibility\":\"INTERNAL\",\"body\":\"forbidden\"}",
                        now))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList(
                        """
                        SELECT conname FROM pg_constraint
                        WHERE conrelid IN ('support.ticket_comments'::regclass, 'support.outbox_events'::regclass)
                          AND conname IN ('ticket_comments_visibility_check', 'outbox_events_payload_check')
                          AND convalidated
                        """,
                        String.class))
                .containsExactlyInAnyOrder("ticket_comments_visibility_check", "outbox_events_payload_check");
        insertEvent(
                jdbc,
                ticketId,
                actorId,
                "TICKET_PRIORITY_CHANGED",
                "{\"previousPriority\":\"NORMAL\",\"newPriority\":\"HIGH\"}",
                now);
        insertEvent(
                jdbc,
                ticketId,
                actorId,
                "TICKET_STATUS_CHANGED",
                "{\"previousStatus\":\"OPEN\",\"newStatus\":\"IN_PROGRESS\"}",
                now);
        assertThatThrownBy(() -> insertEvent(
                        jdbc,
                        ticketId,
                        actorId,
                        "TICKET_PRIORITY_CHANGED",
                        "{\"previousPriority\":\"NORMAL\",\"newPriority\":\"HIGH\",\"extra\":true}",
                        now))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID lifecycleId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO support.ticket_lifecycle_events
                    (id, ticket_id, event_type, from_status, to_status, actor_user_id,
                     resolution_summary, occurred_at, resource_version)
                VALUES (?, ?, 'RESOLVED', 'IN_PROGRESS', 'RESOLVED', ?, 'Restored service', ?, 1)
                """,
                lifecycleId,
                ticketId,
                actorId,
                Timestamp.from(now));
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE support.ticket_lifecycle_events SET resolution_summary = 'Changed' WHERE id = ?",
                        lifecycleId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "DELETE FROM support.ticket_lifecycle_events WHERE id = ?", lifecycleId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO support.ticket_lifecycle_events
                            (id, ticket_id, event_type, from_status, to_status, actor_user_id,
                             close_reason, occurred_at, resource_version)
                        VALUES (?, ?, 'CLOSED', 'OPEN', 'CLOSED', ?, 'RESOLVED', ?, 2)
                        """,
                        UUID.randomUUID(),
                        ticketId,
                        actorId,
                        Timestamp.from(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM support.ticket_lifecycle_events WHERE id = ?",
                        Integer.class,
                        lifecycleId))
                .isOne();
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
