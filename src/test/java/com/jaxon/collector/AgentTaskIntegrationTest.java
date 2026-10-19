package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Task.Operator",
            "jaxon.identity.bootstrap.password=Task integration passphrase 42!"
        })
class AgentTaskIntegrationTest {

    private static final String USERNAME = "Task.Operator";
    private static final String PASSWORD = "Task integration passphrase 42!";
    private static final Path TASK_ROOT = temporaryDirectory("jaxon-task-publication-");
    private static final Path MAILBOX_ROOT = temporaryDirectory("jaxon-task-results-");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void taskProperties(DynamicPropertyRegistry properties) {
        properties.add("jaxon.collector.tasks.publication-root", TASK_ROOT::toString);
        properties.add("jaxon.collector.task-results.mailbox-root", MAILBOX_ROOT::toString);
        properties.add("jaxon.collector.task-results.stability-age", () -> "0s");
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentTaskService tasks;
    @Autowired AgentResultReconciler results;

    @BeforeEach
    void resetState() throws IOException {
        jdbc.update("DELETE FROM collector.accepted_task_results");
        jdbc.update("DELETE FROM collector.task_result_ingestions");
        jdbc.update("DELETE FROM collector.agent_tasks");
        jdbc.update("DELETE FROM collector.agents");
        jdbc.update("DELETE FROM inventory.assets");
        clear(TASK_ROOT);
        clear(MAILBOX_ROOT);
        restoreOperatorRole();
    }

    @Test
    void createsPublishesQueriesAndCompletesCollectSnapshotTask() throws Exception {
        Session session = login();
        UUID agentId = seedAgent(true);
        Path taskDirectory = Files.createDirectory(TASK_ROOT.resolve("agent-" + agentId + "-tasks"));

        HttpResponse<String> created = postTask(session, agentId.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\"}");

        assertThat(created.statusCode()).isEqualTo(201);
        UUID taskId = UUID.fromString(extract(created.body(), "taskId"));
        assertThat(created.headers().firstValue("location"))
                .contains("/api/v1/agents/" + agentId + "/tasks/" + taskId);
        assertThat(created.body())
                .contains("\"status\":\"DISPATCHED\"")
                .contains("\"taskType\":\"COLLECT_SNAPSHOT\"")
                .contains("\"completedAt\":null")
                .contains("\"error\":null");
        Path taskFile = taskDirectory.resolve(taskId + ".json");
        assertThat(taskFile).isRegularFile();
        JsonNode task = json.readTree(Files.readString(taskFile));
        assertThat(task.path("taskId").asText()).isEqualTo(taskId.toString());
        assertThat(task.path("agentId").asText()).isEqualTo(agentId.toString());
        assertThat(task.path("payload").isEmpty()).isTrue();
        assertThat(Files.exists(taskDirectory.resolve(taskId + ".json.part"))).isFalse();

        HttpResponse<String> duplicate = postTask(session, agentId.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\"}");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("\"code\":\"AGENT_TASK_CONFLICT\"");
        assertThat(getTask(session.cookie(), agentId, taskId).body()).isEqualTo(created.body());

        UUID resultMessageId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        Path responseDirectory = Files.createDirectory(MAILBOX_ROOT.resolve("agent-" + agentId + "-responses"));
        Files.writeString(
                responseDirectory.resolve(resultMessageId + ".json"),
                result(agentId, taskId, resultMessageId, resultId, "SUCCEEDED"));
        results.reconcile();

        HttpResponse<String> completed = getTask(session.cookie(), agentId, taskId);
        assertThat(completed.statusCode()).isEqualTo(200);
        assertThat(completed.body())
                .contains("\"status\":\"SUCCEEDED\"")
                .contains("\"completedAt\":")
                .contains("\"error\":null");
        assertThat(count("collector.accepted_task_results")).isEqualTo(1);
        assertThat(count("collector.task_result_ingestions")).isEqualTo(1);

        results.reconcile();
        assertThat(count("collector.accepted_task_results")).isEqualTo(1);
        assertThat(count("collector.task_result_ingestions")).isEqualTo(1);
    }

    @Test
    void persistsPendingTaskWhenPublicationFailsAndReconcilesItLater() throws Exception {
        Session session = login();
        UUID agentId = seedAgent(true);

        HttpResponse<String> unavailable = postTask(
                session, agentId.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\"}");

        assertThat(unavailable.statusCode()).isEqualTo(503);
        assertThat(unavailable.body()).contains("\"code\":\"TASK_PUBLICATION_UNAVAILABLE\"");
        UUID taskId = jdbc.queryForObject(
                "SELECT task_id FROM collector.agent_tasks WHERE agent_id = ?", UUID.class, agentId);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM collector.agent_tasks WHERE task_id = ?", String.class, taskId))
                .isEqualTo("PENDING");

        Path directory = Files.createDirectory(TASK_ROOT.resolve("agent-" + agentId + "-tasks"));
        Files.writeString(directory.resolve(taskId + ".json"), "conflicting-content");
        tasks.reconcilePending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM collector.agent_tasks WHERE task_id = ?", String.class, taskId))
                .isEqualTo("PENDING");
        Files.delete(directory.resolve(taskId + ".json"));
        tasks.reconcilePending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM collector.agent_tasks WHERE task_id = ?", String.class, taskId))
                .isEqualTo("DISPATCHED");
        assertThat(directory.resolve(taskId + ".json")).isRegularFile();
    }

    @Test
    void rejectsUnsafeResultsThenAcceptsOneFailedResultAndRecordsLaterConflict() throws Exception {
        Session session = login();
        UUID agentId = seedAgent(true);
        Files.createDirectory(TASK_ROOT.resolve("agent-" + agentId + "-tasks"));
        HttpResponse<String> created = postTask(session, agentId.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\"}");
        UUID taskId = UUID.fromString(extract(created.body(), "taskId"));
        Path responses = Files.createDirectory(MAILBOX_ROOT.resolve("agent-" + agentId + "-responses"));

        UUID malformedMessageId = UUID.randomUUID();
        Files.writeString(responses.resolve(malformedMessageId + ".json"), "{}");
        UUID oversizedMessageId = UUID.randomUUID();
        Files.write(responses.resolve(oversizedMessageId + ".json"),
                new byte[AgentTaskContract.MAX_RESULT_BYTES + 1]);
        results.reconcile();

        assertThat(jdbc.queryForList(
                        "SELECT reason_code FROM collector.task_result_ingestions ORDER BY reason_code",
                        String.class))
                .containsExactly("FILE_TOO_LARGE", "RESULT_SCHEMA_INVALID");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM collector.agent_tasks WHERE task_id = ?", String.class, taskId))
                .isEqualTo("DISPATCHED");

        UUID messageId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        Path accepted = responses.resolve(messageId + ".json");
        Files.writeString(accepted, failedResult(agentId, taskId, messageId, resultId));
        results.reconcile();

        HttpResponse<String> failed = getTask(session.cookie(), agentId, taskId);
        assertThat(failed.body())
                .contains("\"status\":\"FAILED\"")
                .contains("\"code\":\"COLLECTION_FAILED\"")
                .contains("La ejecución controlada")
                .doesNotContain("private diagnostic");
        assertThat(count("collector.accepted_task_results")).isEqualTo(1);

        Files.writeString(accepted, failedResult(agentId, taskId, messageId, resultId) + "\n");
        results.reconcile();
        assertThat(jdbc.queryForList(
                        "SELECT status FROM collector.task_result_ingestions ORDER BY received_at", String.class))
                .containsExactlyInAnyOrder("REJECTED", "REJECTED", "ACCEPTED", "CONFLICT");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM collector.agent_tasks WHERE task_id = ?", String.class, taskId))
                .isEqualTo("FAILED");
    }

    @Test
    void enforcesClosedRequestCsrfAuthorizationAgentStateAndIdentifiers() throws Exception {
        Session session = login();
        UUID activeAgent = seedAgent(true);
        UUID pendingAgent = seedAgent(false);
        Files.createDirectory(TASK_ROOT.resolve("agent-" + activeAgent + "-tasks"));

        assertThat(postTask(session, pendingAgent.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\"}")
                        .statusCode())
                .isEqualTo(409);
        assertThat(postTask(session, activeAgent.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\",\"extra\":true}")
                        .statusCode())
                .isEqualTo(422);
        assertThat(postTask(session, activeAgent.toString(), "{\"taskType\":\"COLLECT_NETWORK_STATE\"}")
                        .statusCode())
                .isEqualTo(422);
        assertThat(postTask(null, activeAgent.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\"}")
                        .statusCode())
                .isEqualTo(401);
        assertThat(postTask(
                                new Session(session.cookie(), null),
                                activeAgent.toString(),
                                "{\"taskType\":\"COLLECT_SNAPSHOT\"}")
                        .body())
                .contains("\"code\":\"CSRF_INVALID\"");
        assertThat(postTask(session, "not-a-uuid", "{\"taskType\":\"COLLECT_SNAPSHOT\"}")
                        .statusCode())
                .isEqualTo(404);
        assertThat(getTask(session.cookie(), activeAgent, UUID.randomUUID()).statusCode()).isEqualTo(404);

        removeOperatorRole();
        try {
            assertThat(postTask(session, activeAgent.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\"}")
                            .statusCode())
                    .isEqualTo(403);
        } finally {
            restoreOperatorRole();
        }
    }

    @Test
    void expiresOnlyNonTerminalTasksAndKeepsTerminalStateImmutable() throws Exception {
        Session session = login();
        UUID agentId = seedAgent(true);
        Files.createDirectory(TASK_ROOT.resolve("agent-" + agentId + "-tasks"));
        HttpResponse<String> created = postTask(session, agentId.toString(), "{\"taskType\":\"COLLECT_SNAPSHOT\"}");
        UUID taskId = UUID.fromString(extract(created.body(), "taskId"));
        Instant old = Instant.now().minusSeconds(3600);
        jdbc.update(
                "UPDATE collector.agent_tasks SET created_at = ?, expires_at = ?, updated_at = ? WHERE task_id = ?",
                Timestamp.from(old),
                Timestamp.from(old.plusSeconds(900)),
                Timestamp.from(old),
                taskId);

        tasks.reconcilePending();
        tasks.reconcilePending();

        assertThat(getTask(session.cookie(), agentId, taskId).body())
                .contains("\"status\":\"EXPIRED\"")
                .contains("\"completedAt\":");
        assertThat(jdbc.queryForObject(
                        "SELECT version FROM collector.agent_tasks WHERE task_id = ?", Long.class, taskId))
                .isEqualTo(2L);
    }

    private UUID seedAgent(boolean active) {
        UUID assetId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
                """
                INSERT INTO inventory.assets
                    (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, ?, 'DESKTOP', 'ACTIVE', ?, ?, 0)
                """,
                assetId,
                "Task test asset " + assetId,
                now,
                now);
        jdbc.update(
                """
                INSERT INTO collector.agents
                    (id, asset_id, status, created_at, updated_at, version, sftp_username, activated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?)
                """,
                agentId,
                assetId,
                active ? "ACTIVE" : "PENDING",
                now,
                now,
                active ? "agent-" + agentId : null,
                active ? now : null);
        return agentId;
    }

