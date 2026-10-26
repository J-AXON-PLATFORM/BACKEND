package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
            "jaxon.identity.bootstrap.username=Job.Operator",
            "jaxon.identity.bootstrap.password=Job integration passphrase 42!"
        })
class DiscoveryJobIntegrationTest {

    private static final String USERNAME = "Job.Operator";
    private static final String PASSWORD = "Job integration passphrase 42!";
    private static final String PUBLIC_KEY =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKg2VaOybk2P/sGQKcJkL7QWqLpzaxLfOjfPCsqSy5aL";
    private static final String FINGERPRINT =
            "SHA256:s5flX5trJMwyaxA/ME8Kyid8X3Q4XdOM487UdG7jOnA";
    private static final Path JOB_ROOT = temporaryDirectory();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void jobProperties(DynamicPropertyRegistry properties) {
        properties.add("jaxon.discovery.jobs.publication-root", JOB_ROOT::toString);
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DiscoveryJobService jobs;
    @MockitoBean DiscoverySftpGoProvisioner provisioner;

    @BeforeEach
    void reset() throws IOException {
        jdbc.update("DELETE FROM discovery.outbox_events");
        jdbc.update("DELETE FROM discovery.discovery_jobs");
        jdbc.update("DELETE FROM discovery.discovery_executor_scopes");
        jdbc.update("DELETE FROM discovery.discovery_executors");
        jdbc.update("DELETE FROM discovery.authorized_scopes");
        jdbc.update("DELETE FROM identity.technical_identities WHERE identity_type = 'DISCOVERY_EXECUTOR'");
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'IT_OPERATOR' FROM identity.human_users
                WHERE normalized_username = 'job.operator'
                ON CONFLICT DO NOTHING
                """);
        try (var paths = Files.list(JOB_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isDirectory(path)) {
                    try (var nested = Files.list(path)) {
                        for (Path file : nested.toList()) Files.delete(file);
                    }
                }
                Files.delete(path);
            }
        }
    }

    @Test
    void createsPublishesAndQueriesAClosedBasicJob() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> created = postJob(session, seed, correlationId);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID jobId = UUID.fromString(extract(created.body(), "jobId"));
        assertThat(created.body())
                .contains("\"executorId\":\"" + seed.executorId() + "\"")
                .contains("\"scopeId\":\"" + seed.scopeId() + "\"")
                .contains("\"profile\":\"BASIC\"")
                .contains("\"status\":\"DISPATCHED\"")
                .contains("\"version\":1");
        Path target = JOB_ROOT.resolve("discovery-executor-" + seed.executorId() + "-jobs")
                .resolve(jobId + ".json");
        assertThat(target).isRegularFile();
        assertThat(Files.exists(target.resolveSibling(jobId + ".json.part"))).isFalse();
        JsonNode document = json.readTree(Files.readString(target));
        assertThat(document.properties()).hasSize(9);
        assertThat(document.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(document.path("messageType").asText()).isEqualTo("DISCOVERY_JOB");
        assertThat(document.path("scopeCidr").asText()).isEqualTo("192.0.2.0/24");
        assertThat(jdbc.queryForList(
                        "SELECT event_type FROM discovery.outbox_events WHERE resource_id = ? ORDER BY resource_version",
                        String.class,
                        jobId))
                .containsExactly("DISCOVERY_JOB_CREATED", "DISCOVERY_JOB_DISPATCHED");
        assertThat(getJob(session, jobId).body()).isEqualTo(created.body());
        assertThat(postJob(session, seed, UUID.randomUUID()).statusCode()).isEqualTo(409);
    }

    @Test
    void retainsPendingAndReconcilesTheSameJobAfterStorageReturns() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(false);

        HttpResponse<String> unavailable = postJob(session, seed, UUID.randomUUID());

        assertThat(unavailable.statusCode()).isEqualTo(503);
        UUID jobId = jdbc.queryForObject("SELECT job_id FROM discovery.discovery_jobs", UUID.class);
        assertThat(unavailable.headers().firstValue("location"))
                .contains("/api/v1/discovery/jobs/" + jobId);
        assertThat(unavailable.body()).contains("\"instance\":\"/api/v1/discovery/jobs/" + jobId + "\"");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("PENDING");
        Files.createDirectory(JOB_ROOT.resolve("discovery-executor-" + seed.executorId() + "-jobs"));
        jobs.reconcilePending();
        jobs.reconcilePending();
        assertThat(jdbc.queryForMap(
                        "SELECT status, version FROM discovery.discovery_jobs WHERE job_id = ?", jobId))
                .containsEntry("status", "DISPATCHED")
                .containsEntry("version", 1L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events WHERE resource_id = ?", Integer.class, jobId))
                .isEqualTo(2);
    }

    @Test
    void expiresAPendingJobWhenItsScopeBecomesInactiveBeforeReconciliation() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(false);

        HttpResponse<String> unavailable = postJob(session, seed, UUID.randomUUID());
        UUID jobId = jdbc.queryForObject("SELECT job_id FROM discovery.discovery_jobs", UUID.class);
        assertThat(unavailable.statusCode()).isEqualTo(503);

        jdbc.update("UPDATE discovery.authorized_scopes SET status = 'DISABLED' WHERE id = ?",
                seed.scopeId());
        Files.createDirectory(JOB_ROOT.resolve("discovery-executor-" + seed.executorId() + "-jobs"));
        jobs.reconcilePending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("EXPIRED");
        assertThat(JOB_ROOT.resolve("discovery-executor-" + seed.executorId() + "-jobs")
                        .resolve(jobId + ".json"))
                .doesNotExist();
    }

    @Test
    void removesAFinalFileBeforeExpiringAJobAfterPostRenameRecovery() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(false);

        HttpResponse<String> unavailable = postJob(session, seed, UUID.randomUUID());
        UUID jobId = jdbc.queryForObject("SELECT job_id FROM discovery.discovery_jobs", UUID.class);
        assertThat(unavailable.statusCode()).isEqualTo(503);
        Path directory = JOB_ROOT.resolve("discovery-executor-" + seed.executorId() + "-jobs");
        Files.createDirectory(directory);
        Path target = Files.writeString(directory.resolve(jobId + ".json"), "published-before-crash");
        jdbc.update("UPDATE discovery.discovery_jobs "
                + "SET created_at = now() - interval '1 hour 1 second', "
                + "expires_at = now() - interval '1 second' WHERE job_id = ?", jobId);

        jobs.reconcilePending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("EXPIRED");
        assertThat(target).doesNotExist();
    }

    @Test
    void expiresAPendingJobWhenItsPublicationDirectoryWasNeverCreated() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(false);

        HttpResponse<String> unavailable = postJob(session, seed, UUID.randomUUID());
        UUID jobId = jdbc.queryForObject("SELECT job_id FROM discovery.discovery_jobs", UUID.class);
        assertThat(unavailable.statusCode()).isEqualTo(503);
        jdbc.update("UPDATE discovery.discovery_jobs "
                + "SET created_at = now() - interval '1 hour 1 second', "
                + "expires_at = now() - interval '1 second' WHERE job_id = ?", jobId);

        jobs.reconcilePending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("EXPIRED");
        assertThat(JOB_ROOT.resolve("discovery-executor-" + seed.executorId() + "-jobs"))
                .doesNotExist();
    }

    @Test
    void deniesAnAdministratorWithoutTheOperatorRole() throws Exception {
        Seed seed = seedActiveExecutor(true);
        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'IT_OPERATOR'");
        Session administrator = login();
        assertThat(postJob(administrator, seed, UUID.randomUUID()).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery.discovery_jobs", Integer.class))
                .isZero();
    }

    private Seed seedActiveExecutor(boolean createDirectory) throws IOException {
        UUID actorId = jdbc.queryForObject(
                "SELECT id FROM identity.human_users WHERE normalized_username = 'job.operator'", UUID.class);
        UUID scopeId = UUID.randomUUID();
        UUID executorId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO discovery.authorized_scopes "
                        + "(id,name,normalized_name,cidr,status,created_by_user_id,created_at,updated_at,version) "
                        + "VALUES (?, 'Job scope', ?, '192.0.2.0/24', 'ACTIVE', ?, now(), now(), 0)",
                scopeId, "job-scope-" + scopeId, actorId);
        jdbc.update(
                "INSERT INTO discovery.discovery_executors "
                        + "(id,name,normalized_name,status,created_by_user_id,created_at,updated_at,version,sftp_username,activated_at) "
                        + "VALUES (?, 'Job executor', ?, 'ACTIVE', ?, now(), now(), 1, ?, now())",
                executorId, "job-executor-" + executorId, actorId,
                "discovery-executor-" + executorId);
        jdbc.update(
                "INSERT INTO discovery.discovery_executor_scopes (executor_id,scope_id,associated_at) "
                        + "VALUES (?, ?, now())",
                executorId, scopeId);
        jdbc.update(
                "INSERT INTO identity.technical_identities "
                        + "(id,agent_id,discovery_executor_id,identity_type,public_key,public_key_fingerprint,created_at,updated_at,version) "
                        + "VALUES (?,NULL,?,'DISCOVERY_EXECUTOR',?,?,now(),now(),1)",
                UUID.randomUUID(), executorId, PUBLIC_KEY, FINGERPRINT);
        when(provisioner.ensureProvisioned(eq(executorId), any())).thenReturn(
                new DiscoveryExecutorActivationResponse(
                        "sftp.example", 2022, "discovery-executor-" + executorId,
                        PUBLIC_KEY, FINGERPRINT));
        if (createDirectory) {
            Files.createDirectory(JOB_ROOT.resolve("discovery-executor-" + executorId + "-jobs"));
        }
        return new Seed(executorId, scopeId);
    }

    private HttpResponse<String> postJob(Session session, Seed seed, UUID correlationId)
            throws Exception {
        return send("POST", "/api/v1/discovery/jobs", session,
                "{\"executorId\":\"" + seed.executorId() + "\",\"scopeId\":\""
                        + seed.scopeId() + "\"}", correlationId);
    }

    private HttpResponse<String> getJob(Session session, UUID jobId) throws Exception {
        return send("GET", "/api/v1/discovery/jobs/" + jobId, session, null, null);
    }

    private Session login() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/auth/login", null,
                "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}",
                UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(200);
        return new Session(response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0],
                extract(response.body(), "csrfToken"));
    }

    private HttpResponse<String> send(String method, String path, Session session,
            String body, UUID correlationId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (session != null) {
            request.header("Cookie", session.cookie());
            if (body != null) request.header("X-CSRF-Token", session.csrfToken());
        }
        if (correlationId != null) request.header("X-Correlation-ID", correlationId.toString());
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String body, String field) {
        var matcher = Pattern.compile("\\\"" + field + "\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("jaxon-discovery-jobs-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Session(String cookie, String csrfToken) {}
    private record Seed(UUID executorId, UUID scopeId) {}
}
