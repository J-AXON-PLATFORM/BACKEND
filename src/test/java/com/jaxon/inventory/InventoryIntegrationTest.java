package com.jaxon.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;
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
            "jaxon.identity.bootstrap.username=Inventory.Operator",
            "jaxon.identity.bootstrap.password=Inventory test passphrase 42!"
        })
class InventoryIntegrationTest {

    private static final String USERNAME = "Inventory.Operator";
    private static final String PASSWORD = "Inventory test passphrase 42!";
    private static final String VALID_ASSET =
            "{\"name\":\"Workstation 042\",\"assetType\":\"DESKTOP\",\"status\":\"ACTIVE\"}";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsPersistsAndRetrievesTheOfficialAssetWithItsDurableEvent() throws Exception {
        Session session = login();
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> created = postAsset(session.cookie(), session.csrfToken(), VALID_ASSET, correlationId);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("location")).isPresent();
        assertThat(created.body())
                .contains("\"name\":\"Workstation 042\"")
                .contains("\"assetType\":\"DESKTOP\"")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"version\":0")
                .contains("\"createdAt\"")
                .contains("\"updatedAt\"");
        UUID assetId = UUID.fromString(extract(created.body(), "assetId"));
        assertThat(assetId.version()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.assets WHERE id = ?", Integer.class, assetId))
                .isEqualTo(1);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, event_version, producer_module, actor_type, resource_type,
                               resource_id, resource_version, result, correlation_id, payload::text AS payload
                        FROM inventory.outbox_events
                        WHERE resource_id = ?
                        """,
                        assetId))
                .containsEntry("event_type", "ASSET_CREATED")
                .containsEntry("event_version", "2.0")
                .containsEntry("producer_module", "INVENTORY")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("resource_type", "ASSET")
                .containsEntry("resource_id", assetId)
                .containsEntry("resource_version", 0L)
                .containsEntry("result", "SUCCESS")
                .containsEntry("correlation_id", correlationId)
                .containsEntry("payload", "{}");

        HttpResponse<String> retrieved = getAsset(session.cookie(), assetId);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(retrieved.body()).isEqualTo(created.body());
    }

    @Test
    void rejectsUnknownAssetTypesBeforePersisting() throws Exception {
        Session session = login();
        int assetsBefore = jdbc.queryForObject("SELECT count(*) FROM inventory.assets", Integer.class);
        int eventsBefore = jdbc.queryForObject("SELECT count(*) FROM inventory.outbox_events", Integer.class);

        HttpResponse<String> rejected = postAsset(
                session.cookie(),
                session.csrfToken(),
                "{\"name\":\"Unsupported\",\"assetType\":\"ROUTER\",\"status\":\"ACTIVE\"}",
                UUID.randomUUID());

        assertThat(rejected.statusCode()).isEqualTo(422);
        assertThat(rejected.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(rejected.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.assets", Integer.class))
                .isEqualTo(assetsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.outbox_events", Integer.class))
                .isEqualTo(eventsBefore);

        HttpResponse<String> invalidFields = postAsset(
                session.cookie(),
                session.csrfToken(),
                "{\"name\":\" \",\"assetType\":\"DESKTOP\",\"status\":\"ACTIVE\"}",
                UUID.randomUUID());
        assertThat(invalidFields.statusCode()).isEqualTo(422);
        assertThat(invalidFields.body())
                .contains("\"code\":\"VALIDATION_FAILED\"")
                .contains("\"pointer\":\"/name\"");
    }

    @Test
    void requiresAuthenticationCsrfAndTheExplicitItOperatorRole() throws Exception {
        assertThat(getAsset(null, UUID.randomUUID()).statusCode()).isEqualTo(401);

        Session session = login();
        HttpResponse<String> missingCsrf = postAsset(session.cookie(), null, VALID_ASSET, UUID.randomUUID());
        assertThat(missingCsrf.statusCode()).isEqualTo(403);
        assertThat(missingCsrf.body()).contains("\"code\":\"CSRF_INVALID\"");

        jdbc.update(
                """
                DELETE FROM identity.human_user_roles
                WHERE role_code = 'IT_OPERATOR'
                  AND user_id = (SELECT id FROM identity.human_users WHERE normalized_username = 'inventory.operator')
                """);
        try {
            HttpResponse<String> forbiddenGet = getAsset(session.cookie(), UUID.randomUUID());
            assertThat(forbiddenGet.statusCode()).isEqualTo(403);
            assertThat(forbiddenGet.body()).contains("\"code\":\"ACCESS_DENIED\"");

            HttpResponse<String> forbiddenPost =
                    postAsset(session.cookie(), session.csrfToken(), VALID_ASSET, UUID.randomUUID());
            assertThat(forbiddenPost.statusCode()).isEqualTo(403);
            assertThat(forbiddenPost.body()).contains("\"code\":\"ACCESS_DENIED\"");
        } finally {
            jdbc.update(
                    """
                    INSERT INTO identity.human_user_roles (user_id, role_code)
                    SELECT id, 'IT_OPERATOR' FROM identity.human_users
                    WHERE normalized_username = 'inventory.operator'
                    ON CONFLICT DO NOTHING
                    """);
        }
    }

    @Test
    void returnsProblemDetailsForMissingAssets() throws Exception {
        Session session = login();
        HttpResponse<String> missing = getAsset(session.cookie(), UUID.randomUUID());

        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(missing.body())
                .contains("\"code\":\"ASSET_NOT_FOUND\"")
                .contains("\"correlationId\"")
                .contains("\"errors\":[]");

        HttpResponse<String> malformed = getAsset(session.cookie(), "not-a-uuid");
        assertThat(malformed.statusCode()).isEqualTo(404);
        assertThat(malformed.body()).contains("\"code\":\"ASSET_NOT_FOUND\"");

        HttpResponse<String> nonV4 = getAsset(session.cookie(), "0b2b7ee9-017e-1d62-83e0-87056735e71e");
        assertThat(nonV4.statusCode()).isEqualTo(404);
    }

    private Session login() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(uri("/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String setCookie = response.headers().firstValue("set-cookie").orElseThrow();
        return new Session(
                setCookie.substring(0, setCookie.indexOf(';')),
                extract(response.body(), "csrfToken"));
    }

    private HttpResponse<String> postAsset(String cookie, String csrfToken, String body, UUID correlationId)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/assets"))
                .header("Cookie", cookie)
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrfToken != null) {
            request.header("X-CSRF-Token", csrfToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getAsset(String cookie, UUID assetId) throws Exception {
        return getAsset(cookie, assetId.toString());
    }

    private HttpResponse<String> getAsset(String cookie, String assetId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(uri("/api/v1/assets/" + assetId)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String extract(String body, String property) {
        var matcher = Pattern.compile("\\\"" + property + "\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private record Session(String cookie, String csrfToken) {}
}
