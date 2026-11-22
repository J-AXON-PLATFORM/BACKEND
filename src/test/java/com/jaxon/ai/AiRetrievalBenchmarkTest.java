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
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Local representative measurement of the retrieval objective (p95 <= 500 ms).
 *
 * <p>Disabled unless {@code -Djaxon.ai.retrieval.benchmark=true} and the external model and
 * tokenizer paths are supplied. It uses the real local ONNX provider, PostgreSQL/pgvector and the
 * HTTP endpoint; model weights are never part of the repository.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "jaxon.ai.retrieval.benchmark", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Benchmark.Admin",
            "jaxon.identity.bootstrap.password=AI retrieval benchmark passphrase 42!"
        })
class AiRetrievalBenchmarkTest {

    private static final String[] QUERIES = {
        "procedimiento de respaldo de la base de datos",
        "cómo restablecer una contraseña de usuario",
        "configuración de la impresora de red",
        "política de retención de documentos",
        "reinicio seguro del servidor de aplicaciones",
        "network troubleshooting checklist",
        "instalación del agente en un equipo nuevo",
        "límites de almacenamiento del buzón SFTP"
    };

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
    EmbeddingProvider provider;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Path storage = Files.createTempDirectory("jaxon-ai-benchmark-");
        registry.add("jaxon.ai.document-storage.root", storage::toString);
        registry.add("jaxon.ai.embedding.model-path", () -> System.getProperty("jaxon.ai.embedding.model-path"));
        registry.add(
                "jaxon.ai.embedding.tokenizer-path", () -> System.getProperty("jaxon.ai.embedding.tokenizer-path"));
    }

    @Test
    void measuresEndToEndRetrievalLatency() throws Exception {
        assertThat(provider.isAvailable()).isTrue();
        int sources = Integer.getInteger("jaxon.ai.retrieval.benchmark.sources", 2000);
        int chunksPerSource = Integer.getInteger("jaxon.ai.retrieval.benchmark.chunks-per-source", 10);
        int iterations = Integer.getInteger("jaxon.ai.retrieval.benchmark.iterations", 200);
        seed(sources, chunksPerSource);
        jdbc.execute("VACUUM ANALYZE ai.source_chunks");
        jdbc.execute("VACUUM ANALYZE ai.document_sources");
        Session session = login();

        for (int warmup = 0; warmup < 20; warmup++) {
            assertThat(post(session, QUERIES[warmup % QUERIES.length]).statusCode()).isEqualTo(200);
        }
        long[] durations = new long[iterations];
        for (int index = 0; index < iterations; index++) {
            long started = System.nanoTime();
            HttpResponse<String> response = post(session, QUERIES[index % QUERIES.length] + " " + index);
            durations[index] = System.nanoTime() - started;
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"limit\":10");
        }
        Arrays.sort(durations);
        double p50 = millis(durations[(int) Math.ceil(0.50 * iterations) - 1]);
        double p95 = millis(durations[(int) Math.ceil(0.95 * iterations) - 1]);
        double max = millis(durations[iterations - 1]);
        System.out.printf(
                "AI_RETRIEVAL_BENCHMARK sources=%d chunks=%d iterations=%d p50_ms=%.1f p95_ms=%.1f max_ms=%.1f cpus=%d%n",
                sources,
                sources * chunksPerSource,
                iterations,
                p50,
                p95,
                max,
                Runtime.getRuntime().availableProcessors());
        assertThat(p95).isLessThanOrEqualTo(500.0);
    }

    private void seed(int sources, int chunksPerSource) {
        Random random = new Random(20261121L);
        Instant created = Instant.parse("2026-11-20T10:00:00Z");
        String content = "contenido documental de referencia ".repeat(60).strip();
        List<Object[]> sourceRows = new ArrayList<>();
        List<Object[]> chunkRows = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < sources; sourceIndex++) {
            UUID sourceId = UUID.randomUUID();
            sourceRows.add(new Object[] {
                sourceId, "Fuente " + sourceIndex, "a".repeat(64), UUID.randomUUID() + ".txt",
                Timestamp.from(created), Timestamp.from(created.plusSeconds(1))
            });
            for (int ordinal = 0; ordinal < chunksPerSource; ordinal++) {
                chunkRows.add(new Object[] {
                    UUID.randomUUID(), sourceId, ordinal, content + " " + ordinal,
                    String.format("%064x", ordinal + 1L), ordinal * 408, ordinal * 408 + 480,
                    AiIndexingPersistence.vectorLiteral(randomUnit(random)),
                    EmbeddingProvider.MODEL_ID, EmbeddingProvider.MODEL_REVISION,
                    EmbeddingProvider.VOCABULARY_DIGEST, AiSourceChunker.VERSION
                });
            }
        }
        jdbc.batchUpdate(
                """
                INSERT INTO ai.document_sources
                    (id, name, media_type, size_bytes, sha256, storage_key, status,
                     created_by_user_id, created_at, updated_at, version)
                VALUES (?, ?, 'text/plain', 10, ?, ?, 'READY',
                        '6f0f2f4e-1d4a-4c3b-9a5e-2b7c8d9e0f11', ?, ?, 2)
                """,
                sourceRows);
        jdbc.batchUpdate(
                """
                INSERT INTO ai.source_chunks
                    (id, source_id, ordinal, content, content_sha256, token_count, start_token,
                     end_token, embedding, model_id, model_revision, tokenizer_sha256,
                     chunker_version, created_at)
                VALUES (?, ?, ?, ?, ?, 480, ?, ?, ?::vector, ?, ?, ?, ?, now())
                """,
                chunkRows);
    }

    private Session login() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"Benchmark.Admin\",\"password\":\"AI retrieval benchmark passphrase 42!\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher matcher = Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return new Session(
                response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0], matcher.group(1));
    }

    private HttpResponse<String> post(Session session, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ai/retrieval"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"" + query + "\",\"limit\":10}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
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

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record Session(String cookie, String csrfToken) {}
}
