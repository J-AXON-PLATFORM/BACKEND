package com.jaxon;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.slf4j.MDC;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthReadinessIntegrationTest {

    private static final Set<String> EXPECTED_SCHEMAS = Set.of(
            "inventory",
            "discovery",
            "collector",
            "support",
            "ai",
            "identity",
            "automation",
            "audit_reporting");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Environment environment;

    @Autowired
    OpenTelemetry openTelemetry;

    @Autowired
    Tracer tracer;

    @Test
    void exposesOnlyNonSensitiveOperationalHealth() throws Exception {
        assertHealthy("/actuator/health");
        assertHealthy("/actuator/health/liveness");
        assertHealthy("/actuator/health/readiness");
    }

    @Test
    void returnsApprovedCorrelationIdForRealHttpRequests() throws Exception {
        HttpResponse<String> generated = get("/actuator/health");
        String generatedId = generated.headers().firstValue(CorrelationIdFilter.HEADER_NAME).orElseThrow();
        assertThat(UUID.fromString(generatedId).version()).isEqualTo(4);

        String expected = "4cfd0cf9-8742-4a83-b3a8-d3f051953ced";
        HttpResponse<String> preserved = get("/actuator/health", expected);
        assertThat(preserved.headers().firstValue(CorrelationIdFilter.HEADER_NAME))
                .contains(expected);

        HttpResponse<String> replaced = get("/actuator/health", "unsafe-value");
        String replacement = replaced.headers().firstValue(CorrelationIdFilter.HEADER_NAME).orElseThrow();
        assertThat(UUID.fromString(replacement).version()).isEqualTo(4);
        assertThat(replacement).isNotEqualTo("unsafe-value");
    }

    @Test
    void startsWithPostgreSqlPgvectorFlywayAndApprovedSchemas() {
        String serverVersion = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        String vectorVersion = jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                String.class);
        List<String> schemas = jdbcTemplate.queryForList(
                """
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name IN ('inventory', 'discovery', 'collector', 'support',
                                      'ai', 'identity', 'automation', 'audit_reporting')
                ORDER BY schema_name
                """,
                String.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM public.flyway_schema_history
                WHERE version = '20261006000000' AND success
                """,
                Integer.class);

        assertThat(serverVersion).startsWith("18.6");
        assertThat(vectorVersion).isEqualTo("0.8.6");
        assertThat(schemas).containsExactlyInAnyOrderElementsOf(EXPECTED_SCHEMAS);
        assertThat(migrationCount).isEqualTo(1);
    }

    @Test
    void exposesPrometheusWithStandardLowCardinalityMetricsOnly() throws Exception {
        get("/actuator/health");
        HttpResponse<String> response = get("/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("jvm_memory_used_bytes")
                .contains("http_server_requests_seconds")
                .contains("hikaricp_connections")
                .doesNotContain(
                        "userId=", "assetId=", "taskId=", "messageId=", "correlationId=");
        assertMetricHasCommonTags(response.body(), "jvm_memory_used_bytes");
        assertMetricHasCommonTags(response.body(), "http_server_requests_seconds_count");
        assertMetricHasCommonTags(response.body(), "hikaricp_connections");
    }

    @Test
    void doesNotExposeSensitiveActuatorEndpoints() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/metrics", "/actuator/loggers")) {
            assertThat(get(path).statusCode()).as(path).isEqualTo(404);
        }
    }

    @Test
    void configuresW3cPropagationSamplingAndOptionalOtlpExport() {
        assertThat(openTelemetry.getPropagators().getTextMapPropagator().fields())
                .contains("traceparent", "tracestate");
        assertThat(environment.getProperty("management.tracing.propagation.type"))
                .isEqualTo("w3c");
        assertThat(environment.getProperty("management.tracing.sampling.probability", Double.class))
                .isEqualTo(0.10d);
        assertThat(environment.getProperty("management.tracing.export.otlp.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("management.logging.export.otlp.enabled", Boolean.class))
                .isFalse();
    }

    @Test
    void makesTraceAndSpanIdsAvailableInMdcWhenSpanExists() {
        Span span = tracer.nextSpan().name("observability-test").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            assertThat(MDC.get("traceId")).isEqualTo(span.context().traceId());
            assertThat(MDC.get("spanId")).isEqualTo(span.context().spanId());
        } finally {
            span.end();
        }
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    private void assertHealthy(String path) throws Exception {
        HttpResponse<String> response = get(path);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"UP\"")
                .doesNotContain("components", "details", "database", "jdbc", "password");
    }

    private static void assertMetricHasCommonTags(String body, String metricName) {
        String sample = body.lines()
                .filter(line -> line.startsWith(metricName + "{"))
                .findFirst()
                .orElseThrow();
        assertThat(sample)
                .contains("application=\"jaxon-backend\"")
                .contains("environment=\"local\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build());
    }

    private HttpResponse<String> get(String path, String correlationId) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header(CorrelationIdFilter.HEADER_NAME, correlationId)
                .GET()
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
