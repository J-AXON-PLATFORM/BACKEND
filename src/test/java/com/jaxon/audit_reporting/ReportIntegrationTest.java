package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Report.Admin",
            "jaxon.identity.bootstrap.password=Report admin passphrase 42!"
        })
class ReportIntegrationTest {

    private static final String ADMIN = "Report.Admin";
    private static final String ADMIN_PASSWORD = "Report admin passphrase 42!";
    private static final String PASSWORD = "Report user passphrase 42!";
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

    UUID assetId;
    UUID agentId;
    UUID ticketId;

    @BeforeEach
    void data() {
        assetId = UUID.randomUUID();
        agentId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-27T12:00:00Z");
        jdbc.update(
                "INSERT INTO inventory.assets"
                        + " (id, name, asset_type, status, created_at, updated_at, version)"
                        + " VALUES (?, ?, 'SERVER', 'ACTIVE', ?, ?, 0)",
                assetId, "  =sensitive,\"asset\"", Timestamp.from(now), Timestamp.from(now));
        jdbc.update(
                "INSERT INTO collector.agents"
                        + " (id, asset_id, status, created_at, updated_at, version)"
                        + " VALUES (?, ?, 'PENDING', ?, ?, 0)",
                agentId, assetId, Timestamp.from(now), Timestamp.from(now));
        UUID requester = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO support.tickets"
                        + " (id, type, summary, description, status, priority, source,"
                        + " requester_user_id, created_by_user_id, assignee_user_id, asset_id,"
                        + " created_at, updated_at, version)"
                        + " VALUES (?, 'INCIDENT', ?, 'hidden description', 'OPEN', 'HIGH', 'WEB',"
                        + " ?, ?, NULL, ?, ?, ?, 0)",
                ticketId, "@formula", requester, requester, assetId, Timestamp.from(now), Timestamp.from(now));
    }

    @Test
    void exposesClosedJsonSchemasWithFiltersAndStableFields() throws Exception {
        Session admin = login(ADMIN, ADMIN_PASSWORD);
        Session auditor = createUser(admin, "Report.Auditor." + suffix(), List.of("AUDITOR"));

        JsonNode assets = okJson(get(auditor, "/api/v1/reports/assets?status=ACTIVE&assetType=SERVER"));
        assertThat(assets.path("items").get(0).propertyNames())
                .containsExactlyInAnyOrder("assetId", "name", "assetType", "status", "createdAt", "updatedAt");
        assertThat(assets.path("size").asInt()).isEqualTo(50);

        JsonNode agents = okJson(get(auditor, "/api/v1/reports/agents?operationalStatus=UNKNOWN"));
        assertThat(agents.path("items").get(0).propertyNames()).containsExactlyInAnyOrder(
                "agentId", "assetId", "status", "operationalStatus", "lastSeenAt",
                "lastSnapshotAt", "createdAt", "updatedAt");
        assertThat(agents.path("items").get(0).path("operationalStatus").asString()).isEqualTo("UNKNOWN");

        JsonNode tickets = okJson(get(auditor, "/api/v1/reports/tickets?priority=HIGH"));
        assertThat(tickets.path("items").get(0).propertyNames()).containsExactlyInAnyOrder(
                "ticketId", "type", "summary", "status", "priority", "source", "requesterUserId",
                "createdByUserId", "assigneeUserId", "assetId", "createdAt", "updatedAt");
        assertThat(tickets.toString()).doesNotContain("hidden description");
    }

    @Test
    void appliesTheExactRoleMatrixWithoutImplicitSystemAdminAccess() throws Exception {
        Session admin = login(ADMIN, ADMIN_PASSWORD);
        Session systemAdmin = createUser(admin, "Report.SystemAdmin." + suffix(), List.of("SYSTEM_ADMIN"));
        Session operator = createUser(admin, "Report.Operator." + suffix(), List.of("IT_OPERATOR"));
        Session technician = createUser(admin, "Report.Tech." + suffix(), List.of("SUPPORT_TECHNICIAN"));

        assertThat(get(systemAdmin, "/api/v1/reports/assets").statusCode()).isEqualTo(403);
        assertThat(get(operator, "/api/v1/reports/assets").statusCode()).isEqualTo(200);
        assertThat(get(operator, "/api/v1/reports/agents/export").statusCode()).isEqualTo(200);
        assertThat(get(operator, "/api/v1/reports/tickets").statusCode()).isEqualTo(403);
        assertThat(get(technician, "/api/v1/reports/tickets").statusCode()).isEqualTo(200);
        assertThat(get(technician, "/api/v1/reports/assets/export").statusCode()).isEqualTo(403);
        assertThat(http.send(
                                HttpRequest.newBuilder(URI.create(url("/api/v1/reports/assets"))).GET().build(),
                                HttpResponse.BodyHandlers.ofString())
                        .statusCode())
                .isEqualTo(401);
    }

    @Test
    void generatesCompleteCsvAndDurablyAuditsExactlyOnceBeforeSuccess() throws Exception {
        Session auditor = createUser(
                login(ADMIN, ADMIN_PASSWORD), "Report.Export." + suffix(), List.of("AUDITOR"));
        int before = jdbc.queryForObject(
                "SELECT count(*) FROM audit_reporting.audit_events WHERE event_type = 'REPORT_EXPORTED'",
                Integer.class);

        HttpResponse<String> response = get(auditor, "/api/v1/reports/assets/export?status=ACTIVE");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElseThrow())
                .isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.headers().firstValue("content-disposition").orElseThrow())
                .isEqualTo("attachment; filename=\"assets-report.csv\"");
        assertThat(response.body())
                .startsWith("\"assetId\",\"name\",\"assetType\",\"status\",\"createdAt\",\"updatedAt\"\r\n")
                .contains("\"'  =sensitive,\"\"asset\"\"\"");
        assertThat(response.body().replace("\r\n", "")).doesNotContain("\n");

        List<Map<String, Object>> facts = jdbc.queryForList(
                "SELECT producer_module, actor_type, resource_type, result, metadata::text AS metadata"
                        + " FROM audit_reporting.audit_events WHERE event_type = 'REPORT_EXPORTED'"
                        + " ORDER BY recorded_at, audit_event_id");
        assertThat(facts).hasSize(before + 1);
        assertThat(facts.getLast())
                .containsEntry("producer_module", "AUDIT_REPORTING")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("resource_type", "REPORT_EXPORT")
                .containsEntry("result", "SUCCESS");
        assertThat(facts.getLast().get("metadata").toString())
                .contains("\"format\": \"CSV\"", "\"reportType\": \"ASSETS\"", "\"rowCount\":");
    }

    @Test
    void rejectsUnknownRepeatedEmptyInvalidAndExportPaginationParametersWithoutEcho() throws Exception {
        Session auditor = createUser(
                login(ADMIN, ADMIN_PASSWORD), "Report.Invalid." + suffix(), List.of("AUDITOR"));
        for (String path : List.of(
                "/api/v1/reports/assets?status=",
                "/api/v1/reports/assets?size=201",
                "/api/v1/reports/assets?status=ACTIVE&status=INACTIVE",
                "/api/v1/reports/agents?assetId=secret",
                "/api/v1/reports/tickets?createdFrom=2026-11-27T12:00:00Z&createdTo=2026-11-27T12:00:00Z",
                "/api/v1/reports/assets/export?page=0",
                "/api/v1/reports/assets?unknownSecret=secret")) {
            HttpResponse<String> response = get(auditor, path);
            assertThat(response.statusCode()).as(path).isEqualTo(422);
            assertThat(response.body()).contains("VALIDATION_FAILED").doesNotContain("secret");
        }
    }

    private JsonNode okJson(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private Session createUser(Session admin, String username, List<String> roles) throws Exception {
        String body = JSON.writeValueAsString(Map.of("username", username, "password", PASSWORD, "roles", roles));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/users")))
                .header("Cookie", admin.cookie())
                .header("X-CSRF-Token", admin.csrfToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return login(username, PASSWORD);
    }

    private HttpResponse<String> get(Session session, String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url(path)))
                        .header("Cookie", session.cookie())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Session login(String username, String password) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/auth/login")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Matcher matcher = Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return new Session(response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0], matcher.group(1));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Session(String cookie, String csrfToken) {}
}
