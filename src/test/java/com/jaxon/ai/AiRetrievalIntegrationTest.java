package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
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
            "jaxon.identity.bootstrap.username=Retrieval.Admin",
            "jaxon.identity.bootstrap.password=AI retrieval test passphrase 42!"
        })
class AiRetrievalIntegrationTest {

    private static final String USERNAME = "Retrieval.Admin";
    private static final String PASSWORD = "AI retrieval test passphrase 42!";
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID CREATOR = UUID.fromString("6f0f2f4e-1d4a-4c3b-9a5e-2b7c8d9e0f11");

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
        Files.deleteIfExists(STORAGE_ROOT);
    }

    @BeforeEach
    void resetState() {
        reset(embeddingProvider);
        when(embeddingProvider.isAvailable()).thenReturn(true);
        when(embeddingProvider.tokenize(anyString())).thenAnswer(invocation -> tokens(invocation.getArgument(0)));
        when(embeddingProvider.embedQuery(anyString())).thenReturn(axis(0));
        jdbc.update("DELETE FROM ai.outbox_events");
        jdbc.update("DELETE FROM ai.source_chunks");
        jdbc.update("DELETE FROM ai.source_indexing_jobs");
        jdbc.update("DELETE FROM ai.document_sources");
    }

    @AfterEach
    void restoreAdministratorRole() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'SYSTEM_ADMIN' FROM identity.human_users
                WHERE normalized_username = 'retrieval.admin'
                ON CONFLICT DO NOTHING
                """);
    }

    @Test
    void deniesUnauthenticatedUnauthorizedAndCsrfInvalidRequestsBeforeEmbedding() throws Exception {
        seedReadySource("Visible", List.of(axis(0)));

        HttpRequest anonymous = HttpRequest.newBuilder(uri())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"red\"}"))
                .build();
        HttpResponse<String> unauthenticated = http.send(anonymous, HttpResponse.BodyHandlers.ofString());
        assertThat(unauthenticated.statusCode()).isEqualTo(401);

        Session session = login();
        HttpResponse<String> withoutCsrf = post(session, "{\"query\":\"red\"}", false);
        assertThat(withoutCsrf.statusCode()).isEqualTo(403);
        assertThat(withoutCsrf.body()).contains("CSRF_INVALID").doesNotContain("Visible");

        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'SYSTEM_ADMIN'");
        assertThat(jdbc.queryForList(
                        """
                        SELECT r.role_code FROM identity.human_user_roles r
                        JOIN identity.human_users u ON u.id = r.user_id
                        WHERE u.normalized_username = 'retrieval.admin'
                        """,
                        String.class))
                .containsExactly("IT_OPERATOR");
        Session operator = login();
        HttpResponse<String> forbidden = post(operator, "{\"query\":\"red\"}", true);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("ACCESS_DENIED").doesNotContain("Visible");

        verify(embeddingProvider, never()).embedQuery(anyString());
        verify(embeddingProvider, never()).tokenize(anyString());
    }

    @Test
    void rejectsInvalidRequestsWithClosedProblemsAndNoEmbedding() throws Exception {
        Session session = login();
        String tooLong = "a".repeat(2001);
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("{\"query\":\"red\",\"extra\":1}", "UNKNOWN_FIELD"),
                Map.entry("{}", "INVALID_FIELD"),
                Map.entry("{\"query\":null}", "INVALID_FIELD"),
                Map.entry("{\"query\":42}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"   \\n\\t \"}", "BLANK"),
                Map.entry("{\"query\":\"" + tooLong + "\"}", "TOO_LONG"),
                Map.entry("{\"query\":\"red\",\"limit\":0}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"red\",\"limit\":11}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"red\",\"limit\":1.5}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"red\",\"limit\":\"5\"}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"red\",\"limit\":null}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"red\",\"limit\":1e2}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"red\",\"limit\":5.0}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"red\",\"limit\":99999999999999999999}", "INVALID_FIELD"),
                Map.entry("{\"query\":\"\\u00A0\\uFEFF\\u001F\"}", "BLANK"),
                Map.entry("{\"query\":\"\\u200B\\u2060\"}", "BLANK"),
                Map.entry("[\"red\"]", "INVALID_FIELD"));
        for (Map.Entry<String, String> entry : invalid.entrySet()) {
            HttpResponse<String> response = post(session, entry.getKey(), true);
            assertThat(response.statusCode()).as(entry.getKey()).isEqualTo(422);
            assertThat(response.headers().firstValue("content-type").orElseThrow())
                    .startsWith("application/problem+json");
            JsonNode problem = JSON.readTree(response.body());
            assertThat(problem.path("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(problem.path("errors").get(0).path("code").asString()).isEqualTo(entry.getValue());
            assertThat(response.body()).doesNotContain("extra");
        }
        for (String malformed : List.of(
                "{\"query\":",
                "",
                "{\"query\":\"a\",\"query\":\"b\"}",
                "{\"query\":\"x\",\"limit\":1,\"limit\":10}",
                "{\"query\":\"red\"} {}")) {
            HttpResponse<String> response = post(session, malformed, true);
            assertThat(response.statusCode()).as(malformed).isEqualTo(400);
            assertThat(response.body()).contains("MALFORMED_REQUEST");
        }
        HttpRequest withoutContentType = HttpRequest.newBuilder(uri())
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"red\"}"))
                .build();
        HttpResponse<String> noContentType = http.send(withoutContentType, HttpResponse.BodyHandlers.ofString());
        assertThat(noContentType.statusCode()).isEqualTo(400);
        assertThat(noContentType.body()).contains("MALFORMED_REQUEST");
        verify(embeddingProvider, never()).embedQuery(anyString());

        String exact = "b".repeat(2000);
        assertThat(post(session, "{\"query\":\"" + exact + "\"}", true).statusCode()).isEqualTo(200);

        StringBuilder tooManyTokens = new StringBuilder();
        for (int index = 0; index < 481; index++) tooManyTokens.append("t ");
        HttpResponse<String> tokens = post(session, "{\"query\":\"" + tooManyTokens + "\"}", true);
        assertThat(tokens.statusCode()).isEqualTo(422);
        assertThat(tokens.body()).contains("TOO_MANY_TOKENS");

        when(embeddingProvider.tokenize("sin-tokens")).thenReturn(
                new EmbeddingProvider.TokenSequence(new long[0], List.of()));
        HttpResponse<String> noTokens = post(session, "{\"query\":\"sin-tokens\"}", true);
        assertThat(noTokens.statusCode()).isEqualTo(422);
        assertThat(noTokens.body()).contains("BLANK");
        verify(embeddingProvider, never()).embedQuery("sin-tokens");
    }

    @Test
    void returnsEmptyResultsWithoutFabricatingEvidence() throws Exception {
        seedSource("Pending", "PENDING", List.of(axis(0)));
        Session session = login();

        HttpResponse<String> response = post(session, "{\"query\":\"sin evidencia\"}", true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).toString()).isEqualTo("{\"limit\":5,\"results\":[]}");
        verify(embeddingProvider).embedQuery("sin evidencia");
    }

    @Test
    void retrievesOnlyReadySourcesOfTheCurrentModelWithoutLeakingStorageDetails() throws Exception {
        UUID ready = seedReadySource("Manual autorizado", List.of(axis(0), axis(1)));
        for (String status : List.of("PENDING", "INDEXING", "FAILED", "DISABLED")) {
            seedSource("Fuente " + status, status, List.of(axis(0)));
        }
        UUID otherModel = seedReadySource("Otro modelo", List.of());
        insertChunk(otherModel, 0, axis(0), "other/model", EmbeddingProvider.MODEL_REVISION,
                EmbeddingProvider.VOCABULARY_DIGEST);
        insertChunk(otherModel, 1, axis(0), EmbeddingProvider.MODEL_ID, "0".repeat(40),
                EmbeddingProvider.VOCABULARY_DIGEST);
        insertChunk(otherModel, 2, axis(0), EmbeddingProvider.MODEL_ID, EmbeddingProvider.MODEL_REVISION,
                "f".repeat(64));
        UUID zero = seedReadySource("Vector nulo", List.of(new float[EmbeddingProvider.DIMENSIONS]));
        String storageKey = jdbc.queryForObject(
                "SELECT storage_key FROM ai.document_sources WHERE id = ?", String.class, ready);
        Session session = login();

        HttpResponse<String> response = post(session, "{\"query\":\"  manual  \",\"limit\":10}", true);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("limit").asInt()).isEqualTo(10);
        assertThat(body.path("results")).hasSize(2);
        for (JsonNode result : body.path("results")) {
            assertThat(result.path("sourceId").asString()).isEqualTo(ready.toString());
            assertThat(result.path("sourceName").asString()).isEqualTo("Manual autorizado");
            assertThat(result.propertyNames()).containsExactly(
                    "sourceId", "sourceName", "sourceSha256", "chunkId", "ordinal", "startToken",
                    "endToken", "contentSha256", "content", "similarity");
            String chunkSha = jdbc.queryForObject(
                    "SELECT content_sha256 FROM ai.source_chunks WHERE id = ?::uuid",
                    String.class,
                    result.path("chunkId").asString());
            assertThat(result.path("contentSha256").asString()).isEqualTo(chunkSha.trim());
        }
        assertThat(body.path("results").get(0).path("similarity").asDouble()).isCloseTo(1.0, within(1e-6));
        assertThat(body.path("results").get(1).path("similarity").asDouble()).isCloseTo(0.0, within(1e-6));
        assertThat(response.body()).doesNotContain(
                storageKey, "storage", "embedding", STORAGE_ROOT.toString(), otherModel.toString(),
                zero.toString(), "NaN");
        verify(embeddingProvider).embedQuery("manual");
    }

    @Test
    void ranksDeterministicallyBySimilarityThenSourceThenOrdinal() throws Exception {
        UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID second = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        seedReadySource(second, "Segunda", List.of(axis(0), mix(0.8, 0.6)));
        seedReadySource(first, "Primera", List.of(mix(0.6, 0.8), axis(0), axis(0)));
        Session session = login();

        HttpResponse<String> response = post(session, "{\"query\":\"orden\"}", true);
        HttpResponse<String> repeated = post(session, "{\"query\":\"orden\"}", true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(repeated.body()).isEqualTo(response.body());
        JsonNode results = JSON.readTree(response.body()).path("results");
        assertThat(results).hasSize(5);
        List<String> order = new ArrayList<>();
        results.forEach(result -> order.add(
                result.path("sourceName").asString() + "#" + result.path("ordinal").asInt()));
        assertThat(order).containsExactly(
                "Primera#1", "Primera#2", "Segunda#0", "Segunda#1", "Primera#0");
        assertThat(results.get(3).path("similarity").asDouble()).isCloseTo(0.8, within(1e-5));
        assertThat(results.get(4).path("similarity").asDouble()).isCloseTo(0.6, within(1e-5));

        HttpResponse<String> limited = post(session, "{\"query\":\"orden\",\"limit\":1}", true);
        assertThat(JSON.readTree(limited.body()).path("results")).hasSize(1);
        assertThat(JSON.readTree(limited.body()).at("/results/0/ordinal").asInt()).isEqualTo(1);
    }

    @Test
    void matchesAnExactBruteForceRankingAcrossManyChunks() throws Exception {
        Random random = new Random(42);
        List<float[]> stored = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < 30; sourceIndex++) {
            List<float[]> vectors = new ArrayList<>();
            for (int chunk = 0; chunk < 10; chunk++) vectors.add(randomUnit(random));
            UUID sourceId = seedReadySource("Fuente " + sourceIndex, vectors);
            for (int chunk = 0; chunk < vectors.size(); chunk++) {
                stored.add(vectors.get(chunk));
                keys.add(sourceId + "#" + chunk);
            }
        }
        float[] query = randomUnit(random);
        when(embeddingProvider.embedQuery(anyString())).thenReturn(query);
        List<Integer> expected = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) expected.add(index);
        expected.sort(Comparator.comparingDouble((Integer index) -> -dot(stored.get(index), query))
                .thenComparing(keys::get));
        Session session = login();

        JsonNode results = JSON.readTree(post(session, "{\"query\":\"exacta\",\"limit\":10}", true).body())
                .path("results");

        assertThat(results).hasSize(10);
        for (int rank = 0; rank < 10; rank++) {
            JsonNode result = results.get(rank);
            assertThat(result.path("sourceId").asString() + "#" + result.path("ordinal").asInt())
                    .isEqualTo(keys.get(expected.get(rank)));
            assertThat(result.path("similarity").asDouble())
                    .isCloseTo(dot(stored.get(expected.get(rank)), query), within(1e-5));
        }
    }

    @Test
    void failsClosedWhenTheEmbeddingProviderIsUnavailableWithoutMutations() throws Exception {
        seedReadySource("Visible", List.of(axis(0)));
        long sources = count("ai.document_sources");
        long chunks = count("ai.source_chunks");
        Session session = login();

        when(embeddingProvider.isAvailable()).thenReturn(false);
        HttpResponse<String> unavailable = post(session, "{\"query\":\"red\"}", true);
        assertThat(unavailable.statusCode()).isEqualTo(503);
        assertThat(unavailable.body()).contains("AI_RETRIEVAL_UNAVAILABLE").doesNotContain("Visible");
        verify(embeddingProvider, never()).embedQuery(anyString());

        when(embeddingProvider.isAvailable()).thenReturn(true);
        when(embeddingProvider.embedQuery(anyString())).thenThrow(new EmbeddingProviderUnavailableException());
        HttpResponse<String> failed = post(session, "{\"query\":\"red\"}", true);
        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(failed.body()).contains("AI_RETRIEVAL_UNAVAILABLE").doesNotContain("Visible");

        doReturn(new float[] {Float.NaN}).when(embeddingProvider).embedQuery(anyString());
        assertThat(post(session, "{\"query\":\"red\"}", true).statusCode()).isEqualTo(503);

        assertThat(count("ai.document_sources")).isEqualTo(sources);
        assertThat(count("ai.source_chunks")).isEqualTo(chunks);
        assertThat(count("ai.outbox_events")).isZero();
        assertThat(count("ai.source_indexing_jobs")).isZero();
    }

    @Test
    void successfulRetrievalIsReadOnly() throws Exception {
        seedReadySource("Visible", List.of(axis(0)));
        Session session = login();

        assertThat(post(session, "{\"query\":\"red\"}", true).statusCode()).isEqualTo(200);

        assertThat(count("ai.outbox_events")).isZero();
        assertThat(count("ai.source_indexing_jobs")).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT status || ':' || version FROM ai.document_sources", String.class))
                .isEqualTo("READY:2");
        verify(embeddingProvider, never()).embedPassages(any());
    }

    private UUID seedReadySource(String name, List<float[]> vectors) {
        return seedReadySource(UUID.randomUUID(), name, vectors);
    }

    private UUID seedReadySource(UUID sourceId, String name, List<float[]> vectors) {
        return seedSource(sourceId, name, "READY", vectors);
    }

    private UUID seedSource(String name, String status, List<float[]> vectors) {
        return seedSource(UUID.randomUUID(), name, status, vectors);
    }

    private UUID seedSource(UUID sourceId, String name, String status, List<float[]> vectors) {
        Instant created = Instant.parse("2026-11-20T10:00:00Z");
        jdbc.update(
                """
                INSERT INTO ai.document_sources
                    (id, name, media_type, size_bytes, sha256, storage_key, status,
                     created_by_user_id, created_at, updated_at, version)
                VALUES (?, ?, 'text/plain', 10, ?, ?, ?, ?, ?, ?, 2)
                """,
                sourceId,
                name,
                "a".repeat(64),
                UUID.randomUUID() + ".txt",
                status,
                CREATOR,
                Timestamp.from(created),
                Timestamp.from(created.plusSeconds(1)));
        for (int ordinal = 0; ordinal < vectors.size(); ordinal++) {
            insertChunk(sourceId, ordinal, vectors.get(ordinal), EmbeddingProvider.MODEL_ID,
                    EmbeddingProvider.MODEL_REVISION, EmbeddingProvider.VOCABULARY_DIGEST);
        }
        return sourceId;
    }

    private void insertChunk(
            UUID sourceId, int ordinal, float[] vector, String modelId, String revision, String tokenizer) {
        jdbc.update(
                """
                INSERT INTO ai.source_chunks
                    (id, source_id, ordinal, content, content_sha256, token_count, start_token,
                     end_token, embedding, model_id, model_revision, tokenizer_sha256,
                     chunker_version, created_at)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?::vector, ?, ?, ?, ?, now())
                """,
                UUID.randomUUID(),
                sourceId,
                ordinal,
                "fragmento " + ordinal,
                String.format("%064x", ordinal + 1),
                ordinal,
                ordinal + 1,
                AiIndexingPersistence.vectorLiteral(vector),
                modelId,
                revision,
                tokenizer,
                AiSourceChunker.VERSION);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
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
        return new Session(
                response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0],
                matcher.group(1));
    }

    private HttpResponse<String> post(Session session, String body, boolean csrf) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri())
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrf) builder.header("X-CSRF-Token", session.csrfToken());
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri() {
        return URI.create("http://localhost:" + port + "/api/v1/ai/retrieval");
    }

    private static EmbeddingProvider.TokenSequence tokens(String text) {
        Matcher matcher = Pattern.compile("\\S+").matcher(text);
        List<Long> ids = new ArrayList<>();
        List<EmbeddingProvider.TokenSpan> spans = new ArrayList<>();
        while (matcher.find()) {
            ids.add((long) ids.size() + 1);
            spans.add(new EmbeddingProvider.TokenSpan(matcher.start(), matcher.end()));
        }
        long[] values = ids.stream().mapToLong(Long::longValue).toArray();
        return new EmbeddingProvider.TokenSequence(values, spans);
    }

    private static float[] axis(int index) {
        float[] vector = new float[EmbeddingProvider.DIMENSIONS];
        vector[index] = 1.0f;
        return vector;
    }

    private static float[] mix(double first, double second) {
        float[] vector = new float[EmbeddingProvider.DIMENSIONS];
        vector[0] = (float) first;
        vector[1] = (float) second;
        return vector;
    }

    private static float[] randomUnit(Random random) {
        float[] vector = new float[EmbeddingProvider.DIMENSIONS];
        double norm = 0.0;
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) random.nextGaussian();
            norm += vector[index] * vector[index];
        }
        for (int index = 0; index < vector.length; index++) vector[index] /= (float) Math.sqrt(norm);
        return vector;
    }

    private static double dot(float[] left, float[] right) {
        double sum = 0.0;
        for (int index = 0; index < left.length; index++) sum += left[index] * right[index];
        return sum;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("jaxon-ai-retrieval-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Session(String cookie, String csrfToken) {}
}
