package com.jaxon.automation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaxon.AutomationTrigger;
import com.jaxon.AutomationTriggerOutcome;
import com.jaxon.AutomationTriggerReceiver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Automation.Owner",
            "jaxon.identity.bootstrap.password=Automation test passphrase 42!",
            "jaxon.support.automation-dispatch.enabled=false",
            "jaxon.automation.execution.enabled=false",
            "jaxon.automation.audit-dispatch.enabled=false"
        })
class AutomationNotificationIntegrationTest {

    private static final String USERNAME = "Automation.Owner";
    private static final String PASSWORD = "Automation test passphrase 42!";
    private static final Instant NOW = Instant.parse("2026-12-02T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationContext context;

    AutomationExecutionWorker worker;

    @Autowired
    AutomationAuditDispatcher auditDispatcher;

    @Autowired
    AutomationTriggerReceiver receiver;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearAutomationFixture() {
        if (worker == null) {
            worker = new AutomationExecutionWorker(
                    jdbc,
                    transactionManager,
                    new SimpleMeterRegistry(),
                    Clock.systemUTC(),
                    50,
                    bound -> 0);
        }
        jdbc.update("DELETE FROM automation.outbox_deliveries");
        jdbc.update("DELETE FROM automation.outbox_events");
        jdbc.update("DELETE FROM automation.notifications");
        jdbc.update("DELETE FROM automation.executions");
        jdbc.update("DELETE FROM support.outbox_deliveries WHERE consumer = 'AUTOMATION'");
        jdbc.update(
                "DELETE FROM support.outbox_events WHERE event_type = 'TICKET_STATUS_CHANGED' "
                        + "AND payload = '{\"previousStatus\":\"IN_PROGRESS\","
                        + "\"newStatus\":\"WAITING_FOR_REQUESTER\"}'::jsonb");
        jdbc.update("DELETE FROM support.tickets WHERE summary = 'Waiting test'");
    }

    @Test
    void handsOffExactlyOnceMaterializesOwnedNotificationAndRecordsAudit() throws Exception {
        Session owner = login();
        UUID ownerId = userId();
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        insertTicketAndTrigger(ticketId, eventId, ownerId, actorId, correlationId);

        Object supportDispatcher = context.getBean("supportAutomationDispatcher");
        Object firstDispatch = ReflectionTestUtils.invokeMethod(supportDispatcher, "dispatchDue", 10);
        assertThat(firstDispatch.toString()).contains("accepted=1");
        assertThat(worker.processDue(10)).isEqualTo(new AutomationExecutionWorker.ExecutionSummary(1, 0, 0));

        Map<String, Object> execution = jdbc.queryForMap(
                """
                SELECT status, attempts, source_event_id, recipient_user_id
                FROM automation.executions
                WHERE source_event_id = ?
                """,
                eventId);
        assertThat(execution)
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("attempts", 1)
                .containsEntry("source_event_id", eventId)
                .containsEntry("recipient_user_id", ownerId);
        UUID notificationId = jdbc.queryForObject(
                "SELECT notification_id FROM automation.notifications WHERE execution_id = "
                        + "(SELECT execution_id FROM automation.executions WHERE source_event_id = ?)",
                UUID.class,
                eventId);

        jdbc.update(
                "DELETE FROM support.outbox_deliveries WHERE event_id = ? AND consumer = 'AUTOMATION'",
                eventId);
        Object duplicateDispatch = ReflectionTestUtils.invokeMethod(supportDispatcher, "dispatchDue", 10);
        assertThat(duplicateDispatch.toString()).contains("duplicates=1");
        assertThat(worker.processDue(10)).isEqualTo(new AutomationExecutionWorker.ExecutionSummary(0, 0, 0));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM automation.executions WHERE source_event_id = ?",
                        Integer.class,
                        eventId))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM automation.notifications WHERE execution_id = "
                                + "(SELECT execution_id FROM automation.executions WHERE source_event_id = ?)",
                        Integer.class,
                        eventId))
                .isOne();

        assertThat(auditDispatcher.dispatchDue(10).delivered()).isOne();
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, event_version, producer_module, actor_type,
                               resource_type, resource_version, result, metadata::text AS metadata
                        FROM audit_reporting.audit_events
                        WHERE correlation_id = ?
                          AND event_type = 'AUTOMATION_EXECUTION_SUCCEEDED'
                        """,
                        correlationId))
                .containsEntry("event_type", "AUTOMATION_EXECUTION_SUCCEEDED")
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "AUTOMATION")
                .containsEntry("actor_type", "AUTOMATION")
                .containsEntry("resource_type", "AUTOMATION_EXECUTION")
                .containsEntry("resource_version", 1L)
                .containsEntry("result", "SUCCESS");

        UUID foreignNotification = insertForeignNotification(ticketId);
        HttpResponse<String> anonymous = get("/api/v1/notifications", null);
        assertThat(anonymous.statusCode()).isEqualTo(401);

        HttpResponse<String> listed = get("/api/v1/notifications?status=UNREAD&page=0&size=20", owner.cookie());
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body())
                .contains(notificationId.toString(), "\"type\":\"TICKET_WAITING_FOR_REQUESTER\"")
                .doesNotContain(foreignNotification.toString());

        HttpResponse<String> foreignRead = read(owner, foreignNotification, 0);
        assertThat(foreignRead.statusCode()).isEqualTo(404);
        assertThat(foreignRead.body()).contains("\"code\":\"NOTIFICATION_NOT_FOUND\"");

        HttpResponse<String> read = read(owner, notificationId, 0);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).contains("\"status\":\"READ\"", "\"version\":1");
        String firstRead = read.body();

        HttpResponse<String> repeated = read(owner, notificationId, 1);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(repeated.body()).isEqualTo(firstRead);
        assertThat(read(owner, notificationId, 0).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM automation.outbox_events WHERE resource_id = ?",
                        Integer.class,
                        notificationId))
                .isZero();
    }

    @Test
    void rejectsConflictingTriggerAndFailsExecutionAfterTenAtomicAttempts() {
        UUID sourceEventId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        AutomationTrigger trigger = trigger(
                sourceEventId, recipientId, ticketId, actorId, correlationId, NOW);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        AutomationTriggerOutcome accepted = transactions.execute(status -> receiver.accept(trigger));
        AutomationTriggerOutcome duplicate = transactions.execute(status -> receiver.accept(trigger));
        assertThat(accepted).isEqualTo(AutomationTriggerOutcome.ACCEPTED);
        assertThat(duplicate).isEqualTo(AutomationTriggerOutcome.DUPLICATE);

        jdbc.execute(
                """
                CREATE FUNCTION automation.reject_notification_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'simulated notification persistence failure';
                END;
                $$;
                CREATE TRIGGER reject_notification_insert
                BEFORE INSERT ON automation.notifications
                FOR EACH ROW EXECUTE FUNCTION automation.reject_notification_insert();
                """);
        try {
            for (int attempt = 1; attempt <= AutomationExecutionWorker.MAX_ATTEMPTS; attempt++) {
                jdbc.update(
                        "UPDATE automation.executions SET next_attempt_at = now() - interval '1 second' "
                                + "WHERE source_event_id = ?",
                        sourceEventId);
                AutomationExecutionWorker.ExecutionSummary summary = worker.processDue(1);
                if (attempt < AutomationExecutionWorker.MAX_ATTEMPTS) {
                    assertThat(summary).isEqualTo(new AutomationExecutionWorker.ExecutionSummary(0, 1, 0));
                } else {
                    assertThat(summary).isEqualTo(new AutomationExecutionWorker.ExecutionSummary(0, 0, 1));
                }
                assertThat(jdbc.queryForObject(
                                "SELECT count(*) FROM automation.notifications WHERE execution_id = "
                                        + "(SELECT execution_id FROM automation.executions WHERE source_event_id = ?)",
                                Integer.class,
                                sourceEventId))
                        .isZero();
            }
        } finally {
            jdbc.execute("DROP TRIGGER reject_notification_insert ON automation.notifications");
            jdbc.execute("DROP FUNCTION automation.reject_notification_insert()");
        }

        assertThat(jdbc.queryForMap(
                        """
                        SELECT status, attempts, last_error_code, completed_at IS NOT NULL AS completed
                        FROM automation.executions WHERE source_event_id = ?
                        """,
                        sourceEventId))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", 10)
                .containsEntry("last_error_code", "NOTIFICATION_PERSISTENCE_FAILED")
                .containsEntry("completed", true);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, result, payload::text AS payload
                        FROM automation.outbox_events
                        WHERE resource_id = (
                            SELECT execution_id FROM automation.executions WHERE source_event_id = ?)
                        """,
                        sourceEventId))
                .containsEntry("event_type", "AUTOMATION_EXECUTION_FAILED")
                .containsEntry("result", "FAILED");
    }

    private void insertTicketAndTrigger(
            UUID ticketId,
            UUID eventId,
            UUID requesterId,
            UUID actorId,
            UUID correlationId) {
        jdbc.update(
                """
                INSERT INTO support.tickets
                    (id, type, summary, description, status, priority, source,
                     requester_user_id, created_by_user_id, assignee_user_id,
                     created_at, updated_at, version)
                VALUES (?, 'INCIDENT', 'Waiting test', 'Waiting test description',
                        'WAITING_FOR_REQUESTER', 'NORMAL', 'WEB', ?, ?, ?,
                        ?, ?, 3)
                """,
                ticketId,
                requesterId,
                requesterId,
                actorId,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        jdbc.update(
                """
                INSERT INTO support.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'TICKET_STATUS_CHANGED', '2.0', ?, 'SUPPORT',
                        'HUMAN_USER', ?, 'TICKET', ?, 3, 'SUCCESS', ?,
                        '{"previousStatus":"IN_PROGRESS","newStatus":"WAITING_FOR_REQUESTER"}'::jsonb, ?)
                """,
                eventId,
                Timestamp.from(NOW),
                actorId,
                ticketId,
                correlationId,
                Timestamp.from(NOW));
    }

    private UUID insertForeignNotification(UUID ticketId) {
        UUID sourceEventId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> receiver.accept(trigger(
                sourceEventId,
                recipientId,
                ticketId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW.plusSeconds(1))));
        worker.processDue(1);
        return jdbc.queryForObject(
                "SELECT notification_id FROM automation.notifications WHERE execution_id = ?",
                UUID.class,
                jdbc.queryForObject(
                        "SELECT execution_id FROM automation.executions WHERE source_event_id = ?",
                        UUID.class,
                        sourceEventId));
    }

    private static AutomationTrigger trigger(
            UUID sourceEventId,
            UUID recipientId,
            UUID ticketId,
            UUID actorId,
            UUID correlationId,
            Instant occurredAt) {
        return new AutomationTrigger(
                sourceEventId,
                "TICKET_STATUS_CHANGED",
                "2.0",
                occurredAt,
                "SUPPORT",
                "TICKET",
                ticketId,
                3,
                "HUMAN_USER",
                actorId,
                correlationId,
                recipientId,
                "IN_PROGRESS",
                "WAITING_FOR_REQUESTER");
    }

    private Session login() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url("/api/v1/auth/login")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Matcher csrf = Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(csrf.find()).isTrue();
        return new Session(
                response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0],
                csrf.group(1));
    }

    private UUID userId() {
        return jdbc.queryForObject(
                "SELECT id FROM identity.human_users WHERE normalized_username = lower(?)",
                UUID.class,
                USERNAME);
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path))).GET();
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> read(Session session, UUID notificationId, long expectedVersion)
            throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url(
                                "/api/v1/notifications/" + notificationId + "/read")))
                        .header("Cookie", session.cookie())
                        .header("X-CSRF-Token", session.csrfToken())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"expectedVersion\":" + expectedVersion + "}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private record Session(String cookie, String csrfToken) {}
}
