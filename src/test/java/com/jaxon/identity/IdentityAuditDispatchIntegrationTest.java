package com.jaxon.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Identity.Audit.Admin",
            "jaxon.identity.bootstrap.password=Identity audit passphrase 42!"
        })
class IdentityAuditDispatchIntegrationTest {

    private static final String USERNAME = "Identity.Audit.Admin";
    private static final String PASSWORD = "Identity audit passphrase 42!";

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
    IdentityAuditDispatcher dispatcher;

    @BeforeEach
    void cleanIdentityState() {
        jdbc.update("DELETE FROM identity.outbox_events");
        jdbc.update("DELETE FROM identity.human_sessions");
        jdbc.update(
                """
                DELETE FROM identity.human_user_roles
                WHERE user_id IN (
                    SELECT id FROM identity.human_users WHERE normalized_username <> 'identity.audit.admin')
                """);
        jdbc.update("DELETE FROM identity.human_users WHERE normalized_username <> 'identity.audit.admin'");
    }

    @AfterEach
    void dropFaults() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_identity_audit_insert ON audit_reporting.audit_events");
        jdbc.execute("DROP FUNCTION IF EXISTS audit_reporting.reject_identity_audit_insert()");
    }

    @Test
    void createsVersionTwoAtomicallyAndMaterializesCompleteCanonicalRoles() throws Exception {
        Session session = login();
        UUID correlationId = UUID.randomUUID();
        HttpResponse<String> response = createUser(
                session,
                "audited-" + UUID.randomUUID(),
                List.of("SYSTEM_ADMIN", "AUDITOR", "END_USER"),
                correlationId);
        assertThat(response.statusCode()).isEqualTo(201);
        UUID userId = locationId(response);
        UUID eventId = userCreatedEventId(userId);
        insertHistoricalVersionOne(session.userId());

        assertThat(jdbc.queryForMap(
                        "SELECT event_version, payload::text AS payload FROM identity.outbox_events WHERE event_id = ?",
                        eventId))
                .containsEntry("event_version", "2.0")
                .containsEntry("payload", "{\"roles\": \"AUDITOR|END_USER|SYSTEM_ADMIN\", \"status\": \"ACTIVE\"}");

        assertThat(dispatcher.dispatchDue(50))
                .isEqualTo(new IdentityAuditDispatcher.DispatchSummary(1, 0, 0, 0));

        assertThat(jdbc.queryForMap(
                        "SELECT * FROM audit_reporting.audit_events WHERE source_event_id = ?", eventId))
                .containsEntry("event_type", "HUMAN_USER_CREATED")
                .containsEntry("event_version", "2.0")
                .containsEntry("producer_module", "IDENTITY")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("actor_id", session.userId())
                .containsEntry("resource_type", "HUMAN_USER")
                .containsEntry("resource_id", userId)
                .containsEntry("resource_version", null)
                .containsEntry("result", "SUCCESS")
                .containsEntry("correlation_id", correlationId);
        assertThat(jdbc.queryForObject(
                        "SELECT metadata::text FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        String.class,
                        eventId))
                .isEqualTo("{\"roles\": \"AUDITOR|END_USER|SYSTEM_ADMIN\", \"status\": \"ACTIVE\"}");
        assertThat(delivery(eventId))
                .containsEntry("status", "DELIVERED")
                .containsEntry("attempts", 1)
                .containsEntry("last_error_code", null);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM identity.outbox_deliveries d
                        JOIN identity.outbox_events e USING (event_id)
                        WHERE e.event_version = '1.0'
                        """,
                        Integer.class))
                .isZero();
        assertThat(dispatcher.dispatchDue(50))
                .isEqualTo(new IdentityAuditDispatcher.DispatchSummary(0, 0, 0, 0));
    }

    @Test
    void redeliveryIsIdempotentAndConflictFailsAfterTenAttempts() {
        UUID actorId = bootstrapId();
        UUID duplicate = insertVersionTwo(actorId, UUID.randomUUID(), "AUDITOR|SUPPORT_TECHNICIAN");
        assertThat(dispatcher.dispatchDue(1))
                .isEqualTo(new IdentityAuditDispatcher.DispatchSummary(1, 0, 0, 0));
        jdbc.update("DELETE FROM identity.outbox_deliveries WHERE event_id = ?", duplicate);
        assertThat(dispatcher.dispatchDue(1))
                .isEqualTo(new IdentityAuditDispatcher.DispatchSummary(0, 1, 0, 0));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        Integer.class,
                        duplicate))
                .isEqualTo(1);

        UUID resourceId = UUID.randomUUID();
        UUID conflicting = insertVersionTwo(actorId, resourceId, "END_USER");
        insertConflictingAudit(conflicting, actorId, resourceId);
        for (int attempt = 1; attempt <= 10; attempt++) {
            makeDue(conflicting);
            IdentityAuditDispatcher.DispatchSummary summary = dispatcher.dispatchDue(1);
            assertThat(summary.delivered() + summary.duplicates()).isZero();
            assertThat(delivery(conflicting))
                    .containsEntry("attempts", attempt)
                    .containsEntry("last_error_code", "AUDIT_CONFLICT")
                    .containsEntry("status", attempt == 10 ? "FAILED" : "PENDING");
        }
        makeDue(conflicting);
        assertThat(dispatcher.dispatchDue(1))
                .isEqualTo(new IdentityAuditDispatcher.DispatchSummary(0, 0, 0, 0));
    }

    @Test
    void auditFailureDoesNotAffectUserCreationAndADefectiveEventDoesNotBlockTheNext() throws Exception {
        Session session = login();
        jdbc.execute(
                """
                CREATE FUNCTION audit_reporting.reject_identity_audit_insert() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'audit unavailable'; END $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_identity_audit_insert BEFORE INSERT ON audit_reporting.audit_events
                FOR EACH ROW EXECUTE FUNCTION audit_reporting.reject_identity_audit_insert()
                """);
        HttpResponse<String> created = createUser(
                session, "available-" + UUID.randomUUID(), List.of("IT_OPERATOR"), UUID.randomUUID());
        assertThat(created.statusCode()).isEqualTo(201);
        UUID userId = locationId(created);
        UUID eventId = userCreatedEventId(userId);
        assertThat(dispatcher.dispatchDue(1))
                .isEqualTo(new IdentityAuditDispatcher.DispatchSummary(0, 0, 1, 0));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.human_users WHERE id = ?", Integer.class, userId))
                .isEqualTo(1);
        assertThat(delivery(eventId))
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 1)
                .containsEntry("last_error_code", "AUDIT_UNAVAILABLE");

        dropFaults();
        makeDue(eventId);
        assertThat(dispatcher.dispatchDue(1))
                .isEqualTo(new IdentityAuditDispatcher.DispatchSummary(1, 0, 0, 0));

        UUID rejectedResource = UUID.randomUUID();
        UUID rejected = insertVersionTwo(session.userId(), rejectedResource, "AUDITOR");
        insertConflictingAudit(rejected, session.userId(), rejectedResource);
        UUID valid = insertVersionTwo(session.userId(), UUID.randomUUID(), "SYSTEM_ADMIN");
        IdentityAuditDispatcher.DispatchSummary summary = dispatcher.dispatchDue(50);
        assertThat(summary).isEqualTo(new IdentityAuditDispatcher.DispatchSummary(1, 0, 1, 0));
        assertThat(delivery(rejected)).containsEntry("status", "PENDING");
        assertThat(delivery(valid)).containsEntry("status", "DELIVERED");
    }

    @Test
    void concurrentDispatchersMaterializeEachEventOnce() throws Exception {
        UUID actorId = bootstrapId();
        List<UUID> eventIds = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            eventIds.add(insertVersionTwo(
                    actorId,
                    UUID.randomUUID(),
                    index % 2 == 0 ? "AUDITOR|IT_OPERATOR" : "END_USER|SUPPORT_TECHNICIAN"));
        }
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        try {
            List<Future<IdentityAuditDispatcher.DispatchSummary>> futures = new ArrayList<>();
            for (int worker = 0; worker < 3; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return dispatcher.dispatchDue(50);
                }));
            }
            start.countDown();
            int materialized = 0;
            for (Future<IdentityAuditDispatcher.DispatchSummary> future : futures) {
                IdentityAuditDispatcher.DispatchSummary summary = future.get();
                assertThat(summary.retried() + summary.failed()).isZero();
                materialized += summary.delivered();
            }
            assertThat(materialized).isEqualTo(12);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.outbox_deliveries WHERE status = 'DELIVERED'",
                        Integer.class))
                .isEqualTo(12);
        for (UUID eventId : eventIds) {
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                            Integer.class,
                            eventId))
                    .isEqualTo(1);
        }
    }

    private UUID insertVersionTwo(UUID actorId, UUID resourceId, String roles) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO identity.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, result,
                     correlation_id, payload, next_attempt_at)
                VALUES (?, 'HUMAN_USER_CREATED', '2.0', ?, 'IDENTITY',
                        'HUMAN_USER', ?, 'HUMAN_USER', ?, 'SUCCESS', ?,
                        jsonb_build_object('status', 'ACTIVE', 'roles', ?), ?)
                """,
                eventId,
                Timestamp.from(now),
                actorId,
                resourceId,
                UUID.randomUUID(),
                roles,
                Timestamp.from(now));
        return eventId;
    }

    private void insertHistoricalVersionOne(UUID actorId) {
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO identity.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, result,
                     correlation_id, payload, next_attempt_at)
                VALUES (?, 'HUMAN_USER_CREATED', '1.0', ?, 'IDENTITY',
                        'HUMAN_USER', ?, 'HUMAN_USER', ?, 'SUCCESS', ?,
                        '{"status":"ACTIVE"}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Timestamp.from(now));
    }

    private void insertConflictingAudit(UUID eventId, UUID actorId, UUID resourceId) {
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, 'HUMAN_USER_CREATED', '2.0', 'IDENTITY', now(), 'HUMAN_USER', ?,
                        'HUMAN_USER', ?, NULL, 'SUCCESS', ?,
                        '{"status":"ACTIVE","roles":"SYSTEM_ADMIN"}'::jsonb)
                """,
                UUID.randomUUID(), eventId, actorId, resourceId, UUID.randomUUID());
    }

    private Map<String, Object> delivery(UUID eventId) {
        return jdbc.queryForMap(
                "SELECT * FROM identity.outbox_deliveries WHERE event_id = ? AND consumer = 'AUDIT'", eventId);
    }

    private void makeDue(UUID eventId) {
        jdbc.update(
                """
                UPDATE identity.outbox_deliveries
                SET next_attempt_at = now() - interval '1 second'
                WHERE event_id = ?
                """,
                eventId);
    }

    private UUID bootstrapId() {
        return jdbc.queryForObject(
                "SELECT id FROM identity.human_users WHERE normalized_username = 'identity.audit.admin'",
                UUID.class);
    }

    private UUID userCreatedEventId(UUID userId) {
        return jdbc.queryForObject(
                """
                SELECT event_id FROM identity.outbox_events
                WHERE resource_id = ? AND event_type = 'HUMAN_USER_CREATED'
                """,
                UUID.class,
                userId);
    }

    private HttpResponse<String> createUser(
            Session session, String username, List<String> roles, UUID correlationId) throws Exception {
        String roleJson = roles.stream().map(role -> "\"" + role + "\"").collect(java.util.stream.Collectors.joining(","));
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/users"))
                        .header("Cookie", session.cookie())
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-Token", session.csrfToken())
                        .header("X-Correlation-ID", correlationId.toString())
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + username + "\","
                                        + "\"password\":\"Created user passphrase 42!\","
                                        + "\"roles\":[" + roleJson + "]}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Session login() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher csrf = Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(response.body());
        Matcher userId = Pattern.compile("\"userId\":\"([^\"]+)\"").matcher(response.body());
        assertThat(csrf.find()).isTrue();
        assertThat(userId.find()).isTrue();
        return new Session(
                response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0],
                csrf.group(1),
                UUID.fromString(userId.group(1)));
    }

    private static UUID locationId(HttpResponse<String> response) {
        String location = response.headers().firstValue("location").orElseThrow();
        return UUID.fromString(location.substring("/api/v1/users/".length()));
    }

    private record Session(String cookie, String csrfToken, UUID userId) {}
}
