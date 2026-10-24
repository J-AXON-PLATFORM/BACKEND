package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
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
            "jaxon.identity.bootstrap.username=Discovery.Admin",
            "jaxon.identity.bootstrap.password=Discovery test passphrase 42!"
        })
class AuthorizedScopeIntegrationTest {

    private static final String USERNAME = "Discovery.Admin";
    private static final String PASSWORD = "Discovery test passphrase 42!";

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

    @BeforeEach
    void clearDiscoveryData() {
        jdbc.update("DELETE FROM discovery.outbox_events");
        jdbc.update("DELETE FROM discovery.authorized_scopes");
    }

    @AfterEach
    void restoreAdministratorRole() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'SYSTEM_ADMIN' FROM identity.human_users
                WHERE normalized_username = 'discovery.admin'
                ON CONFLICT DO NOTHING
                """);
    }

    @Test
    void createsListsAndChangesStatusWithMinimalDurableEvents() throws Exception {
        Session session = login();
        UUID createCorrelation = UUID.randomUUID();
        HttpResponse<String> created = send(
                "POST",
                "/api/v1/discovery/scopes",
                session,
                "{\"name\":\"  Red\\tＯficina  \",\"cidr\":\"2001:0DB8:0:0::/64\"}",
                createCorrelation);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID scopeId = UUID.fromString(extract(created.body(), "scopeId"));
        assertThat(created.body())
                .contains("\"name\":\"Red Oficina\"")
                .contains("\"cidr\":\"2001:db8::/64\"")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"version\":0");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, event_version, producer_module, resource_version,
                               correlation_id, payload::text AS payload
                        FROM discovery.outbox_events WHERE resource_id = ?
                        """,
                        scopeId))
                .containsEntry("event_type", "DISCOVERY_SCOPE_CREATED")
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "DISCOVERY")
                .containsEntry("resource_version", 0L)
                .containsEntry("correlation_id", createCorrelation)
                .containsEntry("payload", "{\"status\": \"ACTIVE\"}");

        assertThat(send("GET", "/api/v1/discovery/scopes/" + scopeId, session, null, null).body())
                .isEqualTo(created.body());
        assertThat(send("GET", "/api/v1/discovery/scopes?page=0&size=20", session, null, null).body())
                .contains("\"totalElements\":1", "\"scopeId\":\"" + scopeId + "\"");
        assertThat(send(
                                "PUT",
                                "/api/v1/discovery/scopes/" + scopeId + "/status",
                                session,
                                "{\"status\":\"DISABLED\"}",
                                UUID.randomUUID())
                        .statusCode())
                .isEqualTo(422);
        assertThat(send(
                                "PUT",
                                "/api/v1/discovery/scopes/" + scopeId + "/status",
                                session,
                                "{\"status\":\"DISABLED\",\"expectedVersion\":null}",
                                UUID.randomUUID())
                        .statusCode())
                .isEqualTo(422);

        UUID updateCorrelation = UUID.randomUUID();
        HttpResponse<String> disabled = send(
                "PUT",
                "/api/v1/discovery/scopes/" + scopeId + "/status",
                session,
                "{\"status\":\"DISABLED\",\"expectedVersion\":0}",
                updateCorrelation);
        assertThat(disabled.statusCode()).isEqualTo(200);
        assertThat(disabled.body()).contains("\"status\":\"DISABLED\"", "\"version\":1");
        assertThat(jdbc.queryForObject(
                        "SELECT payload::text FROM discovery.outbox_events WHERE resource_id = ? AND resource_version = 1",
                        String.class,
                        scopeId))
                .isEqualTo("{\"newStatus\": \"DISABLED\", \"previousStatus\": \"ACTIVE\"}");

        int events = jdbc.queryForObject(
                "SELECT count(*) FROM discovery.outbox_events WHERE resource_id = ?", Integer.class, scopeId);
        assertThat(send(
                                "PUT",
                                "/api/v1/discovery/scopes/" + scopeId + "/status",
                                session,
                                "{\"status\":\"DISABLED\",\"expectedVersion\":1}",
                                UUID.randomUUID())
                        .body())
                .contains("\"version\":1");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events WHERE resource_id = ?", Integer.class, scopeId))
                .isEqualTo(events);
        assertThat(send(
                                "PUT",
                                "/api/v1/discovery/scopes/" + scopeId + "/status",
                                session,
                                "{\"status\":\"DISABLED\",\"expectedVersion\":0}",
                                UUID.randomUUID())
                        .statusCode())
                .isEqualTo(409);
    }

    @Test
    void validatesCanonicalCidrsAndRejectsExactDuplicatesButAllowsOverlap() throws Exception {
        Session session = login();
        assertThat(create(session, "Network A", "192.0.2.1/24").statusCode()).isEqualTo(422);
        assertThat(create(session, "Network A", "192.0.2.0/24").statusCode()).isEqualTo(201);
        assertThat(create(session, "network a", "198.51.100.0/24").statusCode()).isEqualTo(409);
        assertThat(create(session, "Network B", "192.0.2.0/24").statusCode()).isEqualTo(409);
        assertThat(create(session, "Network C", "192.0.2.0/25").statusCode()).isEqualTo(201);
        assertThat(create(session, "😀".repeat(101), "198.51.100.0/24").statusCode()).isEqualTo(201);
    }

    @Test
    void hidesDisabledScopesFromOperatorsWithTheSameNotFoundResponse() throws Exception {
        Session administrator = login();
        UUID scopeId = UUID.fromString(extract(create(administrator, "Hidden", "198.51.100.0/24").body(), "scopeId"));
        send(
                "PUT",
                "/api/v1/discovery/scopes/" + scopeId + "/status",
                administrator,
                "{\"status\":\"DISABLED\",\"expectedVersion\":0}",
                UUID.randomUUID());
        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'SYSTEM_ADMIN'");
        Session operator = login();

        HttpResponse<String> hidden = send("GET", "/api/v1/discovery/scopes/" + scopeId, operator, null, null);
        HttpResponse<String> missing = send(
                "GET", "/api/v1/discovery/scopes/" + UUID.randomUUID(), operator, null, null);
        assertThat(hidden.statusCode()).isEqualTo(404);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(hidden.body()).contains(
                "\"code\":\"DISCOVERY_SCOPE_NOT_FOUND\"",
                "\"detail\":\"No existe un ámbito accesible con el identificador indicado.\"");
        assertThat(missing.body()).contains(
                "\"code\":\"DISCOVERY_SCOPE_NOT_FOUND\"",
                "\"detail\":\"No existe un ámbito accesible con el identificador indicado.\"");
        assertThat(send("GET", "/api/v1/discovery/scopes", operator, null, null).body())
                .contains("\"totalElements\":0");
        assertThat(create(operator, "Denied", "203.0.113.0/24").statusCode()).isEqualTo(403);
    }

    @Test
    void rollsBackTheScopeWhenItsOutboxInsertFails() throws Exception {
        Session session = login();
        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION discovery.reject_test_outbox() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced outbox failure'; END; $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_test_outbox BEFORE INSERT ON discovery.outbox_events
                FOR EACH ROW EXECUTE FUNCTION discovery.reject_test_outbox()
                """);
        try {
            assertThat(create(session, "Atomic", "203.0.113.0/24").statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery.authorized_scopes", Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_outbox ON discovery.outbox_events");
            jdbc.execute("DROP FUNCTION IF EXISTS discovery.reject_test_outbox()");
        }
    }

    @Test
    void rollsBackAStatusChangeWhenItsOutboxInsertFails() throws Exception {
        Session session = login();
        UUID scopeId = UUID.fromString(extract(create(session, "Atomic update", "203.0.113.0/24").body(), "scopeId"));
        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION discovery.reject_test_status_outbox() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced status outbox failure'; END; $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_test_status_outbox BEFORE INSERT ON discovery.outbox_events
                FOR EACH ROW WHEN (NEW.event_type = 'DISCOVERY_SCOPE_STATUS_CHANGED')
                EXECUTE FUNCTION discovery.reject_test_status_outbox()
                """);
        try {
            assertThat(send(
                                    "PUT",
                                    "/api/v1/discovery/scopes/" + scopeId + "/status",
                                    session,
                                    "{\"status\":\"DISABLED\",\"expectedVersion\":0}",
                                    UUID.randomUUID())
                            .statusCode())
                    .isEqualTo(503);
            assertThat(jdbc.queryForMap(
                            "SELECT status, version FROM discovery.authorized_scopes WHERE id = ?", scopeId))
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("version", 0L);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_status_outbox ON discovery.outbox_events");
            jdbc.execute("DROP FUNCTION IF EXISTS discovery.reject_test_status_outbox()");
        }
    }

    private HttpResponse<String> create(Session session, String name, String cidr) throws Exception {
        return send(
                "POST",
                "/api/v1/discovery/scopes",
                session,
                "{\"name\":\"" + name + "\",\"cidr\":\"" + cidr + "\"}",
                UUID.randomUUID());
    }

    private Session login() throws Exception {
        HttpResponse<String> response = send(
                "POST",
                "/api/v1/auth/login",
                null,
                "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}",
                UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(200);
        return new Session(
                response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0],
                extract(response.body(), "csrfToken"));
    }

    private HttpResponse<String> send(
            String method, String path, Session session, String body, UUID correlationId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (session != null) {
            request.header("Cookie", session.cookie());
            if (body != null) {
                request.header("X-CSRF-Token", session.csrfToken());
            }
        }
        if (correlationId != null) {
            request.header("X-Correlation-ID", correlationId.toString());
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String body, String field) {
        java.util.regex.Matcher matcher = Pattern.compile("\\\"" + field + "\\\":\\\"([^\\\"]+)\\\"")
                .matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Missing " + field + " in " + body);
        }
        return matcher.group(1);
    }

    private record Session(String cookie, String csrfToken) {}
}
