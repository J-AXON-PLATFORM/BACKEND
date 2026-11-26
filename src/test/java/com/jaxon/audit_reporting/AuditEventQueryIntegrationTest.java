package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
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
            "jaxon.identity.bootstrap.username=Audit.Query.Admin",
            "jaxon.identity.bootstrap.password=Audit query admin passphrase 42!"
        })
class AuditEventQueryIntegrationTest {

    private static final String ADMIN = "Audit.Query.Admin";
    private static final String ADMIN_PASSWORD = "Audit query admin passphrase 42!";
    private static final String AUDITOR_PASSWORD = "Audit query auditor passphrase 42!";
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

    @Autowired
    AuditFactRecorder recorder;

    @Test
    void onlyAuditorsCanQueryAndSystemAdminHasNoImplicitAuthority() throws Exception {
        Session admin = login(ADMIN, ADMIN_PASSWORD);
        Session auditor = auditor(admin, "Auditor.Only." + suffix());
        Session operator = createUser(admin, "Operator.NoAudit." + suffix(), List.of("IT_OPERATOR", "SUPPORT_TECHNICIAN"));

        HttpRequest anonymous = HttpRequest.newBuilder(URI.create(url("/api/v1/audit/events"))).GET().build();
        assertThat(http.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        HttpResponse<String> adminResponse = get(admin, "/api/v1/audit/events");
        assertThat(adminResponse.statusCode()).isEqualTo(403);
        assertThat(adminResponse.body()).contains("ACCESS_DENIED");
        assertThat(get(operator, "/api/v1/audit/events").statusCode()).isEqualTo(403);
        assertThat(get(auditor, "/api/v1/audit/events").statusCode()).isEqualTo(200);

        Session adminAuditor = createUser(admin, "Admin.Auditor." + suffix(), List.of("SYSTEM_ADMIN", "AUDITOR"));
        assertThat(get(adminAuditor, "/api/v1/audit/events").statusCode()).isEqualTo(200);
    }

    @Test
    void filtersPaginatesAndOrdersDeterministicallyWithoutProhibitedFields() throws Exception {
        Session auditor = auditor(login(ADMIN, ADMIN_PASSWORD), "Auditor.Filters." + suffix());
        UUID correlation = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        Instant base = Instant.parse("2026-11-20T10:00:00Z");
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            ids.add(insert(correlation, index == 4 ? UUID.randomUUID() : actor,
                    index == 3 ? resource : UUID.randomUUID(), base.plusSeconds(index * 60L),
                    index % 2 == 0 ? "READY" : "FAILED"));
        }
        UUID tieA = insert(correlation, actor, UUID.randomUUID(), base.plusSeconds(600), "READY");
        UUID tieB = insert(correlation, actor, UUID.randomUUID(), base.plusSeconds(600), "READY");

        JsonNode page = json(get(auditor, "/api/v1/audit/events?correlationId=" + correlation + "&size=3"));
        assertThat(page.path("totalElements").asLong()).isEqualTo(7);
        assertThat(page.path("totalPages").asLong()).isEqualTo(3);
        assertThat(page.path("size").asInt()).isEqualTo(3);
        List<String> first = ids(page);
        assertThat(first).containsExactlyElementsOf(List.of(
                sortedDescending(tieA, tieB).get(0), sortedDescending(tieA, tieB).get(1), ids.get(4).toString()));

        JsonNode item = page.path("items").get(2);
        assertThat(item.propertyNames()).containsExactlyInAnyOrder(
                "auditEventId", "sourceEventId", "eventType", "eventVersion", "producerModule", "occurredAt",
                "recordedAt", "actorType", "actorId", "resourceType", "resourceId", "resourceVersion",
                "result", "correlationId", "metadata");
        assertThat(item.path("metadata").propertyNames()).containsExactlyInAnyOrder("previousStatus", "newStatus");

        assertThat(ids(json(get(auditor, "/api/v1/audit/events?correlationId=" + correlation + "&page=2&size=3"))))
                .hasSize(1);
        assertThat(ids(json(get(auditor, "/api/v1/audit/events?resourceId=" + resource))))
                .containsExactly(ids.get(3).toString());
        assertThat(json(get(auditor, "/api/v1/audit/events?correlationId=" + correlation + "&actorId=" + actor))
                        .path("totalElements").asLong())
                .isEqualTo(6);
        assertThat(ids(json(get(auditor, "/api/v1/audit/events?correlationId=" + correlation
                        + "&from=" + enc(base.plusSeconds(60).toString()) + "&to=" + enc(base.plusSeconds(180).toString())))))
                .containsExactly(ids.get(2).toString(), ids.get(1).toString());
        assertThat(json(get(auditor, "/api/v1/audit/events?correlationId=" + correlation
                        + "&module=AI&eventType=AI_SOURCE_DISABLED&resourceType=AI_SOURCE&result=SUCCESS"))
                        .path("totalElements").asLong())
                .isEqualTo(7);
        for (String empty : List.of("module=IDENTITY", "result=DENIED", "eventType=HUMAN_USER_CREATED",
                "resourceType=HUMAN_USER")) {
            assertThat(json(get(auditor, "/api/v1/audit/events?correlationId=" + correlation + "&" + empty))
                            .path("totalElements").asLong())
                    .as(empty)
                    .isZero();
        }
        JsonNode defaults = json(get(auditor, "/api/v1/audit/events?correlationId=" + correlation));
        assertThat(defaults.path("page").asInt()).isZero();
        assertThat(defaults.path("size").asInt()).isEqualTo(50);
        assertThat(json(get(auditor, "/api/v1/audit/events?size=200")).path("size").asInt()).isEqualTo(200);
    }

