package com.jaxon;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

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

    @Test
    void exposesOnlyNonSensitiveOperationalHealth() throws Exception {
        assertHealthy("/actuator/health");
        assertHealthy("/actuator/health/liveness");
        assertHealthy("/actuator/health/readiness");
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

    private void assertHealthy(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"UP\"")
                .doesNotContain("components", "details", "database", "jdbc", "password");
    }
}
