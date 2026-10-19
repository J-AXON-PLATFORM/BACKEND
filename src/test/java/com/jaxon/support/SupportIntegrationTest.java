package com.jaxon.support;

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
            "jaxon.identity.bootstrap.username=Support.Operator",
            "jaxon.identity.bootstrap.password=Support test passphrase 42!"
        })
class SupportIntegrationTest {

    private static final String USERNAME = "Support.Operator";
    private static final String PASSWORD = "Support test passphrase 42!";
    private static final String VALID_TICKET = """
            {"type":"INCIDENT","summary":"Printer unavailable","description":"The reception printer is offline."}
            """;

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
    void createsPersistsAndRetrievesAnOwnedTicketWithItsDurableEvent() throws Exception {
        Session session = login();
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> created = postTicket(session, VALID_TICKET, correlationId);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID ticketId = UUID.fromString(extract(created.body(), "ticketId"));
        UUID requesterId = UUID.fromString(extract(created.body(), "requesterUserId"));
        assertThat(created.headers().firstValue("location")).contains("/api/v1/tickets/" + ticketId);
        assertThat(created.body())
                .contains("\"type\":\"INCIDENT\"")
                .contains("\"summary\":\"Printer unavailable\"")
                .contains("\"status\":\"OPEN\"")
                .contains("\"priority\":\"NORMAL\"")
                .contains("\"source\":\"WEB\"")
                .contains("\"createdByUserId\":\"" + requesterId + "\"")
                .contains("\"assigneeUserId\":null")
                .contains("\"assetId\":null")
                .contains("\"version\":0");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT requester_user_id, created_by_user_id, assignee_user_id, status,
                               priority, source, asset_id
                        FROM support.tickets WHERE id = ?
                        """,
                        ticketId))
                .containsEntry("requester_user_id", requesterId)
                .containsEntry("created_by_user_id", requesterId)
                .containsEntry("assignee_user_id", null)
                .containsEntry("status", "OPEN")
                .containsEntry("priority", "NORMAL")
                .containsEntry("source", "WEB")
                .containsEntry("asset_id", null);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, producer_module, actor_id, resource_id, correlation_id,
                               payload, status, attempts
                        FROM support.outbox_events WHERE resource_id = ?
                        """,
                        ticketId))
                .containsEntry("event_type", "TICKET_CREATED")
                .containsEntry("producer_module", "SUPPORT")
                .containsEntry("actor_id", requesterId)
                .containsEntry("resource_id", ticketId)
                .containsEntry("correlation_id", correlationId)
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 0);
        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM support.outbox_events WHERE resource_id = ?", String.class, ticketId);
        assertThat(payload)
                .contains("\"type\": \"INCIDENT\"")
                .doesNotContain("Printer unavailable", "reception printer");

        HttpResponse<String> retrieved = getTicket(session.cookie(), ticketId.toString());
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(retrieved.body()).isEqualTo(created.body());

        jdbc.update("UPDATE support.tickets SET requester_user_id = ? WHERE id = ?", UUID.randomUUID(), ticketId);
        HttpResponse<String> noLongerOwned = getTicket(session.cookie(), ticketId.toString());
        assertThat(noLongerOwned.statusCode()).isEqualTo(404);
        assertThat(noLongerOwned.body()).contains("\"code\":\"TICKET_NOT_FOUND\"");
    }

    @Test
    void validatesAnOptionalAssetThroughInventoryWithoutCrossSchemaReferences() throws Exception {
        Session session = login();
        UUID assetId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO inventory.assets (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, 'Associated asset', 'DESKTOP', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                assetId);

        HttpResponse<String> created = postTicket(
                session,
                """
                {"type":"SERVICE_REQUEST","summary":"Install software","description":"Approved tool.","assetId":"%s"}
                """.formatted(assetId),
                UUID.randomUUID());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"assetId\":\"" + assetId + "\"");

        int ticketsBefore = jdbc.queryForObject("SELECT count(*) FROM support.tickets", Integer.class);
        int eventsBefore = jdbc.queryForObject("SELECT count(*) FROM support.outbox_events", Integer.class);
        HttpResponse<String> missing = postTicket(
                session,
                """
                {"type":"QUESTION","summary":"Unknown asset","description":"Cannot associate it.","assetId":"%s"}
                """.formatted(UUID.randomUUID()),
                UUID.randomUUID());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("\"code\":\"ASSET_NOT_FOUND\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.tickets", Integer.class))
                .isEqualTo(ticketsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.outbox_events", Integer.class))
                .isEqualTo(eventsBefore);
    }

    @Test
    void rejectsInvalidFieldsAndUnavailableTicketIdentifiersSafely() throws Exception {
        Session session = login();
        HttpResponse<String> invalid = postTicket(
                session,
                "{\"type\":\"INVALID\",\"summary\":\" \",\"description\":\" \"}",
                UUID.randomUUID());
        assertThat(invalid.statusCode()).isEqualTo(422);
        assertThat(invalid.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(invalid.body())
                .contains("\"code\":\"VALIDATION_FAILED\"")
                .contains("\"pointer\":\"/type\"")
                .contains("\"pointer\":\"/summary\"")
                .contains("\"pointer\":\"/description\"");
        assertThat(getTicket(session.cookie(), "not-a-uuid").body())
                .contains("\"code\":\"TICKET_NOT_FOUND\"");
        assertThat(getTicket(session.cookie(), UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
    }

    @Test
    void requiresAuthenticationCsrfAndTheExplicitItOperatorRole() throws Exception {
        assertThat(getTicket(null, UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
        Session session = login();
        HttpResponse<String> missingCsrf = postTicket(
                new Session(session.cookie(), null), VALID_TICKET, UUID.randomUUID());
        assertThat(missingCsrf.statusCode()).isEqualTo(403);
        assertThat(missingCsrf.body()).contains("\"code\":\"CSRF_INVALID\"");

        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'IT_OPERATOR'");
        try {
            assertThat(getTicket(session.cookie(), UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
            assertThat(postTicket(session, VALID_TICKET, UUID.randomUUID()).statusCode()).isEqualTo(403);
        } finally {
            jdbc.update(
                    """
                    INSERT INTO identity.human_user_roles (user_id, role_code)
                    SELECT id, 'IT_OPERATOR' FROM identity.human_users
                    WHERE normalized_username = 'support.operator'
                    ON CONFLICT DO NOTHING
                    """);
        }
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
        return new Session(setCookie.substring(0, setCookie.indexOf(';')), extract(response.body(), "csrfToken"));
    }

    private HttpResponse<String> postTicket(Session session, String body, UUID correlationId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets"))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session.csrfToken() != null) {
            request.header("X-CSRF-Token", session.csrfToken());
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTicket(String cookie, String ticketId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(uri("/api/v1/tickets/" + ticketId)).GET();
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
