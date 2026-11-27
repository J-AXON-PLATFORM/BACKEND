package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
            "jaxon.identity.bootstrap.username=Ai.Admin",
            "jaxon.identity.bootstrap.password=AI source test passphrase 42!",
            "jaxon.ai.indexing.enabled=true",
            "jaxon.ai.indexing.initial-delay=1h"
        })
class AiSourceIntegrationTest {

    private static final String USERNAME = "Ai.Admin";
    private static final String PASSWORD = "AI source test passphrase 42!";
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
    AiIndexingPersistence indexingPersistence;

    @Autowired
    AiIndexingWorker indexingWorker;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @AfterAll
    static void removeStorageRoot() throws Exception {
        try (var files = Files.list(STORAGE_ROOT)) {
            files.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // Test cleanup only.
                }
            });
        }
        Files.deleteIfExists(STORAGE_ROOT);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jaxon.ai.document-storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void clearSources() throws Exception {
        reset(embeddingProvider);
        when(embeddingProvider.isAvailable()).thenReturn(true);
        when(embeddingProvider.tokenize(anyString())).thenAnswer(invocation -> tokenize(invocation.getArgument(0)));
        when(embeddingProvider.embedPassages(anyList())).thenAnswer(invocation -> embeddings(invocation.getArgument(0)));
        jdbc.update("DELETE FROM ai.outbox_events");
        jdbc.update("DELETE FROM ai.source_chunks");
        jdbc.update("DELETE FROM ai.source_indexing_jobs");
        jdbc.update("DELETE FROM ai.document_sources");
        try (var files = Files.list(STORAGE_ROOT)) {
            for (Path file : files.toList()) Files.delete(file);
        }
    }

    @AfterEach
    void restoreAdministratorRole() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'SYSTEM_ADMIN' FROM identity.human_users
                WHERE normalized_username = 'ai.admin'
                ON CONFLICT DO NOTHING
                """);
        jdbc.execute("DROP TRIGGER IF EXISTS reject_ai_outbox ON ai.outbox_events");
        jdbc.execute("DROP FUNCTION IF EXISTS ai.reject_ai_outbox()");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_second_chunk ON ai.source_chunks");
        jdbc.execute("DROP FUNCTION IF EXISTS ai.reject_second_chunk()");
    }

    @Test
    void registersListsAndGetsAStoredUtf8SourceWithoutLeakingItsPhysicalKey() throws Exception {
        Session session = login();
        byte[] content = "Guía técnica autorizada\nSegunda línea.\n".getBytes(StandardCharsets.UTF_8);
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> created = upload(
                session, "  Guía   de soporte  ", "manual-original.txt", "text/plain", content, correlationId);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID sourceId = UUID.fromString(extract(created.body(), "sourceId"));
        assertThat(created.headers().firstValue("location"))
                .contains("/api/v1/ai/sources/" + sourceId);
        assertThat(created.body())
                .contains("\"name\":\"Guía de soporte\"")
                .contains("\"mediaType\":\"text/plain\"")
                .contains("\"sizeBytes\":" + content.length)
                .contains("\"sha256\":\"" + sha256(content) + "\"")
                .contains("\"status\":\"PENDING\"")
                .contains("\"version\":0")
                .doesNotContain("manual-original.txt", "storageKey", STORAGE_ROOT.toString());

        Map<String, Object> source = jdbc.queryForMap(
                """
                SELECT name, media_type, size_bytes, sha256, storage_key, status,
                       created_by_user_id, version
                FROM ai.document_sources WHERE id = ?
                """,
                sourceId);
        String storageKey = (String) source.get("storage_key");
        assertThat(source)
                .containsEntry("name", "Guía de soporte")
                .containsEntry("media_type", "text/plain")
                .containsEntry("size_bytes", (long) content.length)
                .containsEntry("status", "PENDING")
                .containsEntry("version", 0L);
        assertThat(((String) source.get("sha256")).trim()).isEqualTo(sha256(content));
        assertThat(Files.readAllBytes(STORAGE_ROOT.resolve(storageKey))).isEqualTo(content);

        Map<String, Object> event = jdbc.queryForMap(
                """
                SELECT event_type, event_version, producer_module, resource_type,
                       resource_version, correlation_id, payload::text AS payload
                FROM ai.outbox_events WHERE resource_id = ?
                """,
                sourceId);
        assertThat(event)
                .containsEntry("event_type", "AI_SOURCE_REGISTERED")
                .containsEntry("event_version", "2.0")
                .containsEntry("producer_module", "AI")
                .containsEntry("resource_type", "AI_SOURCE")
                .containsEntry("resource_version", 0L)
                .containsEntry("correlation_id", correlationId)
                .containsEntry("payload", "{\"status\": \"PENDING\"}");
        assertThat(event.toString()).doesNotContain("Guía", storageKey, STORAGE_ROOT.toString());

        HttpResponse<String> detail = get(session, "/api/v1/ai/sources/" + sourceId);
        HttpResponse<String> page = get(session, "/api/v1/ai/sources?page=0&size=20");
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).isEqualTo(created.body());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body())
                .contains("\"sourceId\":\"" + sourceId + "\"")
                .contains("\"page\":0", "\"size\":20", "\"totalElements\":1", "\"totalPages\":1")
                .doesNotContain("storageKey", storageKey);
    }

    @Test
    void acceptsIdenticalBytesAsDistinctSources() throws Exception {
        Session session = login();
        byte[] content = "same bytes".getBytes(StandardCharsets.UTF_8);

        UUID first = UUID.fromString(extract(
                upload(session, "First", "first.txt", "text/plain", content, UUID.randomUUID()).body(),
                "sourceId"));
        UUID second = UUID.fromString(extract(
                upload(session, "Second", "second.txt", "text/plain", content, UUID.randomUUID()).body(),
                "sourceId"));

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForList(
                        "SELECT storage_key FROM ai.document_sources ORDER BY name", String.class))
                .hasSize(2)
                .doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.outbox_events", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void rejectsInvalidInputPaginationAuthorizationAndCsrf() throws Exception {
        Session administrator = login();
        assertThat(upload(administrator, "Binary", "binary.bin", "application/octet-stream", new byte[] {1}, UUID.randomUUID())
                        .body())
                .contains("\"code\":\"VALIDATION_FAILED\"", "UNSUPPORTED_MEDIA_TYPE");
        assertThat(upload(administrator, "Invalid UTF-8", "bad.txt", "text/plain", new byte[] {(byte) 0xc3, 0x28}, UUID.randomUUID())
                        .body())
                .contains("\"code\":\"VALIDATION_FAILED\"", "INVALID_UTF8");
        assertThat(upload(administrator, "   ", "empty-name.txt", "text/plain", new byte[0], UUID.randomUUID())
                        .statusCode())
                .isEqualTo(422);
        assertThat(get(administrator, "/api/v1/ai/sources?page=-1&size=51").statusCode())
                .isEqualTo(422);
        assertThat(get(administrator, "/api/v1/ai/sources/not-a-uuid").statusCode())
                .isEqualTo(404);
        assertThat(uploadWithExtraPart(administrator).statusCode()).isEqualTo(400);

        HttpResponse<String> noCsrf = upload(
                new Session(administrator.cookie(), "wrong"),
                "No CSRF",
                "no-csrf.txt",
                "text/plain",
                "safe".getBytes(StandardCharsets.UTF_8),
                UUID.randomUUID());
        assertThat(noCsrf.statusCode()).isEqualTo(403);

        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'SYSTEM_ADMIN'");
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'IT_OPERATOR' FROM identity.human_users
                WHERE normalized_username = 'ai.admin'
                ON CONFLICT DO NOTHING
                """);
        Session operator = login();
        assertThat(get(operator, "/api/v1/ai/sources").statusCode()).isEqualTo(403);
        assertThat(upload(operator, "Denied", "denied.txt", "text/plain", new byte[0], UUID.randomUUID())
                        .statusCode())
                .isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.document_sources", Integer.class))
                .isZero();
    }

    @Test
    void acceptsExactlyOneMebibyteAndRejectsOneAdditionalByte() throws Exception {
        Session administrator = login();
        byte[] maximum = new byte[(int) FileSystemDocumentStorage.MAX_BYTES];
        assertThat(upload(administrator, "Maximum", "maximum.txt", "text/plain", maximum, UUID.randomUUID())
                        .statusCode())
                .isEqualTo(201);

        byte[] oversized = new byte[(int) FileSystemDocumentStorage.MAX_BYTES + 1];
        assertThat(upload(administrator, "Oversized", "oversized.txt", "text/plain", oversized, UUID.randomUUID())
                        .statusCode())
                .isEqualTo(413);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.document_sources", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void removesTheStoredFileAndPublishesNoMetadataWhenTheDatabaseCommitFails() throws Exception {
        Session session = login();
        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION ai.reject_ai_outbox() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced outbox failure'; END; $$
                """);
        jdbc.execute(
                """
                CREATE CONSTRAINT TRIGGER reject_ai_outbox AFTER INSERT ON ai.outbox_events
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW EXECUTE FUNCTION ai.reject_ai_outbox()
                """);

        HttpResponse<String> response = upload(
                session,
                "Rollback",
                "rollback.txt",
                "text/plain",
                "rollback".getBytes(StandardCharsets.UTF_8),
                UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.document_sources", Integer.class))
                .isZero();
        try (var files = Files.list(STORAGE_ROOT)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void acceptsExactlyOneConcurrentIndexingRequest() throws Exception {
        Session session = login();
        UUID sourceId = UUID.fromString(extract(upload(
                        session,
                        "Concurrent",
                        "concurrent.txt",
                        "text/plain",
                        "content".getBytes(StandardCharsets.UTF_8),
                        UUID.randomUUID())
                .body(), "sourceId"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statuses;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return index(session, sourceId, 0, UUID.randomUUID()).statusCode();
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return index(session, sourceId, 0, UUID.randomUUID()).statusCode();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertThat(statuses).containsExactlyInAnyOrder(202, 409);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.source_indexing_jobs WHERE source_id = ?",
                        Integer.class,
                        sourceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM ai.outbox_events
                        WHERE resource_id = ? AND event_type = 'AI_SOURCE_INDEXING_REQUESTED'
                        """,
                        Integer.class,
                        sourceId))
                .isEqualTo(1);
    }

    @Test
    void indexesAsynchronouslyWithClosedChunksVectorsAndEvents() throws Exception {
        Session session = login();
        String content = numberedWords(600);
        UUID sourceId = UUID.fromString(extract(upload(
                        session,
                        "Indexable",
                        "indexable.txt",
                        "text/plain",
                        content.getBytes(StandardCharsets.UTF_8),
                        UUID.randomUUID())
                .body(), "sourceId"));

        UUID correlationId = UUID.randomUUID();
        HttpResponse<String> accepted = index(session, sourceId, 0, correlationId);
        HttpResponse<String> duplicate = index(session, sourceId, 0, UUID.randomUUID());

        assertThat(accepted.statusCode()).isEqualTo(202);
        assertThat(accepted.body()).contains("\"status\":\"INDEXING\"", "\"version\":1");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        indexingWorker.poll();
        awaitStatus(sourceId, "READY");

        assertThat(get(session, "/api/v1/ai/sources/" + sourceId).body())
                .contains("\"status\":\"READY\"", "\"version\":2")
                .doesNotContain("failureCode", "storageKey", "embedding");
        assertThat(jdbc.queryForList(
                        """
                        SELECT ordinal, token_count, start_token, end_token,
                               vector_dims(embedding) AS dimensions,
                               model_id, model_revision, tokenizer_sha256, chunker_version
                        FROM ai.source_chunks WHERE source_id = ? ORDER BY ordinal
                        """,
                        sourceId))
                .hasSize(2)
                .satisfiesExactly(
                        first -> assertThat(first)
                                .containsEntry("ordinal", 0)
                                .containsEntry("token_count", 480)
                                .containsEntry("start_token", 0)
                                .containsEntry("end_token", 480)
                                .containsEntry("dimensions", 384)
                                .containsEntry("model_id", EmbeddingProvider.MODEL_ID)
                                .containsEntry("model_revision", EmbeddingProvider.MODEL_REVISION)
                                .containsEntry("tokenizer_sha256", EmbeddingProvider.VOCABULARY_DIGEST)
                                .containsEntry("chunker_version", AiSourceChunker.VERSION),
                        second -> assertThat(second)
                                .containsEntry("ordinal", 1)
                                .containsEntry("token_count", 192)
                                .containsEntry("start_token", 408)
                                .containsEntry("end_token", 600)
                                .containsEntry("dimensions", 384));
        assertThat(jdbc.queryForList(
                        "SELECT event_type FROM ai.outbox_events WHERE resource_id = ? ORDER BY occurred_at",
                        String.class,
                        sourceId))
                .containsExactly(
                        "AI_SOURCE_REGISTERED", "AI_SOURCE_INDEXING_REQUESTED", "AI_SOURCE_INDEXED");
        assertLifecycleEvents(sourceId, correlationId, "AI_SOURCE_INDEXED", "SUCCESS", "READY");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM pg_indexes
                        WHERE schemaname = 'ai' AND indexname = 'ai_source_chunks_embedding_hnsw_idx'
                        """,
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void keepsPendingWhenProviderIsUnavailableAndRequiresCsrf() throws Exception {
        Session session = login();
        UUID sourceId = UUID.fromString(extract(upload(
                        session,
                        "Unavailable",
                        "unavailable.txt",
                        "text/plain",
                        "content".getBytes(StandardCharsets.UTF_8),
                        UUID.randomUUID())
                .body(), "sourceId"));
        when(embeddingProvider.isAvailable()).thenReturn(false);

        assertThat(index(session, UUID.randomUUID(), 0, UUID.randomUUID()).statusCode())
                .isEqualTo(404);
        assertThat(index(session, sourceId, 1, UUID.randomUUID()).statusCode())
                .isEqualTo(409);
        assertThat(index(session, sourceId, 0, UUID.randomUUID()).statusCode()).isEqualTo(503);
        assertThat(index(new Session(session.cookie(), "wrong"), sourceId, 0, UUID.randomUUID()).statusCode())
                .isEqualTo(403);
        assertThat(jdbc.queryForMap(
                        "SELECT status, version FROM ai.document_sources WHERE id = ?", sourceId))
                .containsEntry("status", "PENDING")
                .containsEntry("version", 0L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.source_indexing_jobs", Integer.class))
                .isZero();
    }

    @Test
    void rollsBackAllChunksAndFailsClosedWhenFinalPublicationFails() throws Exception {
        Session session = login();
        UUID sourceId = UUID.fromString(extract(upload(
                        session,
                        "Atomic",
                        "atomic.txt",
                        "text/plain",
                        numberedWords(600).getBytes(StandardCharsets.UTF_8),
                        UUID.randomUUID())
                .body(), "sourceId"));
        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION ai.reject_second_chunk() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN
                    IF NEW.ordinal = 1 THEN RAISE EXCEPTION 'forced chunk failure'; END IF;
                    RETURN NEW;
                END; $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_second_chunk BEFORE INSERT ON ai.source_chunks
                FOR EACH ROW EXECUTE FUNCTION ai.reject_second_chunk()
                """);

        UUID correlationId = UUID.randomUUID();
        assertThat(index(session, sourceId, 0, correlationId).statusCode()).isEqualTo(202);
        indexingWorker.poll();
        awaitStatus(sourceId, "FAILED");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.source_chunks WHERE source_id = ?", Integer.class, sourceId))
                .isZero();
        assertThat(jdbc.queryForMap(
                        "SELECT status, failure_code FROM ai.source_indexing_jobs WHERE source_id = ?", sourceId))
                .containsEntry("status", "FAILED")
                .containsEntry("failure_code", "INDEXING_FAILED");
        assertThat(jdbc.queryForList(
                        "SELECT event_type FROM ai.outbox_events WHERE resource_id = ? ORDER BY occurred_at",
                        String.class,
                        sourceId))
                .containsExactly(
                        "AI_SOURCE_REGISTERED", "AI_SOURCE_INDEXING_REQUESTED", "AI_SOURCE_INDEXING_FAILED");
        assertLifecycleEvents(sourceId, correlationId, "AI_SOURCE_INDEXING_FAILED", "FAILED", "FAILED");
    }

    @Test
    void terminalizesAnAbandonedClaimWithoutReinference() throws Exception {
        Session session = login();
        UUID sourceId = UUID.fromString(extract(upload(
                        session,
                        "Interrupted",
                        "interrupted.txt",
                        "text/plain",
                        "content".getBytes(StandardCharsets.UTF_8),
                        UUID.randomUUID())
                .body(), "sourceId"));
        UUID actorId = jdbc.queryForObject(
                "SELECT created_by_user_id FROM ai.document_sources WHERE id = ?", UUID.class, sourceId);
        UUID correlationId = UUID.randomUUID();
        jdbc.update(
                "UPDATE ai.document_sources SET status = 'INDEXING', version = 1, updated_at = now() WHERE id = ?",
                sourceId);
        jdbc.update(
                """
                INSERT INTO ai.source_indexing_jobs
                    (id, source_id, status, requested_by_user_id, correlation_id, created_at,
                     claimed_at, claim_expires_at)
                VALUES (?, ?, 'CLAIMED', ?, ?, now() - interval '2 minutes',
                        now() - interval '2 minutes', now() - interval '1 minute')
                """,
                UUID.randomUUID(),
                sourceId,
                actorId,
                correlationId);

        indexingWorker.poll();
        awaitStatus(sourceId, "FAILED");

        assertThat(jdbc.queryForMap(
                        "SELECT status, failure_code FROM ai.source_indexing_jobs WHERE source_id = ?", sourceId))
                .containsEntry("status", "FAILED")
                .containsEntry("failure_code", "INDEXING_INTERRUPTED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.source_chunks WHERE source_id = ?", Integer.class, sourceId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM ai.outbox_events
                        WHERE resource_id = ? AND event_type = 'AI_SOURCE_INDEXING_FAILED'
                        """,
                        Integer.class,
                        sourceId))
                .isEqualTo(1);
        Map<String, Object> failed = jdbc.queryForMap(
                """
                SELECT event_version, actor_type, actor_id, resource_version, result,
                       correlation_id, payload::text AS payload
                FROM ai.outbox_events
                WHERE resource_id = ? AND event_type = 'AI_SOURCE_INDEXING_FAILED'
                """,
                sourceId);
        assertThat(failed)
                .containsEntry("event_version", "2.0")
                .containsEntry("actor_type", "SYSTEM")
                .containsEntry("actor_id", null)
                .containsEntry("resource_version", 2L)
                .containsEntry("result", "FAILED")
                .containsEntry("correlation_id", correlationId)
                .containsEntry("payload", "{\"status\": \"FAILED\"}");
    }

    @Test
    void discardsAResultProducedAfterItsClaimExpired() throws Exception {
        Session session = login();
        UUID sourceId = UUID.fromString(extract(upload(
                        session,
                        "Late result",
                        "late.txt",
                        "text/plain",
                        "content".getBytes(StandardCharsets.UTF_8),
                        UUID.randomUUID())
                .body(), "sourceId"));
        UUID actorId = jdbc.queryForObject(
                "SELECT created_by_user_id FROM ai.document_sources WHERE id = ?", UUID.class, sourceId);
        String storageKey = jdbc.queryForObject(
                "SELECT storage_key FROM ai.document_sources WHERE id = ?", String.class, sourceId);
        String sha256 = jdbc.queryForObject(
                "SELECT sha256 FROM ai.document_sources WHERE id = ?", String.class, sourceId).trim();
        Instant claimedAt = Instant.now();
        UUID jobId = UUID.randomUUID();
        jdbc.update(
                "UPDATE ai.document_sources SET status = 'INDEXING', version = 1, updated_at = ? WHERE id = ?",
                Timestamp.from(claimedAt),
                sourceId);
        jdbc.update(
                """
                INSERT INTO ai.source_indexing_jobs
                    (id, source_id, status, requested_by_user_id, correlation_id, created_at,
                     claimed_at, claim_expires_at)
                VALUES (?, ?, 'CLAIMED', ?, ?, ?, ?, ?)
                """,
                jobId,
                sourceId,
                actorId,
                UUID.randomUUID(),
                Timestamp.from(claimedAt),
                Timestamp.from(claimedAt),
                Timestamp.from(claimedAt.plus(Duration.ofMinutes(1))));
        AiIndexingPersistence.ClaimedJob job = new AiIndexingPersistence.ClaimedJob(
                jobId, sourceId, actorId, UUID.randomUUID(), storageKey, 7, sha256);

        boolean published = indexingPersistence.complete(
                job,
                List.of(new AiIndexingPersistence.IndexedChunk(
                        0, "content", sha256, 1, 0, 1, embeddings(List.of("content")).getFirst())),
                claimedAt.plus(Duration.ofMinutes(2)));

        assertThat(published).isFalse();
        assertThat(jdbc.queryForMap(
                        "SELECT status, version FROM ai.document_sources WHERE id = ?", sourceId))
                .containsEntry("status", "INDEXING")
                .containsEntry("version", 1L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.source_chunks WHERE source_id = ?", Integer.class, sourceId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.outbox_events WHERE resource_id = ?", Integer.class, sourceId))
                .isEqualTo(1);
    }

    private Session login() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return new Session(
                response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0],
                extract(response.body(), "csrfToken"));
    }

    private HttpResponse<String> get(Session session, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", session.cookie())
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> upload(
            Session session,
            String name,
            String filename,
            String mediaType,
            byte[] content,
            UUID correlationId) throws Exception {
        String boundary = "JAXON-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"name\"\r\n\r\n");
        write(body, name + "\r\n");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        write(body, "Content-Type: " + mediaType + "\r\n\r\n");
        body.write(content);
        write(body, "\r\n--" + boundary + "--\r\n");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ai/sources"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("X-Correlation-ID", correlationId.toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> uploadWithExtraPart(Session session) throws Exception {
        String boundary = "JAXON-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"name\"\r\n\r\nClosed request\r\n");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"source.txt\"\r\n");
        write(body, "Content-Type: text/plain\r\n\r\nvalid\r\n");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"unexpected\"\r\n\r\nvalue\r\n");
        write(body, "--" + boundary + "--\r\n");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ai/sources"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> index(
            Session session, UUID sourceId, long expectedVersion, UUID correlationId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/ai/sources/" + sourceId + "/index"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("X-Correlation-ID", correlationId.toString())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":" + expectedVersion + "}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void awaitStatus(UUID sourceId, String expectedStatus) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM ai.document_sources WHERE id = ?", String.class, sourceId);
            if (expectedStatus.equals(status)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Source did not reach " + expectedStatus);
    }

    private void assertLifecycleEvents(
            UUID sourceId, UUID correlationId, String terminalType, String terminalResult, String terminalStatus) {
        List<Map<String, Object>> lifecycle = jdbc.queryForList(
                """
                SELECT event_type, event_version, actor_type, actor_id, resource_version,
                       result, correlation_id, payload::text AS payload
                FROM ai.outbox_events
                WHERE resource_id = ? AND event_type <> 'AI_SOURCE_REGISTERED'
                ORDER BY occurred_at, event_type
                """,
                sourceId);
        assertThat(lifecycle).hasSize(2);
        assertThat(lifecycle.getFirst())
                .containsEntry("event_type", "AI_SOURCE_INDEXING_REQUESTED")
                .containsEntry("event_version", "2.0")
                .containsEntry("actor_type", "HUMAN_USER")
                .doesNotContainEntry("actor_id", null)
                .containsEntry("resource_version", 1L)
                .containsEntry("result", "SUCCESS")
                .containsEntry("correlation_id", correlationId)
                .containsEntry("payload", "{\"status\": \"INDEXING\"}");
        assertThat(lifecycle.getLast())
                .containsEntry("event_type", terminalType)
                .containsEntry("event_version", "2.0")
                .containsEntry("actor_type", "SYSTEM")
                .containsEntry("actor_id", null)
                .containsEntry("resource_version", 2L)
                .containsEntry("result", terminalResult)
                .containsEntry("correlation_id", correlationId)
                .containsEntry("payload", "{\"status\": \"" + terminalStatus + "\"}");
    }

    private static EmbeddingProvider.TokenSequence tokenize(String content) {
        Matcher matcher = Pattern.compile("\\S+").matcher(content);
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        java.util.ArrayList<EmbeddingProvider.TokenSpan> spans = new java.util.ArrayList<>();
        while (matcher.find()) {
            ids.add((long) ids.size() + 1);
            spans.add(new EmbeddingProvider.TokenSpan(matcher.start(), matcher.end()));
        }
        long[] tokenIds = new long[ids.size()];
        for (int index = 0; index < ids.size(); index++) tokenIds[index] = ids.get(index);
        return new EmbeddingProvider.TokenSequence(tokenIds, spans);
    }

    private static List<float[]> embeddings(List<String> contents) {
        return contents.stream().map(content -> {
            float[] embedding = new float[EmbeddingProvider.DIMENSIONS];
            embedding[0] = 1.0f;
            return embedding;
        }).toList();
    }

    private static String numberedWords(int count) {
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) content.append(' ');
            content.append("palabra").append(index);
        }
        return content.toString();
    }

    private static void write(ByteArrayOutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String extract(String body, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        if (!matcher.find()) throw new AssertionError("Missing " + field + " in " + body);
        return matcher.group(1);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("jaxon-ai-sources-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Session(String cookie, String csrfToken) {}
}
