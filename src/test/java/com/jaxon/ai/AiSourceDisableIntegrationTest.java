package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Disable.Admin",
            "jaxon.identity.bootstrap.password=AI disable test passphrase 42!"
        })
class AiSourceDisableIntegrationTest {

    private static final String USERNAME = "Disable.Admin";
    private static final String PASSWORD = "AI disable test passphrase 42!";
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final ObjectMapper JSON = new ObjectMapper();

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

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jaxon.ai.document-storage.root", STORAGE_ROOT::toString);
    }

    @AfterAll
    static void removeStorageRoot() throws Exception {
        try (var files = Files.list(STORAGE_ROOT)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
        Files.deleteIfExists(STORAGE_ROOT);
    }

    @BeforeEach
    void resetState() throws Exception {
        reset(embeddingProvider);
        when(embeddingProvider.isAvailable()).thenReturn(true);
        when(embeddingProvider.tokenize(anyString())).thenReturn(
                new EmbeddingProvider.TokenSequence(new long[] {1}, List.of(new EmbeddingProvider.TokenSpan(0, 1))));
        when(embeddingProvider.embedQuery(anyString())).thenReturn(axis());
        jdbc.update("DELETE FROM ai.outbox_events");
        jdbc.update("DELETE FROM ai.source_chunks");
        jdbc.update("DELETE FROM ai.source_indexing_jobs");
        jdbc.update("DELETE FROM ai.document_sources");
        try (var files = Files.list(STORAGE_ROOT)) {
            for (Path file : files.toList()) Files.delete(file);
        }
    }

    @AfterEach
    void restore() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'SYSTEM_ADMIN' FROM identity.human_users
                WHERE normalized_username = 'disable.admin'
                ON CONFLICT DO NOTHING
                """);
        jdbc.execute("DROP TRIGGER IF EXISTS reject_disable_event ON ai.outbox_events");
        jdbc.execute("DROP FUNCTION IF EXISTS ai.reject_disable_event()");
    }

    @Test
    void disablesAReadySourceAtomicallyAndRemovesItFromRetrievalWithoutDeletingEvidence() throws Exception {
        Session session = login();
        byte[] content = "Manual de red autorizado\n".getBytes(StandardCharsets.UTF_8);
        UUID sourceId = registerReadySource(session, content);
        String storageKey = jdbc.queryForObject(
                "SELECT storage_key FROM ai.document_sources WHERE id = ?", String.class, sourceId);
        List<Map<String, Object>> chunksBefore = chunks(sourceId);
        assertThat(retrievedSources(session)).containsExactly(sourceId.toString());
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> response = disable(session, sourceId.toString(), "{\"expectedVersion\":2}", true, correlationId);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("status").asString()).isEqualTo("DISABLED");
        assertThat(body.path("version").asLong()).isEqualTo(3);
        assertThat(body.propertyNames()).doesNotContain("storageKey");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT event_type || '|' || event_version || '|' || resource_version || '|' ||
                               actor_type || '|' || correlation_id || '|' || payload::text
                        FROM ai.outbox_events WHERE resource_id = ? AND event_type = 'AI_SOURCE_DISABLED'
                        """,
                        String.class,
                        sourceId))
                .isEqualTo("AI_SOURCE_DISABLED|1.0|3|HUMAN_USER|" + correlationId
                        + "|{\"newStatus\": \"DISABLED\", \"previousStatus\": \"READY\"}");
        assertThat(retrievedSources(session)).isEmpty();
        assertThat(chunks(sourceId)).isEqualTo(chunksBefore);
        assertThat(sha256(Files.readAllBytes(STORAGE_ROOT.resolve(storageKey)))).isEqualTo(sha256(content));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.source_indexing_jobs WHERE source_id = ?", Integer.class, sourceId))
                .isEqualTo(1);

        HttpResponse<String> noOp = disable(session, sourceId.toString(), "{\"expectedVersion\":3}", true, UUID.randomUUID());
        assertThat(noOp.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(noOp.body()).path("version").asLong()).isEqualTo(3);
        assertThat(JSON.readTree(noOp.body()).path("updatedAt")).isEqualTo(body.path("updatedAt"));
        assertThat(disable(session, sourceId.toString(), "{\"expectedVersion\":2}", true, UUID.randomUUID())
                        .statusCode())
                .isEqualTo(409);
        assertThat(disabledEvents(sourceId)).isEqualTo(1);

        HttpResponse<String> reindex = post(
                session, "/api/v1/ai/sources/" + sourceId + "/index", "{\"expectedVersion\":3}", true);
        assertThat(reindex.statusCode()).isEqualTo(409);
    }

    @Test
    void disablesAFailedSourceAndRejectsPendingIndexingAndStaleVersions() throws Exception {
        Session session = login();
        UUID failed = registerSource(session);
        jdbc.update("UPDATE ai.document_sources SET status = 'FAILED', version = 2 WHERE id = ?", failed);
        assertThat(disable(session, failed.toString(), "{\"expectedVersion\":1}", true, UUID.randomUUID())
                        .statusCode())
                .isEqualTo(409);
        HttpResponse<String> disabled = disable(session, failed.toString(), "{\"expectedVersion\":2}", true, UUID.randomUUID());
        assertThat(disabled.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                        "SELECT payload::text FROM ai.outbox_events WHERE resource_id = ? AND event_type = 'AI_SOURCE_DISABLED'",
                        String.class,
                        failed))
                .isEqualTo("{\"newStatus\": \"DISABLED\", \"previousStatus\": \"FAILED\"}");

        UUID pending = registerSource(session);
        HttpResponse<String> pendingConflict = disable(session, pending.toString(), "{\"expectedVersion\":0}", true, UUID.randomUUID());
        assertThat(pendingConflict.statusCode()).isEqualTo(409);
        assertThat(pendingConflict.body()).contains("AI_SOURCE_DISABLE_CONFLICT");
        UUID indexing = registerSource(session);
        jdbc.update("UPDATE ai.document_sources SET status = 'INDEXING', version = 1 WHERE id = ?", indexing);
        assertThat(disable(session, indexing.toString(), "{\"expectedVersion\":1}", true, UUID.randomUUID())
                        .statusCode())
                .isEqualTo(409);

        assertThat(jdbc.queryForList("SELECT status FROM ai.document_sources WHERE id IN (?, ?)", String.class, pending, indexing))
                .containsExactlyInAnyOrder("PENDING", "INDEXING");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.outbox_events WHERE event_type = 'AI_SOURCE_DISABLED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void enforcesAuthenticationAuthorizationCsrfAndClosedRequests() throws Exception {
        Session session = login();
        UUID sourceId = registerSource(session);
        jdbc.update("UPDATE ai.document_sources SET status = 'READY', version = 2 WHERE id = ?", sourceId);
        String path = "/api/v1/ai/sources/" + sourceId + "/disable";

        HttpRequest anonymous = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":2}"))
                .build();
        assertThat(http.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(post(session, path, "{\"expectedVersion\":2}", false).body()).contains("CSRF_INVALID");

        assertThat(disable(session, "not-a-uuid", "{\"expectedVersion\":2}", true, UUID.randomUUID()).statusCode())
                .isEqualTo(404);
        assertThat(disable(session, "34844482-1d29-1d30-87fd-1bb67a22e050", "{\"expectedVersion\":2}", true, UUID.randomUUID())
                        .statusCode())
                .isEqualTo(404);
        HttpResponse<String> missing = disable(session, UUID.randomUUID().toString(), "{\"expectedVersion\":2}", true, UUID.randomUUID());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("AI_SOURCE_NOT_FOUND");

        for (String invalid : List.of(
                "{}", "{\"expectedVersion\":-1}", "{\"expectedVersion\":\"2\"}", "{\"expectedVersion\":2.0}",
                "{\"expectedVersion\":null}", "{\"expectedVersion\":2,\"reason\":\"x\"}", "[2]",
                "{\"expectedVersion\":99999999999999999999}")) {
            HttpResponse<String> response = post(session, path, invalid, true);
            assertThat(response.statusCode()).as(invalid).isEqualTo(422);
            assertThat(response.body()).doesNotContain("reason");
        }
        for (String malformed : List.of("", "{\"expectedVersion\":", "{\"expectedVersion\":2,\"expectedVersion\":2}",
                "{\"expectedVersion\":2} {}")) {
            assertThat(post(session, path, malformed, true).statusCode()).as(malformed).isEqualTo(400);
        }

        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'SYSTEM_ADMIN'");
        Session operator = login();
        HttpResponse<String> forbidden = post(operator, path, "{\"expectedVersion\":2}", true);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("ACCESS_DENIED");

        assertThat(jdbc.queryForMap("SELECT status, version FROM ai.document_sources WHERE id = ?", sourceId))
                .containsEntry("status", "READY")
                .containsEntry("version", 2L);
        assertThat(disabledEvents(sourceId)).isZero();
    }

    @Test
    void concurrentRequestsProduceOneTransitionOneConflictAndOneEvent() throws Exception {
        Session session = login();
        UUID sourceId = registerSource(session);
        jdbc.update("UPDATE ai.document_sources SET status = 'READY', version = 2 WHERE id = ?", sourceId);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> {
                start.await();
                return disable(session, sourceId.toString(), "{\"expectedVersion\":2}", true, UUID.randomUUID())
                        .statusCode();
            };
            Future<Integer> first = executor.submit(call);
            Future<Integer> second = executor.submit(call);
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(), second.get()));
            statuses.sort(Integer::compareTo);
            assertThat(statuses).containsExactly(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(disabledEvents(sourceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM ai.document_sources WHERE id = ?", Long.class, sourceId))
                .isEqualTo(3);
    }

    @Test
    void rollsBackTheTransitionWhenTheEventCannotBeStored() throws Exception {
        Session session = login();
        UUID sourceId = registerSource(session);
        jdbc.update("UPDATE ai.document_sources SET status = 'READY', version = 2 WHERE id = ?", sourceId);
        jdbc.execute(
                """
                CREATE FUNCTION ai.reject_disable_event() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.event_type = 'AI_SOURCE_DISABLED' THEN
                    RAISE EXCEPTION 'reject disable event';
                  END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_disable_event BEFORE INSERT ON ai.outbox_events
                FOR EACH ROW EXECUTE FUNCTION ai.reject_disable_event()
                """);

        HttpResponse<String> response = disable(session, sourceId.toString(), "{\"expectedVersion\":2}", true, UUID.randomUUID());

        assertThat(response.statusCode()).isGreaterThanOrEqualTo(500);
        assertThat(jdbc.queryForMap("SELECT status, version FROM ai.document_sources WHERE id = ?", sourceId))
                .containsEntry("status", "READY")
                .containsEntry("version", 2L);
        assertThat(disabledEvents(sourceId)).isZero();
    }

    private UUID registerReadySource(Session session, byte[] content) throws Exception {
        UUID sourceId = register(session, content);
        jdbc.update(
                """
                INSERT INTO ai.source_indexing_jobs
                    (id, source_id, status, requested_by_user_id, correlation_id, created_at,
                     claimed_at, completed_at)
                SELECT ?, id, 'SUCCEEDED', created_by_user_id, ?, now(), now(), now()
                FROM ai.document_sources WHERE id = ?
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                sourceId);
        jdbc.update(
                """
                INSERT INTO ai.source_chunks
                    (id, source_id, ordinal, content, content_sha256, token_count, start_token,
                     end_token, embedding, model_id, model_revision, tokenizer_sha256,
                     chunker_version, created_at)
                VALUES (?, ?, 0, 'Manual de red autorizado', ?, 4, 0, 4, ?::vector, ?, ?, ?, ?, now())
                """,
                UUID.randomUUID(),
                sourceId,
                "b".repeat(64),
                AiIndexingPersistence.vectorLiteral(axis()),
                EmbeddingProvider.MODEL_ID,
                EmbeddingProvider.MODEL_REVISION,
                EmbeddingProvider.VOCABULARY_DIGEST,
                AiSourceChunker.VERSION);
        jdbc.update("UPDATE ai.document_sources SET status = 'READY', version = 2 WHERE id = ?", sourceId);
        return sourceId;
    }

    private UUID registerSource(Session session) throws Exception {
        return register(session, ("Fuente " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    }

    private UUID register(Session session, byte[] content) throws Exception {
        String boundary = "JAXON-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\n");
        write(body, "Fuente " + UUID.randomUUID() + "\r\n--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"source.txt\"\r\n");
        write(body, "Content-Type: text/plain\r\n\r\n");
        body.write(content);
        write(body, "\r\n--" + boundary + "--\r\n");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ai/sources"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(JSON.readTree(response.body()).path("sourceId").asString());
    }

    private List<String> retrievedSources(Session session) throws Exception {
        HttpResponse<String> response = post(session, "/api/v1/ai/retrieval", "{\"query\":\"manual\",\"limit\":10}", true);
        assertThat(response.statusCode()).isEqualTo(200);
        List<String> sources = new ArrayList<>();
        JSON.readTree(response.body()).path("results").forEach(result -> sources.add(result.path("sourceId").asString()));
        return sources;
    }

    private List<Map<String, Object>> chunks(UUID sourceId) {
        return jdbc.queryForList(
                "SELECT id, ordinal, content, content_sha256, embedding::text AS embedding FROM ai.source_chunks WHERE source_id = ? ORDER BY ordinal",
                sourceId);
    }

    private int disabledEvents(UUID sourceId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM ai.outbox_events WHERE resource_id = ? AND event_type = 'AI_SOURCE_DISABLED'",
                Integer.class,
                sourceId);
    }

    private HttpResponse<String> disable(
            Session session, String sourceId, String body, boolean csrf, UUID correlationId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/ai/sources/" + sourceId + "/disable"))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrf) builder.header("X-CSRF-Token", session.csrfToken());
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(Session session, String path, String body, boolean csrf) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrf) builder.header("X-CSRF-Token", session.csrfToken());
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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

    private static float[] axis() {
        float[] vector = new float[EmbeddingProvider.DIMENSIONS];
        vector[0] = 1.0f;
        return vector;
    }

    private static void write(ByteArrayOutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("jaxon-ai-disable-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Session(String cookie, String csrfToken) {}
}
