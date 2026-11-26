package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Audit.Dispatch.Admin",
            "jaxon.identity.bootstrap.password=AI audit dispatch passphrase 42!"
        })
class AiAuditDispatchIntegrationTest {

    private static final String USERNAME = "Audit.Dispatch.Admin";
    private static final String PASSWORD = "AI audit dispatch passphrase 42!";
    private static final Path STORAGE_ROOT = createStorageRoot();

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
    AiAuditDispatcher dispatcher;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jaxon.ai.document-storage.root", STORAGE_ROOT::toString);
    }

    @AfterAll
    static void removeStorageRoot() throws Exception {
        Files.deleteIfExists(STORAGE_ROOT);
    }

    @BeforeEach
    void cleanAiState() {
        jdbc.update("DELETE FROM ai.outbox_events");
        jdbc.update("DELETE FROM ai.source_chunks");
        jdbc.update("DELETE FROM ai.source_indexing_jobs");
        jdbc.update("DELETE FROM ai.document_sources");
    }

    @AfterEach
    void dropFaults() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_audit_insert ON audit_reporting.audit_events");
        jdbc.execute("DROP FUNCTION IF EXISTS audit_reporting.reject_audit_insert()");
    }

    @Test
    void deliversADisabledSourceExactlyOnceWithoutTouchingOtherEvents() throws Exception {
        Session session = login();
        UUID sourceId = readySource("FAILED");
        UUID correlationId = UUID.randomUUID();
        assertThat(disable(session, sourceId, 2, correlationId).statusCode()).isEqualTo(200);
        insertOtherEvent(sourceId);

        AiAuditDispatcher.DispatchSummary summary = dispatcher.dispatchDue(50);

        assertThat(summary).isEqualTo(new AiAuditDispatcher.DispatchSummary(1, 0, 0, 0));
        Map<String, Object> outbox = jdbc.queryForMap(
                """
                SELECT event_id, actor_id, occurred_at FROM ai.outbox_events
                WHERE resource_id = ? AND event_type = 'AI_SOURCE_DISABLED'
                """,
                sourceId);
        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT * FROM audit_reporting.audit_events WHERE source_event_id = ?", outbox.get("event_id"));
        assertThat(audit)
                .containsEntry("event_type", "AI_SOURCE_DISABLED")
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "AI")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("actor_id", outbox.get("actor_id"))
                .containsEntry("resource_type", "AI_SOURCE")
                .containsEntry("resource_id", sourceId)
                .containsEntry("resource_version", 3L)
                .containsEntry("result", "SUCCESS")
                .containsEntry("correlation_id", correlationId)
                .containsEntry("occurred_at", outbox.get("occurred_at"));
        assertThat(jdbc.queryForObject(
                        "SELECT metadata::text FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        String.class,
                        outbox.get("event_id")))
                .isEqualTo("{\"newStatus\": \"DISABLED\", \"previousStatus\": \"FAILED\"}");
        assertThat(delivery((UUID) outbox.get("event_id")))
                .containsEntry("status", "DELIVERED")
                .containsEntry("attempts", 1)
                .containsEntry("last_error_code", null);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.outbox_deliveries d JOIN ai.outbox_events e USING (event_id) "
                                + "WHERE e.event_type <> 'AI_SOURCE_DISABLED'",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM ai.outbox_events", String.class))
                .containsExactly("PENDING");

        assertThat(dispatcher.dispatchDue(50)).isEqualTo(new AiAuditDispatcher.DispatchSummary(0, 0, 0, 0));
        assertThat(auditCount(sourceId)).isEqualTo(1);
    }

    @Test
    void redeliveryOfTheSameEventIsIdempotent() throws Exception {
        Session session = login();
        UUID sourceId = readySource("READY");
        disable(session, sourceId, 2, UUID.randomUUID());
        dispatcher.dispatchDue(50);
        UUID eventId = disabledEventId(sourceId);
        jdbc.update("DELETE FROM ai.outbox_deliveries WHERE event_id = ?", eventId);

        assertThat(dispatcher.dispatchDue(50)).isEqualTo(new AiAuditDispatcher.DispatchSummary(0, 1, 0, 0));

        assertThat(auditCount(sourceId)).isEqualTo(1);
        assertThat(delivery(eventId)).containsEntry("status", "DELIVERED");
    }

    @Test
    void conflictingContentIsNeverOverwrittenAndFailsAfterTenAttempts() throws Exception {
        Session session = login();
        UUID sourceId = readySource("READY");
        disable(session, sourceId, 2, UUID.randomUUID());
        UUID eventId = disabledEventId(sourceId);
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, 'AI_SOURCE_DISABLED', '1.0', 'AI', now(), 'HUMAN_USER', ?, 'AI_SOURCE', ?, 9,
                        'SUCCESS', ?, '{"previousStatus":"FAILED","newStatus":"DISABLED"}'::jsonb)
                """,
                UUID.randomUUID(), eventId, UUID.randomUUID(), sourceId, UUID.randomUUID());

        for (int attempt = 1; attempt <= 10; attempt++) {
            makeDue(eventId);
            AiAuditDispatcher.DispatchSummary summary = dispatcher.dispatchDue(1);
            assertThat(summary.delivered() + summary.duplicates()).isZero();
            assertThat(delivery(eventId))
                    .containsEntry("attempts", attempt)
                    .containsEntry("last_error_code", "AUDIT_CONFLICT")
                    .containsEntry("status", attempt == 10 ? "FAILED" : "PENDING");
        }
        makeDue(eventId);
        assertThat(dispatcher.dispatchDue(5)).isEqualTo(new AiAuditDispatcher.DispatchSummary(0, 0, 0, 0));
        assertThat(jdbc.queryForObject(
                        "SELECT resource_version FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        Long.class,
                        eventId))
                .isEqualTo(9L);
    }

    @Test
    void auditUnavailabilityNeverInvalidatesTheConfirmedOperationAndRecovers() throws Exception {
        Session session = login();
        UUID sourceId = readySource("READY");
        jdbc.execute(
                """
                CREATE FUNCTION audit_reporting.reject_audit_insert() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'audit unavailable'; END $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_audit_insert BEFORE INSERT ON audit_reporting.audit_events
                FOR EACH ROW EXECUTE FUNCTION audit_reporting.reject_audit_insert()
                """);

        assertThat(disable(session, sourceId, 2, UUID.randomUUID()).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT status FROM ai.document_sources WHERE id = ?", String.class, sourceId))
                .isEqualTo("DISABLED");
        Instant before = Instant.now();
        assertThat(dispatcher.dispatchDue(50)).isEqualTo(new AiAuditDispatcher.DispatchSummary(0, 0, 1, 0));
        UUID eventId = disabledEventId(sourceId);
        Map<String, Object> pending = delivery(eventId);
        assertThat(pending)
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 1)
                .containsEntry("last_error_code", "AUDIT_UNAVAILABLE");
        assertThat(((Timestamp) pending.get("next_attempt_at")).toInstant()).isAfter(before);
        assertThat(dispatcher.dispatchDue(50)).isEqualTo(new AiAuditDispatcher.DispatchSummary(0, 0, 0, 0));

        dropFaults();
        makeDue(eventId);
        assertThat(dispatcher.dispatchDue(50)).isEqualTo(new AiAuditDispatcher.DispatchSummary(1, 0, 0, 0));
        assertThat(delivery(eventId)).containsEntry("status", "DELIVERED").containsEntry("attempts", 2);
        assertThat(auditCount(sourceId)).isEqualTo(1);
    }

    @Test
    void aRejectedEventDoesNotBlockTheNextOne() throws Exception {
        Session session = login();
        UUID first = readySource("READY");
        UUID second = readySource("READY");
        disable(session, first, 2, UUID.randomUUID());
        disable(session, second, 2, UUID.randomUUID());
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, 'AI_SOURCE_DISABLED', '1.0', 'AI', now(), 'HUMAN_USER', ?, 'AI_SOURCE', ?, 7,
                        'SUCCESS', ?, '{"previousStatus":"READY","newStatus":"DISABLED"}'::jsonb)
                """,
                UUID.randomUUID(), disabledEventId(first), UUID.randomUUID(), first, UUID.randomUUID());

        assertThat(dispatcher.dispatchDue(50)).isEqualTo(new AiAuditDispatcher.DispatchSummary(1, 0, 1, 0));
        assertThat(delivery(disabledEventId(first))).containsEntry("status", "PENDING");
        assertThat(delivery(disabledEventId(second))).containsEntry("status", "DELIVERED");
    }

    @Test
    void concurrentDispatchersMaterializeEachEventOnce() throws Exception {
        Session session = login();
        List<UUID> sources = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            UUID sourceId = readySource(index % 2 == 0 ? "READY" : "FAILED");
            assertThat(disable(session, sourceId, 2, UUID.randomUUID()).statusCode()).isEqualTo(200);
            sources.add(sourceId);
        }
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        try {
            List<Future<AiAuditDispatcher.DispatchSummary>> futures = new ArrayList<>();
            for (int worker = 0; worker < 3; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return dispatcher.dispatchDue(50);
                }));
            }
            start.countDown();
            int materialized = 0;
            for (Future<AiAuditDispatcher.DispatchSummary> future : futures) {
                AiAuditDispatcher.DispatchSummary summary = future.get();
                assertThat(summary.retried() + summary.failed()).isZero();
                materialized += summary.delivered();
            }
            assertThat(materialized).isEqualTo(12);
        } finally {
            executor.shutdownNow();
        }
        for (UUID sourceId : sources) assertThat(auditCount(sourceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.outbox_deliveries WHERE status = 'DELIVERED'", Integer.class))
                .isEqualTo(12);
    }

    private UUID readySource(String status) {
        UUID sourceId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO ai.document_sources
                    (id, name, media_type, size_bytes, sha256, storage_key, status,
                     created_by_user_id, created_at, updated_at, version)
                VALUES (?, ?, 'text/plain', 7, ?, ?, ?, ?, ?, ?, 2)
                """,
                sourceId, "Audit source " + sourceId, "0".repeat(64), UUID.randomUUID() + ".txt", status,
                UUID.randomUUID(), Timestamp.from(now), Timestamp.from(now));
        return sourceId;
    }

    private void insertOtherEvent(UUID sourceId) {
        jdbc.update(
                """
                INSERT INTO ai.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module, actor_type, actor_id,
                     resource_type, resource_id, resource_version, result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'AI_SOURCE_INDEXED', '1.0', now(), 'AI', 'HUMAN_USER', ?, 'AI_SOURCE', ?, 2,
                        'SUCCESS', ?, '{"status":"READY"}'::jsonb, now())
                """,
                UUID.randomUUID(), UUID.randomUUID(), sourceId, UUID.randomUUID());
    }

    private UUID disabledEventId(UUID sourceId) {
        return jdbc.queryForObject(
                "SELECT event_id FROM ai.outbox_events WHERE resource_id = ? AND event_type = 'AI_SOURCE_DISABLED'",
                UUID.class,
                sourceId);
    }

    private Map<String, Object> delivery(UUID eventId) {
        return jdbc.queryForMap(
                "SELECT * FROM ai.outbox_deliveries WHERE event_id = ? AND consumer = 'AUDIT'", eventId);
    }

    private void makeDue(UUID eventId) {
        jdbc.update(
                "UPDATE ai.outbox_deliveries SET next_attempt_at = now() - interval '1 second' WHERE event_id = ?",
                eventId);
    }

    private int auditCount(UUID sourceId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_reporting.audit_events WHERE resource_id = ?", Integer.class, sourceId);
    }

    private HttpResponse<String> disable(Session session, UUID sourceId, long version, UUID correlationId)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/ai/sources/" + sourceId + "/disable"))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .header("X-CSRF-Token", session.csrfToken())
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":" + version + "}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Session login() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher matcher = Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return new Session(response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0], matcher.group(1));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("jaxon-ai-audit-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Session(String cookie, String csrfToken) {}
}