    @Test
    void rejectsInvalidUnknownAndDuplicatedFiltersWithoutEcho() throws Exception {
        Session auditor = auditor(login(ADMIN, ADMIN_PASSWORD), "Auditor.Invalid." + suffix());
        for (String query : List.of(
                "size=0", "size=201", "page=-1", "page=abc", "size=01", "module=ai", "module=FOO",
                "result=OK", "eventType=lower", "resourceType=X", "actorId=not-a-uuid",
                "resourceId=34844482-1d29-1d30-87fd-1bb67a22e050", "correlationId=", "from=2026-11-20",
                "from=2026-11-20T10:00:00%2B01:00", "to=2026-13-20T10:00:00Z",
                "from=2026-11-20T10:00:00Z&to=2026-11-20T10:00:00Z", "unknownSecretParam=1",
                "result=SUCCESS&result=FAILED")) {
            HttpResponse<String> response = get(auditor, "/api/v1/audit/events?" + query);
            assertThat(response.statusCode()).as(query).isEqualTo(422);
            assertThat(response.body()).as(query).contains("VALIDATION_FAILED").doesNotContain("unknownSecretParam");
        }
    }

    @Test
    void recorderRequiresTheCallersTransactionAndRejectsFactsOutsideTheCatalog() {
        AuditFact valid = new AuditFact(
                UUID.randomUUID(), "AI_SOURCE_DISABLED", "1.0", "AI", Instant.now(), "HUMAN_USER",
                UUID.randomUUID(), "AI_SOURCE", UUID.randomUUID(), 3L, "SUCCESS", UUID.randomUUID(),
                Map.of("previousStatus", "READY", "newStatus", "DISABLED"));
        assertThatThrownBy(() -> recorder.record(valid)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                        Integer.class,
                        valid.sourceEventId()))
                .isZero();
    }

    @Test
    void auditEventsAreAppendOnly() {
        UUID id = insert(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "READY");
        for (String statement : List.of(
                "UPDATE audit_reporting.audit_events SET result = 'FAILED' WHERE audit_event_id = '" + id + "'",
                "DELETE FROM audit_reporting.audit_events WHERE audit_event_id = '" + id + "'",
                "TRUNCATE audit_reporting.audit_events")) {
            assertThatThrownBy(() -> jdbc.execute(statement)).as(statement).hasMessageContaining("append-only");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT result FROM audit_reporting.audit_events WHERE audit_event_id = ?", String.class, id))
                .isEqualTo("SUCCESS");
    }

    private UUID insert(UUID correlation, UUID actor, UUID resource, Instant occurredAt, String previous) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, metadata)
                VALUES (?, ?, 'AI_SOURCE_DISABLED', '1.0', 'AI', ?, 'HUMAN_USER', ?, 'AI_SOURCE', ?, 3,
                        'SUCCESS', ?, jsonb_build_object('previousStatus', ?::text, 'newStatus', 'DISABLED'))
                """,
                id, UUID.randomUUID(), Timestamp.from(occurredAt), actor, resource, correlation, previous);
        return id;
    }

    private static List<String> sortedDescending(UUID left, UUID right) {
        return left.toString().compareTo(right.toString()) > 0
                ? List.of(left.toString(), right.toString())
                : List.of(right.toString(), left.toString());
    }

    private static List<String> ids(JsonNode page) {
        List<String> result = new ArrayList<>();
        page.path("items").forEach(item -> result.add(item.path("auditEventId").asString()));
        return result;
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private Session auditor(Session admin, String username) throws Exception {
        return createUser(admin, username, List.of("AUDITOR"));
    }

    private Session createUser(Session admin, String username, List<String> roles) throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "username", username, "password", AUDITOR_PASSWORD, "roles", roles));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/users")))
                .header("Cookie", admin.cookie())
                .header("X-CSRF-Token", admin.csrfToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return login(username, AUDITOR_PASSWORD);
    }

    private HttpResponse<String> get(Session session, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Cookie", session.cookie())
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
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

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Session(String cookie, String csrfToken) {}
}
