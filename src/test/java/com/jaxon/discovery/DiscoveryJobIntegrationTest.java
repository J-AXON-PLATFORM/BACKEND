package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
    private static final Path RESULT_ROOT = temporaryDirectory();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void jobProperties(DynamicPropertyRegistry properties) {
        properties.add("jaxon.discovery.jobs.publication-root", JOB_ROOT::toString);
        properties.add("jaxon.discovery.results.mailbox-root", RESULT_ROOT::toString);
        properties.add("jaxon.discovery.results.stability-age", () -> "0s");
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DiscoveryJobService jobs;
    @Autowired DiscoveryResultReconciler results;
    @Autowired DiscoveryResultIngestionService ingestion;
    @MockitoBean DiscoverySftpGoProvisioner provisioner;

    @BeforeEach
    void reset() throws IOException {
        jdbc.update("DELETE FROM discovery.outbox_events");
        jdbc.update("DELETE FROM discovery.discovered_devices");
        jdbc.update("DELETE FROM discovery.accepted_discovery_results");
        jdbc.update("DELETE FROM discovery.discovery_result_ingestions");
        jdbc.update("DELETE FROM discovery.discovery_jobs");
        jdbc.update("DELETE FROM discovery.discovery_executor_scopes");
        jdbc.update("DELETE FROM discovery.discovery_executors");
        jdbc.update("DELETE FROM discovery.authorized_scopes");
        jdbc.update("DELETE FROM identity.technical_identities WHERE identity_type = 'DISCOVERY_EXECUTOR'");
        jdbc.update("DELETE FROM inventory.outbox_events");
        jdbc.update("DELETE FROM inventory.assets");
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
        clear(RESULT_ROOT);
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
        UUID actorId = jdbc.queryForObject(
                "SELECT id FROM identity.human_users WHERE normalized_username = 'job.operator'",
                UUID.class);
        assertThat(jdbc.queryForList(
                        """
                        SELECT event_type, event_version, actor_type, actor_id, resource_version,
                               result, correlation_id, payload::text AS payload
                        FROM discovery.outbox_events
                        WHERE resource_id = ?
                        ORDER BY resource_version
                        """,
                        jobId))
                .containsExactly(
                        Map.of(
                                "event_type", "DISCOVERY_JOB_CREATED",
                                "event_version", "2.0",
                                "actor_type", "HUMAN_USER",
                                "actor_id", actorId,
                                "resource_version", 0L,
                                "result", "SUCCESS",
                                "correlation_id", correlationId,
                                "payload", "{}"),
                        Map.of(
                                "event_type", "DISCOVERY_JOB_DISPATCHED",
                                "event_version", "2.0",
                                "actor_type", "HUMAN_USER",
                                "actor_id", actorId,
                                "resource_version", 1L,
                                "result", "SUCCESS",
                                "correlation_id", correlationId,
                                "payload", "{}"));
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

    @Test
    void ingestsASucceededResultIdempotentlyAndListsUnreviewedDevices() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        HttpResponse<String> created = postJob(session, seed, UUID.randomUUID());
        UUID jobId = UUID.fromString(extract(created.body(), "jobId"));
        UUID resultId = UUID.randomUUID();
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class, jobId).toInstant();
        Path mailbox = Files.createDirectory(RESULT_ROOT.resolve(
                "discovery-executor-" + seed.executorId() + "-results"));
        Files.writeString(mailbox.resolve(jobId + ".json"), succeededResult(
                resultId, jobId, seed, dispatchedAt, dispatchedAt));

        results.reconcile();
        results.reconcile();

        assertThat(jdbc.queryForMap(
                        "SELECT status, version, observations_found FROM discovery.discovery_jobs WHERE job_id = ?",
                        jobId))
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("version", 2L)
                .containsEntry("observations_found", 1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.accepted_discovery_results WHERE job_id = ?",
                        Integer.class, jobId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.discovered_devices WHERE job_id = ?",
                        Integer.class, jobId))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT event_type FROM discovery.outbox_events WHERE resource_id = ? ORDER BY resource_version",
                        String.class, jobId))
                .containsExactly("DISCOVERY_JOB_CREATED", "DISCOVERY_JOB_DISPATCHED", "DISCOVERY_JOB_COMPLETED");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_version, actor_type, actor_id, resource_version, result,
                               payload::text AS payload
                        FROM discovery.outbox_events
                        WHERE resource_id = ? AND event_type = 'DISCOVERY_JOB_COMPLETED'
                        """,
                        jobId))
                .containsEntry("event_version", "2.0")
                .containsEntry("actor_type", "DISCOVERY_EXECUTOR")
                .containsEntry("actor_id", seed.executorId())
                .containsEntry("resource_version", 2L)
                .containsEntry("result", "SUCCESS")
                .containsEntry("payload", "{}");
        assertThat(getJob(session, jobId).body())
                .contains("\"status\":\"SUCCEEDED\"")
                .contains("\"observationsFound\":1")
                .contains("\"version\":2");

        HttpResponse<String> devices = send("GET",
                "/api/v1/discovery-jobs/" + jobId + "/discovered-devices", session, null, null);
        assertThat(devices.statusCode()).isEqualTo(200);
        assertThat(devices.body())
                .contains("\"status\":\"UNREVIEWED\"")
                .contains("\"version\":0")
                .contains("\"reviewedAt\":null")
                .contains("\"ipAddress\":\"192.0.2.10\"")
                .contains("\"openTcpPorts\":[22,443]")
                .contains("\"totalElements\":1");
        assertThat(send("GET", "/api/v1/discovery-jobs/" + jobId
                + "/discovered-devices?page=-1", session, null, null).statusCode()).isEqualTo(422);
        assertThat(send("GET", "/api/v1/discovery-jobs/" + jobId
                + "/discovered-devices", null, null, null).statusCode()).isEqualTo(401);
        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'IT_OPERATOR'");
        assertThat(send("GET", "/api/v1/discovery-jobs/" + jobId
                + "/discovered-devices", session, null, null).statusCode()).isEqualTo(403);
    }

    @Test
    void ignoresADeviceOnceAndReturnsCurrentStateForTheNoOp() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> ignored = ignoreDevice(
                session, device.jobId(), device.discoveredDeviceId(), 0, correlationId);

        assertThat(ignored.statusCode()).isEqualTo(200);
        assertThat(ignored.body())
                .contains("\"status\":\"IGNORED\"")
                .contains("\"version\":1")
                .doesNotContain("reviewedByUserId");
        String reviewedAt = extract(ignored.body(), "reviewedAt");
        UUID actorId = jdbc.queryForObject(
                "SELECT id FROM identity.human_users WHERE normalized_username = 'job.operator'",
                UUID.class);
        assertThat(jdbc.queryForMap(
                        "SELECT status, version, reviewed_by_user_id FROM discovery.discovered_devices "
                                + "WHERE discovered_device_id = ?",
                        device.discoveredDeviceId()))
                .containsEntry("status", "IGNORED")
                .containsEntry("version", 1L)
                .containsEntry("reviewed_by_user_id", actorId);
        assertThat(jdbc.queryForMap(
                        "SELECT event_version, producer_module, actor_type, actor_id, resource_type, "
                                + "resource_version, correlation_id, payload FROM discovery.outbox_events "
                                + "WHERE event_type = 'DISCOVERED_DEVICE_IGNORED' AND resource_id = ?",
                        device.discoveredDeviceId()))
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "DISCOVERY")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("actor_id", actorId)
                .containsEntry("resource_type", "DISCOVERED_DEVICE")
                .containsEntry("resource_version", 1L)
                .containsEntry("correlation_id", correlationId);

        HttpResponse<String> noOp = ignoreDevice(
                session, device.jobId(), device.discoveredDeviceId(), 1, UUID.randomUUID());
        assertThat(noOp.statusCode()).isEqualTo(200);
        assertThat(extract(noOp.body(), "reviewedAt")).isEqualTo(reviewedAt);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events "
                                + "WHERE event_type = 'DISCOVERED_DEVICE_IGNORED' AND resource_id = ?",
                        Integer.class,
                        device.discoveredDeviceId()))
                .isEqualTo(1);
        assertThat(ignoreDevice(
                        session, device.jobId(), device.discoveredDeviceId(), 0, UUID.randomUUID())
                .statusCode()).isEqualTo(409);
    }

    @Test
    void enforcesIgnoreAuthorizationValidationOwnershipAndAtomicity() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        String path = "/api/v1/discovery-jobs/" + device.jobId()
                + "/discovered-devices/" + device.discoveredDeviceId() + "/ignore";

        assertThat(send("POST", path, null, "{\"expectedVersion\":0}", UUID.randomUUID()).statusCode())
                .isEqualTo(401);
        HttpRequest noCsrf = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":0}"))
                .build();
        assertThat(http.send(noCsrf, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(send("POST", path, session, "{\"expectedVersion\":-1}", UUID.randomUUID()).statusCode())
                .isEqualTo(422);
        assertThat(send("POST", path, session, "{}", UUID.randomUUID()).statusCode()).isEqualTo(422);
        assertThat(send(
                                "POST",
                                path,
                                session,
                                "{\"expectedVersion\":0,\"unexpected\":true}",
                                UUID.randomUUID())
                        .statusCode())
                .isEqualTo(400);
        assertThat(ignoreDevice(
                        session, UUID.randomUUID(), device.discoveredDeviceId(), 0, UUID.randomUUID())
                .statusCode()).isEqualTo(404);
        assertThat(ignoreDevice(
                        session, device.jobId(), UUID.randomUUID(), 0, UUID.randomUUID())
                .statusCode()).isEqualTo(404);

        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION discovery.reject_test_device_outbox() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced device outbox failure'; END; $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_test_device_outbox BEFORE INSERT ON discovery.outbox_events
                FOR EACH ROW WHEN (NEW.event_type = 'DISCOVERED_DEVICE_IGNORED')
                EXECUTE FUNCTION discovery.reject_test_device_outbox()
                """);
        try {
            assertThat(ignoreDevice(
                            session, device.jobId(), device.discoveredDeviceId(), 0, UUID.randomUUID())
                    .statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForMap(
                            "SELECT status, version, reviewed_at, reviewed_by_user_id "
                                    + "FROM discovery.discovered_devices WHERE discovered_device_id = ?",
                            device.discoveredDeviceId()))
                    .containsEntry("status", "UNREVIEWED")
                    .containsEntry("version", 0L)
                    .containsEntry("reviewed_at", null)
                    .containsEntry("reviewed_by_user_id", null);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_device_outbox ON discovery.outbox_events");
            jdbc.execute("DROP FUNCTION IF EXISTS discovery.reject_test_device_outbox()");
        }
    }

    @Test
    void serializesConcurrentIgnoreRequests() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return ignoreDevice(
                        session, device.jobId(), device.discoveredDeviceId(), 0, UUID.randomUUID())
                        .statusCode();
            });
            var second = executor.submit(() -> {
                start.await();
                return ignoreDevice(
                        session, device.jobId(), device.discoveredDeviceId(), 0, UUID.randomUUID())
                        .statusCode();
            });
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events "
                                + "WHERE event_type = 'DISCOVERED_DEVICE_IGNORED' AND resource_id = ?",
                        Integer.class,
                        device.discoveredDeviceId()))
                .isEqualTo(1);
    }

    @Test
    void incorporatesADeviceOnceIntoTheSameActiveAsset() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        UUID assetId = seedAsset("ACTIVE");
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> incorporated = incorporateDevice(
                session, device.jobId(), device.discoveredDeviceId(), assetId, 0, correlationId);

        assertThat(incorporated.statusCode()).isEqualTo(200);
        assertThat(incorporated.body())
                .contains("\"status\":\"INCORPORATED\"")
                .contains("\"version\":1")
                .contains("\"assetId\":\"" + assetId + "\"")
                .doesNotContain("reviewedByUserId");
        String reviewedAt = extract(incorporated.body(), "reviewedAt");
        UUID actorId = jdbc.queryForObject(
                "SELECT id FROM identity.human_users WHERE normalized_username = 'job.operator'",
                UUID.class);
        assertThat(jdbc.queryForMap(
                        "SELECT status, version, reviewed_by_user_id, inventory_asset_id "
                                + "FROM discovery.discovered_devices WHERE discovered_device_id = ?",
                        device.discoveredDeviceId()))
                .containsEntry("status", "INCORPORATED")
                .containsEntry("version", 1L)
                .containsEntry("reviewed_by_user_id", actorId)
                .containsEntry("inventory_asset_id", assetId);
        assertThat(jdbc.queryForMap(
                        "SELECT event_version, producer_module, actor_type, actor_id, resource_type, "
                                + "resource_version, correlation_id, payload FROM discovery.outbox_events "
                                + "WHERE event_type = 'DISCOVERED_DEVICE_INCORPORATED' AND resource_id = ?",
                        device.discoveredDeviceId()))
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "DISCOVERY")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("actor_id", actorId)
                .containsEntry("resource_type", "DISCOVERED_DEVICE")
                .containsEntry("resource_version", 1L)
                .containsEntry("correlation_id", correlationId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.outbox_events", Integer.class))
                .isZero();
        HttpResponse<String> noOp = incorporateDevice(
                session, device.jobId(), device.discoveredDeviceId(), assetId, 1, UUID.randomUUID());
        assertThat(noOp.statusCode()).isEqualTo(200);
        assertThat(extract(noOp.body(), "reviewedAt")).isEqualTo(reviewedAt);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events "
                                + "WHERE event_type = 'DISCOVERED_DEVICE_INCORPORATED' AND resource_id = ?",
                        Integer.class,
                        device.discoveredDeviceId()))
                .isEqualTo(1);
        assertThat(incorporateDevice(
                        session, device.jobId(), device.discoveredDeviceId(), assetId, 0, UUID.randomUUID())
                .statusCode()).isEqualTo(409);
        assertThat(incorporateDevice(
                        session, device.jobId(), device.discoveredDeviceId(), seedAsset("ACTIVE"), 1,
                        UUID.randomUUID())
                .statusCode()).isEqualTo(409);
        assertThat(ignoreDevice(
                        session, device.jobId(), device.discoveredDeviceId(), 1, UUID.randomUUID())
                .statusCode()).isEqualTo(409);
    }

    @Test
    void enforcesIncorporationAssetValidationOwnershipAndAtomicity() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        UUID activeAssetId = seedAsset("ACTIVE");
        UUID inactiveAssetId = seedAsset("INACTIVE");
        String path = "/api/v1/discovery-jobs/" + device.jobId()
                + "/discovered-devices/" + device.discoveredDeviceId() + "/incorporate";

        assertThat(send("POST", path, null,
                        "{\"assetId\":\"" + activeAssetId + "\",\"expectedVersion\":0}",
                        UUID.randomUUID()).statusCode())
                .isEqualTo(401);
        HttpRequest noCsrf = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"assetId\":\"" + activeAssetId + "\",\"expectedVersion\":0}"))
                .build();
        assertThat(http.send(noCsrf, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(send("POST", path, session,
                        "{\"assetId\":\"00000000-0000-1000-8000-000000000000\",\"expectedVersion\":0}",
                        UUID.randomUUID()).statusCode())
                .isEqualTo(422);
        assertThat(send("POST", path, session,
                        "{\"assetId\":\"" + activeAssetId.toString().toUpperCase()
                                + "\",\"expectedVersion\":0}",
                        UUID.randomUUID()).statusCode())
                .isEqualTo(422);
        assertThat(send("POST", path, session, "{\"expectedVersion\":0}", UUID.randomUUID()).statusCode())
                .isEqualTo(422);
        assertThat(send("POST", path, session,
                        "{\"assetId\":\"" + activeAssetId
                                + "\",\"expectedVersion\":0,\"unexpected\":true}",
                        UUID.randomUUID()).statusCode())
                .isEqualTo(400);
        assertThat(incorporateDevice(
                        session, device.jobId(), device.discoveredDeviceId(), UUID.randomUUID(), 0,
                        UUID.randomUUID()).statusCode())
                .isEqualTo(404);
        assertThat(incorporateDevice(
                        session, device.jobId(), device.discoveredDeviceId(), inactiveAssetId, 0,
                        UUID.randomUUID()).statusCode())
                .isEqualTo(409);
        assertThat(incorporateDevice(
                        session, UUID.randomUUID(), device.discoveredDeviceId(), activeAssetId, 0,
                        UUID.randomUUID()).statusCode())
                .isEqualTo(404);

        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION discovery.reject_test_incorporation_outbox() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced incorporation outbox failure'; END; $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_test_incorporation_outbox BEFORE INSERT ON discovery.outbox_events
                FOR EACH ROW WHEN (NEW.event_type = 'DISCOVERED_DEVICE_INCORPORATED')
                EXECUTE FUNCTION discovery.reject_test_incorporation_outbox()
                """);
        try {
            assertThat(incorporateDevice(
                            session, device.jobId(), device.discoveredDeviceId(), activeAssetId, 0,
                            UUID.randomUUID()).statusCode())
                    .isEqualTo(503);
            assertThat(jdbc.queryForMap(
                            "SELECT status, version, reviewed_at, reviewed_by_user_id, inventory_asset_id "
                                    + "FROM discovery.discovered_devices WHERE discovered_device_id = ?",
                            device.discoveredDeviceId()))
                    .containsEntry("status", "UNREVIEWED")
                    .containsEntry("version", 0L)
                    .containsEntry("reviewed_at", null)
                    .containsEntry("reviewed_by_user_id", null)
                    .containsEntry("inventory_asset_id", null);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_incorporation_outbox ON discovery.outbox_events");
            jdbc.execute("DROP FUNCTION IF EXISTS discovery.reject_test_incorporation_outbox()");
        }
        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'IT_OPERATOR'");
        assertThat(incorporateDevice(
                        session, device.jobId(), device.discoveredDeviceId(), activeAssetId, 0,
                        UUID.randomUUID()).statusCode())
                .isEqualTo(403);
    }

    @Test
    void incorporatesADeviceByCreatingOneActiveAssetAtomically() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> incorporated = incorporateDeviceWithNewAsset(
                session, device.jobId(), device.discoveredDeviceId(), "  Discovered server  ",
                "DESKTOP", 0, correlationId);

        assertThat(incorporated.statusCode()).isEqualTo(200);
        assertThat(incorporated.body())
                .contains("\"status\":\"INCORPORATED\"")
                .contains("\"version\":1")
                .doesNotContain("reviewedByUserId");
        UUID assetId = UUID.fromString(extract(incorporated.body(), "assetId"));
        assertThat(jdbc.queryForMap(
                        "SELECT name, asset_type, status, version FROM inventory.assets WHERE id = ?",
                        assetId))
                .containsEntry("name", "Discovered server")
                .containsEntry("asset_type", "DESKTOP")
                .containsEntry("status", "ACTIVE")
                .containsEntry("version", 0L);
        assertThat(jdbc.queryForMap(
                        "SELECT event_type, resource_id, correlation_id FROM inventory.outbox_events "
                                + "WHERE resource_id = ?",
                        assetId))
                .containsEntry("event_type", "ASSET_CREATED")
                .containsEntry("resource_id", assetId)
                .containsEntry("correlation_id", correlationId);
        assertThat(jdbc.queryForMap(
                        "SELECT event_type, resource_id, correlation_id FROM discovery.outbox_events "
                                + "WHERE resource_id = ? AND event_type = 'DISCOVERED_DEVICE_INCORPORATED'",
                        device.discoveredDeviceId()))
                .containsEntry("event_type", "DISCOVERED_DEVICE_INCORPORATED")
                .containsEntry("resource_id", device.discoveredDeviceId())
                .containsEntry("correlation_id", correlationId);

        assertThat(incorporateDeviceWithNewAsset(
                        session, device.jobId(), device.discoveredDeviceId(), "Another server",
                        "DESKTOP", 1, UUID.randomUUID()).statusCode())
                .isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.assets", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM inventory.outbox_events WHERE event_type = 'ASSET_CREATED'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void validatesAndRollsBackNewAssetIncorporation() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        String path = "/api/v1/discovery-jobs/" + device.jobId()
                + "/discovered-devices/" + device.discoveredDeviceId() + "/incorporate-new-asset";
        String validBody = "{\"name\":\"Discovered server\",\"assetType\":\"DESKTOP\","
                + "\"expectedVersion\":0}";

        assertThat(send("POST", path, null, validBody, UUID.randomUUID()).statusCode()).isEqualTo(401);
        HttpRequest noCsrf = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(validBody))
                .build();
        assertThat(http.send(noCsrf, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(send("POST", path, session,
                        "{\"name\":\" \",\"assetType\":\"DESKTOP\",\"expectedVersion\":0}",
                        UUID.randomUUID()).statusCode())
                .isEqualTo(422);
        assertThat(send("POST", path, session,
                        "{\"name\":\"Server\",\"assetType\":\"ROUTER\",\"expectedVersion\":0}",
                        UUID.randomUUID()).statusCode())
                .isEqualTo(422);
        assertThat(send("POST", path, session,
                        "{\"name\":\"Server\",\"assetType\":\"DESKTOP\","
                                + "\"expectedVersion\":0,\"unexpected\":true}",
                        UUID.randomUUID()).statusCode())
                .isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.assets", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.outbox_events", Integer.class)).isZero();

        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION discovery.reject_test_new_asset_incorporation_outbox()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'forced new asset incorporation outbox failure'; END; $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_test_new_asset_incorporation_outbox
                BEFORE INSERT ON discovery.outbox_events
                FOR EACH ROW WHEN (NEW.event_type = 'DISCOVERED_DEVICE_INCORPORATED')
                EXECUTE FUNCTION discovery.reject_test_new_asset_incorporation_outbox()
                """);
        try {
            assertThat(incorporateDeviceWithNewAsset(
                            session, device.jobId(), device.discoveredDeviceId(), "Discovered server",
                            "DESKTOP", 0, UUID.randomUUID()).statusCode())
                    .isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.assets", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.outbox_events", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForMap(
                            "SELECT status, version, inventory_asset_id FROM discovery.discovered_devices "
                                    + "WHERE discovered_device_id = ?",
                            device.discoveredDeviceId()))
                    .containsEntry("status", "UNREVIEWED")
                    .containsEntry("version", 0L)
                    .containsEntry("inventory_asset_id", null);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_new_asset_incorporation_outbox "
                    + "ON discovery.outbox_events");
            jdbc.execute("DROP FUNCTION IF EXISTS discovery.reject_test_new_asset_incorporation_outbox()");
        }
        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'IT_OPERATOR'");
        assertThat(incorporateDeviceWithNewAsset(
                        session, device.jobId(), device.discoveredDeviceId(), "Discovered server",
                        "DESKTOP", 0, UUID.randomUUID()).statusCode())
                .isEqualTo(403);
    }

    @Test
    void serializesConcurrentNewAssetIncorporationWithoutDuplicates() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return incorporateDeviceWithNewAsset(
                        session, device.jobId(), device.discoveredDeviceId(),
                        "First server", "DESKTOP", 0, UUID.randomUUID()).statusCode();
            });
            var second = executor.submit(() -> {
                start.await();
                return incorporateDeviceWithNewAsset(
                        session, device.jobId(), device.discoveredDeviceId(),
                        "Second server", "DESKTOP", 0, UUID.randomUUID()).statusCode();
            });
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.assets", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM inventory.outbox_events WHERE event_type = 'ASSET_CREATED'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events WHERE resource_id = ? "
                                + "AND event_type = 'DISCOVERED_DEVICE_INCORPORATED'",
                        Integer.class,
                        device.discoveredDeviceId()))
                .isEqualTo(1);
    }

    @Test
    void serializesConcurrentIgnoreAndIncorporateRequests() throws Exception {
        Session session = login();
        DeviceSeed device = seedSucceededDevice(session);
        UUID assetId = seedAsset("ACTIVE");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var ignored = executor.submit(() -> {
                start.await();
                return ignoreDevice(
                        session, device.jobId(), device.discoveredDeviceId(), 0, UUID.randomUUID())
                        .statusCode();
            });
            var incorporated = executor.submit(() -> {
                start.await();
                return incorporateDevice(
                        session, device.jobId(), device.discoveredDeviceId(), assetId, 0, UUID.randomUUID())
                        .statusCode();
            });
            start.countDown();
            assertThat(List.of(ignored.get(), incorporated.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events WHERE resource_id = ? "
                                + "AND event_type IN ('DISCOVERED_DEVICE_IGNORED', "
                                + "'DISCOVERED_DEVICE_INCORPORATED')",
                        Integer.class,
                        device.discoveredDeviceId()))
                .isEqualTo(1);
    }

    @Test
    void rejectsChangedBytesAfterAcceptingAResultWithoutMutatingTheTerminalJob() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        UUID jobId = UUID.fromString(extract(
                postJob(session, seed, UUID.randomUUID()).body(), "jobId"));
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class, jobId).toInstant();
        Path mailbox = Files.createDirectory(RESULT_ROOT.resolve(
                "discovery-executor-" + seed.executorId() + "-results"));
        Path result = mailbox.resolve(jobId + ".json");
        String accepted = succeededResult(UUID.randomUUID(), jobId, seed, dispatchedAt, dispatchedAt);
        Files.writeString(result, accepted);
        results.reconcile();
        assertThat(result).doesNotExist();
        Path replacement = Files.writeString(mailbox.resolve(jobId + ".json.part"), "{\"changed\":true}");
        Files.move(replacement, result, StandardCopyOption.ATOMIC_MOVE);

        results.reconcile();

        assertThat(result).doesNotExist();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.accepted_discovery_results WHERE job_id = ?",
                        Integer.class, jobId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.discovery_result_ingestions WHERE status = 'CONFLICT'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events WHERE resource_id = ? AND event_type = 'DISCOVERY_JOB_COMPLETED'",
                        Integer.class, jobId))
                .isEqualTo(1);
    }

    @Test
    void ingestsAFailedResultWithoutCreatingDevicesAndIgnoresPartFiles() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        UUID jobId = UUID.fromString(extract(
                postJob(session, seed, UUID.randomUUID()).body(), "jobId"));
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class, jobId).toInstant();
        Path mailbox = Files.createDirectory(RESULT_ROOT.resolve(
                "discovery-executor-" + seed.executorId() + "-results"));
        String result = failedResult(UUID.randomUUID(), jobId, seed, dispatchedAt);
        Files.writeString(mailbox.resolve(jobId + ".json.part"), result);

        results.reconcile();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?",
                        String.class, jobId))
                .isEqualTo("DISPATCHED");

        Files.writeString(mailbox.resolve(jobId + ".json"), result);
        results.reconcile();

        assertThat(jdbc.queryForMap(
                        "SELECT status, failure_code, version FROM discovery.discovery_jobs WHERE job_id = ?",
                        jobId))
                .containsEntry("status", "FAILED")
                .containsEntry("failure_code", "EXECUTION_ERROR")
                .containsEntry("version", 2L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.discovered_devices WHERE job_id = ?",
                        Integer.class, jobId))
                .isZero();
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_version, actor_type, actor_id, resource_version, result,
                               payload::text AS payload
                        FROM discovery.outbox_events
                        WHERE resource_id = ? AND event_type = 'DISCOVERY_JOB_COMPLETED'
                        """,
                        jobId))
                .containsEntry("event_version", "2.0")
                .containsEntry("actor_type", "DISCOVERY_EXECUTOR")
                .containsEntry("actor_id", seed.executorId())
                .containsEntry("resource_version", 2L)
                .containsEntry("result", "FAILED")
                .containsEntry("payload", "{}");
    }

    @Test
    void retainsAClaimAcrossQuotaConflictWithoutExpiringTheJob() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        UUID jobId = UUID.fromString(extract(
                postJob(session, seed, UUID.randomUUID()).body(), "jobId"));
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class, jobId).toInstant();
        Path mailbox = Files.createDirectory(RESULT_ROOT.resolve(
                "discovery-executor-" + seed.executorId() + "-results"));
        Files.writeString(mailbox.resolve(jobId + ".json"), succeededResult(
                UUID.randomUUID(), jobId, seed, dispatchedAt, dispatchedAt));
        when(provisioner.startResultQuotaScan(seed.executorId())).thenReturn(false, true);

        results.reconcile();

        assertThat(mailbox.resolve(jobId + ".json")).doesNotExist();
        jdbc.update("UPDATE discovery.discovery_jobs "
                + "SET created_at = now() - interval '1 hour 1 second', "
                + "dispatched_at = now() - interval '1 hour', "
                + "expires_at = now() - interval '1 second', "
                + "updated_at = now() WHERE job_id = ?", jobId);
        jobs.reconcilePending();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("DISPATCHED");
        jdbc.update("UPDATE discovery.discovery_jobs "
                + "SET created_at = now(), expires_at = now() + interval '1 hour', updated_at = now() "
                + "WHERE job_id = ?",
                jobId);

        results.reconcile();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("SUCCEEDED");
        try (var claims = Files.walk(RESULT_ROOT.resolve(".jaxon-claims"))) {
            assertThat(claims.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void doesNotExpireAJobWhoseExactFinalIsWaitingToBeClaimed() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        UUID jobId = UUID.fromString(extract(
                postJob(session, seed, UUID.randomUUID()).body(), "jobId"));
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class, jobId).toInstant();
        Path mailbox = Files.createDirectory(RESULT_ROOT.resolve(
                "discovery-executor-" + seed.executorId() + "-results"));
        Files.writeString(mailbox.resolve(jobId + ".json"), succeededResult(
                UUID.randomUUID(), jobId, seed, dispatchedAt, dispatchedAt));
        jdbc.update("UPDATE discovery.discovery_jobs "
                + "SET created_at = now() - interval '1 hour 1 second', "
                + "dispatched_at = now() - interval '1 hour', "
                + "expires_at = now() - interval '1 second', updated_at = now() WHERE job_id = ?",
                jobId);

        jobs.reconcilePending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("DISPATCHED");
        assertThat(mailbox.resolve(jobId + ".json")).isRegularFile();
    }

    @Test
    void rejectsAndRemovesAFinalPublishedAfterExpiration() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        UUID jobId = UUID.fromString(extract(
                postJob(session, seed, UUID.randomUUID()).body(), "jobId"));
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class, jobId).toInstant();
        jdbc.update("UPDATE discovery.discovery_jobs "
                + "SET created_at = now() - interval '1 hour 1 second', "
                + "dispatched_at = now() - interval '1 hour', "
                + "expires_at = now() - interval '1 second', updated_at = now() WHERE job_id = ?",
                jobId);
        jobs.reconcilePending();
        Path mailbox = Files.createDirectory(RESULT_ROOT.resolve(
                "discovery-executor-" + seed.executorId() + "-results"));
        Path lateFinal = Files.writeString(mailbox.resolve(jobId + ".json"), succeededResult(
                UUID.randomUUID(), jobId, seed, dispatchedAt, dispatchedAt));

        results.reconcile();

        assertThat(lateFinal).doesNotExist();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForMap(
                        "SELECT status, reason_code FROM discovery.discovery_result_ingestions "
                                + "WHERE path_job_id = ?", jobId))
                .containsEntry("status", "REJECTED")
                .containsEntry("reason_code", "JOB_TERMINAL_OR_NOT_DISPATCHED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events "
                                + "WHERE resource_id = ? AND event_type = 'DISCOVERY_JOB_COMPLETED'",
                        Integer.class,
                        jobId))
                .isZero();
    }

    @Test
    void serializesConcurrentResultsAndCompletesTheJobExactlyOnce() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        UUID jobId = UUID.fromString(extract(
                postJob(session, seed, UUID.randomUUID()).body(), "jobId"));
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class, jobId).toInstant();
        DiscoveryResultMessage first = resultMessage(UUID.randomUUID(), jobId, seed, dispatchedAt);
        DiscoveryResultMessage second = resultMessage(UUID.randomUUID(), jobId, seed, dispatchedAt);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstFuture = executor.submit(() -> {
                start.await();
                return ingestion.ingest(seed.executorId(), jobId, "mailbox/" + jobId + ".json",
                        "a".repeat(64), 100, first);
            });
            var secondFuture = executor.submit(() -> {
                start.await();
                return ingestion.ingest(seed.executorId(), jobId, "mailbox/" + jobId + ".json",
                        "b".repeat(64), 100, second);
            });
            start.countDown();
            assertThat(List.of(firstFuture.get(), secondFuture.get()))
                    .containsExactlyInAnyOrder(
                            DiscoveryResultIngestionService.IngestionOutcome.ACCEPTED,
                            DiscoveryResultIngestionService.IngestionOutcome.CONFLICT);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events "
                                + "WHERE resource_id = ? AND event_type = 'DISCOVERY_JOB_COMPLETED'",
                        Integer.class, jobId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.accepted_discovery_results WHERE job_id = ?",
                        Integer.class, jobId))
                .isEqualTo(1);
    }

    @Test
    void rollsBackOfficialCompletionWhenItsVersionTwoOutboxCannotBeCaptured() throws Exception {
        Session session = login();
        Seed seed = seedActiveExecutor(true);
        UUID jobId = UUID.fromString(extract(
                postJob(session, seed, UUID.randomUUID()).body(), "jobId"));
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class,
                jobId).toInstant();
        DiscoveryResultMessage message = resultMessage(UUID.randomUUID(), jobId, seed, dispatchedAt);
        jdbc.execute(
                """
                CREATE FUNCTION discovery.reject_test_job_completion_outbox()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'DISCOVERY_JOB_COMPLETED' THEN
                        RAISE EXCEPTION 'forced job completion outbox failure';
                    END IF;
                    RETURN NEW;
                END $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_test_job_completion_outbox
                BEFORE INSERT ON discovery.outbox_events
                FOR EACH ROW EXECUTE FUNCTION discovery.reject_test_job_completion_outbox()
                """);
        try {
            assertThatThrownBy(() -> ingestion.ingest(
                            seed.executorId(),
                            jobId,
                            "mailbox/" + jobId + ".json",
                            "c".repeat(64),
                            100,
                            message))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_job_completion_outbox "
                    + "ON discovery.outbox_events");
            jdbc.execute("DROP FUNCTION IF EXISTS discovery.reject_test_job_completion_outbox()");
        }
        assertThat(jdbc.queryForMap(
                        "SELECT status, version FROM discovery.discovery_jobs WHERE job_id = ?",
                        jobId))
                .containsEntry("status", "DISPATCHED")
                .containsEntry("version", 1L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.accepted_discovery_results WHERE job_id = ?",
                        Integer.class,
                        jobId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.discovery_result_ingestions WHERE path_job_id = ?",
                        Integer.class,
                        jobId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events "
                                + "WHERE resource_id = ? AND event_type = 'DISCOVERY_JOB_COMPLETED'",
                        Integer.class,
                        jobId))
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
        when(provisioner.startResultQuotaScan(executorId)).thenReturn(true);
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

    private DeviceSeed seedSucceededDevice(Session session) throws Exception {
        Seed seed = seedActiveExecutor(true);
        UUID jobId = UUID.fromString(extract(postJob(session, seed, UUID.randomUUID()).body(), "jobId"));
        Instant dispatchedAt = jdbc.queryForObject(
                "SELECT dispatched_at FROM discovery.discovery_jobs WHERE job_id = ?",
                java.sql.Timestamp.class,
                jobId).toInstant();
        Path mailbox = Files.createDirectory(RESULT_ROOT.resolve(
                "discovery-executor-" + seed.executorId() + "-results"));
        Files.writeString(mailbox.resolve(jobId + ".json"), succeededResult(
                UUID.randomUUID(), jobId, seed, dispatchedAt, dispatchedAt));
        results.reconcile();
        UUID discoveredDeviceId = jdbc.queryForObject(
                "SELECT discovered_device_id FROM discovery.discovered_devices WHERE job_id = ?",
                UUID.class,
                jobId);
        return new DeviceSeed(jobId, discoveredDeviceId);
    }

    private HttpResponse<String> ignoreDevice(
            Session session, UUID jobId, UUID discoveredDeviceId, long expectedVersion, UUID correlationId)
            throws Exception {
        return send(
                "POST",
                "/api/v1/discovery-jobs/" + jobId + "/discovered-devices/"
                        + discoveredDeviceId + "/ignore",
                session,
                "{\"expectedVersion\":" + expectedVersion + "}",
                correlationId);
    }

    private HttpResponse<String> incorporateDevice(
            Session session,
            UUID jobId,
            UUID discoveredDeviceId,
            UUID assetId,
            long expectedVersion,
            UUID correlationId) throws Exception {
        return send(
                "POST",
                "/api/v1/discovery-jobs/" + jobId + "/discovered-devices/"
                        + discoveredDeviceId + "/incorporate",
                session,
                "{\"assetId\":\"" + assetId + "\",\"expectedVersion\":" + expectedVersion + "}",
                correlationId);
    }

    private HttpResponse<String> incorporateDeviceWithNewAsset(
            Session session,
            UUID jobId,
            UUID discoveredDeviceId,
            String name,
            String assetType,
            long expectedVersion,
            UUID correlationId) throws Exception {
        return send(
                "POST",
                "/api/v1/discovery-jobs/" + jobId + "/discovered-devices/"
                        + discoveredDeviceId + "/incorporate-new-asset",
                session,
                "{\"name\":\"" + name + "\",\"assetType\":\"" + assetType
                        + "\",\"expectedVersion\":" + expectedVersion + "}",
                correlationId);
    }

    private UUID seedAsset(String status) {
        UUID assetId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO inventory.assets (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, ?, 'DESKTOP', ?, now(), now(), 0)
                """,
                assetId,
                "Discovery target " + assetId,
                status);
        return assetId;
    }

    private static String succeededResult(
            UUID resultId, UUID jobId, Seed seed, Instant startedAt, Instant completedAt) {
        return """
                {
                  "schemaVersion":"1.0",
                  "messageType":"DISCOVERY_RESULT",
                  "resultId":"%s",
                  "jobId":"%s",
                  "executorId":"%s",
                  "scopeId":"%s",
                  "profile":"BASIC",
                  "startedAt":"%s",
                  "completedAt":"%s",
                  "status":"SUCCEEDED",
                  "failureCode":null,
                  "summary":{"targetsConsidered":256,"targetsScanned":256,"observationsFound":1},
                  "observations":[{"ipAddress":"192.0.2.10","macAddress":"02:00:5e:10:00:01",
                    "hostname":"host.example.internal","evidence":["ARP","TCP"],"openTcpPorts":[22,443]}]
                }
                """.formatted(resultId, jobId, seed.executorId(), seed.scopeId(), startedAt, completedAt);
    }

    private static String failedResult(UUID resultId, UUID jobId, Seed seed, Instant timestamp) {
        return """
                {
                  "schemaVersion":"1.0",
                  "messageType":"DISCOVERY_RESULT",
                  "resultId":"%s",
                  "jobId":"%s",
                  "executorId":"%s",
                  "scopeId":"%s",
                  "profile":"BASIC",
                  "startedAt":"%s",
                  "completedAt":"%s",
                  "status":"FAILED",
                  "failureCode":"EXECUTION_ERROR",
                  "summary":{"targetsConsidered":256,"targetsScanned":0,"observationsFound":0},
                  "observations":[]
                }
                """.formatted(resultId, jobId, seed.executorId(), seed.scopeId(), timestamp, timestamp);
    }

    private static DiscoveryResultMessage resultMessage(
            UUID resultId, UUID jobId, Seed seed, Instant timestamp) {
        return new DiscoveryResultMessage(
                "1.0", resultId, jobId, seed.executorId(), seed.scopeId(), "BASIC",
                timestamp, timestamp, "SUCCEEDED", null,
                new DiscoveryResultMessage.Summary(BigInteger.valueOf(256), 256, 0), List.of());
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

    private static void clear(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(root)) Files.delete(path);
            }
        }
    }

    private record Session(String cookie, String csrfToken) {}
    private record Seed(UUID executorId, UUID scopeId) {}
    private record DeviceSeed(UUID jobId, UUID discoveredDeviceId) {}
}