    private Session login() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder()
                        .uri(uri("/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String cookie = response.headers().firstValue("set-cookie").orElseThrow();
        return new Session(cookie.substring(0, cookie.indexOf(';')), extract(response.body(), "csrfToken"));
    }

    private HttpResponse<String> postTask(Session session, String agentId, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/agents/" + agentId + "/tasks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session != null) {
            request.header("Cookie", session.cookie());
            if (session.csrfToken() != null) {
                request.header("X-CSRF-Token", session.csrfToken());
            }
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTask(String cookie, UUID agentId, UUID taskId) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(uri("/api/v1/agents/" + agentId + "/tasks/" + taskId))
                        .header("Cookie", cookie)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String result(
            UUID agentId, UUID taskId, UUID messageId, UUID resultId, String status) {
        String now = Instant.now().toString();
        return """
                {"agentId":"%s","completedAt":"%s","contractVersion":"1.0",\
                "createdAt":"%s","messageId":"%s","messageType":"AGENT_RESULT",\
                "resultId":"%s","status":"%s","taskId":"%s","taskType":"COLLECT_SNAPSHOT"}
                """.formatted(agentId, now, now, messageId, resultId, status, taskId);
    }

    private static String failedResult(UUID agentId, UUID taskId, UUID messageId, UUID resultId) {
        String now = Instant.now().toString();
        return """
                {"agentId":"%s","completedAt":"%s","contractVersion":"1.0",\
                "createdAt":"%s","error":{"code":"COLLECTION_FAILED",\
                "message":"private diagnostic must not escape"},"messageId":"%s",\
                "messageType":"AGENT_RESULT","resultId":"%s","status":"FAILED",\
                "taskId":"%s","taskType":"COLLECT_SNAPSHOT"}
                """.formatted(agentId, now, now, messageId, resultId, taskId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void removeOperatorRole() {
        jdbc.update(
                """
                DELETE FROM identity.human_user_roles
                WHERE role_code = 'IT_OPERATOR'
                  AND user_id = (SELECT id FROM identity.human_users
                                 WHERE normalized_username = 'task.operator')
                """);
    }

    private void restoreOperatorRole() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'IT_OPERATOR' FROM identity.human_users
                WHERE normalized_username = 'task.operator'
                ON CONFLICT DO NOTHING
                """);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String extract(String body, String property) {
        var matcher = Pattern.compile("\\\"" + property + "\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static Path temporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void clear(Path root) throws IOException {
        try (var entries = Files.walk(root)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).filter(path -> !path.equals(root)).toList()) {
                Files.delete(path);
            }
        }
    }

    private record Session(String cookie, String csrfToken) {}
}
