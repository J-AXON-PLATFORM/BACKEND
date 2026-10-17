package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
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
            "jaxon.identity.bootstrap.username=Collector.Operator",
            "jaxon.identity.bootstrap.password=Collector integration passphrase 42!"
        })
class SnapshotProcessingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @TempDir
    Path mailboxRoot;

    @Autowired JdbcTemplate jdbc;
    @Autowired SnapshotFileReader fileReader;
    @Autowired SnapshotContractValidator contractValidator;
    @Autowired SnapshotIngestionService ingestionService;
    @Autowired TechnicalStateService technicalStateService;
    @Autowired MeterRegistry meterRegistry;
    @Autowired Clock clock;
    @LocalServerPort int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void clearCollectorFixtures() {
        jdbc.update("DELETE FROM collector.accepted_snapshots");
        jdbc.update("DELETE FROM collector.snapshot_ingestions");
        jdbc.update("DELETE FROM collector.agents");
        jdbc.update("DELETE FROM identity.technical_identities");
        jdbc.update("DELETE FROM inventory.assets");
        restoreOperatorRole();
    }

    @Test
    void reconcilesFinalSnapshotsIdempotentlyAndKeepsLatestByCapturedAt() throws Exception {
        UUID assetId = seedAsset();
        UUID agentId = seedActiveAgent(assetId);
        Path directory = Files.createDirectory(mailboxRoot.resolve("agent-" + agentId + "-snapshots"));
        UUID newestMessage = UUID.randomUUID();
        UUID newestSnapshot = UUID.randomUUID();
        String newestJson = snapshot(
                agentId, newestMessage, newestSnapshot, "2026-10-16T12:00:00Z", "newest-host");
        writeFinal(directory, newestMessage, newestJson);
        Files.writeString(directory.resolve(UUID.randomUUID() + ".json.part"), "not-final");

        reconciler().reconcile();
        reconciler().reconcile();

        assertThat(count("collector.snapshot_ingestions")).isEqualTo(1);
        assertThat(count("collector.accepted_snapshots")).isEqualTo(1);
        assertThat(jdbc.queryForMap(
                        "SELECT message_id, agent_id, content_sha256 FROM collector.accepted_snapshots"))
                .containsEntry("message_id", newestMessage)
                .containsEntry("agent_id", agentId);
        String contentSha256 = jdbc.queryForObject(
                "SELECT content_sha256 FROM collector.accepted_snapshots WHERE message_id = ?",
                String.class,
                newestMessage);
        assertThat(ingestionService.ingest(
                        agentId,
                        newestMessage,
                        "duplicate-copy/" + newestMessage + ".json",
                        contentSha256,
                        newestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        contractValidator.validate(newestJson)))
                .isEqualTo(SnapshotIngestionService.IngestionOutcome.DUPLICATE);
        assertThat(count("collector.snapshot_ingestions")).isEqualTo(1);

        UUID olderMessage = UUID.randomUUID();
        UUID olderSnapshot = UUID.randomUUID();
        writeFinal(directory, olderMessage,
                snapshot(agentId, olderMessage, olderSnapshot, "2026-10-15T12:00:00Z", "older-host"));
        reconciler().reconcile();

        TechnicalStateResponse state = technicalStateService.find(assetId).orElseThrow();
        assertThat(state.lastSeenAt()).isNotNull();
        assertThat(state.lastSnapshotAt()).isEqualTo(Instant.parse("2026-10-16T12:00:00Z"));
        assertThat(state.latestObservation().snapshotId()).isEqualTo(newestSnapshot);
        assertThat(state.latestObservation().hostname()).isEqualTo("newest-host");
        assertThat(state.latestObservation().storageVolumes()).singleElement()
                .extracting("availableBytes")
                .isEqualTo(400L);
        HttpResponse<String> response = getTechnicalState(login(), assetId.toString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"snapshotId\":\"" + newestSnapshot + "\"")
                .contains("\"hostname\":\"newest-host\"")
                .contains("\"lastSeenAt\"")
                .contains("\"lastSnapshotAt\":\"2026-10-16T12:00:00Z\"");
    }

    @Test
    void rejectsIdentityAndPathMismatchesAndRecordsIdentifierConflicts() throws Exception {
        UUID assetId = seedAsset();
        UUID agentId = seedActiveAgent(assetId);
        Path directory = Files.createDirectory(mailboxRoot.resolve("agent-" + agentId + "-snapshots"));
        UUID messageId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Path file = writeFinal(directory, messageId,
                snapshot(agentId, messageId, snapshotId, "2026-10-16T12:00:00Z", "first-host"));
        reconciler().reconcile();

        Files.writeString(file,
                snapshot(agentId, messageId, UUID.randomUUID(), "2026-10-16T13:00:00Z", "conflict-host"));
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(5)));
        UUID mismatchedMessage = UUID.randomUUID();
        writeFinal(directory, mismatchedMessage,
                snapshot(agentId, UUID.randomUUID(), UUID.randomUUID(), "2026-10-16T14:00:00Z", "wrong-path"));
        reconciler().reconcile();

        assertThat(count("collector.accepted_snapshots")).isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT reason_code FROM collector.snapshot_ingestions WHERE status <> 'ACCEPTED' ORDER BY reason_code",
                        String.class))
                .containsExactly("MESSAGE_ID_CONFLICT", "PATH_MESSAGE_ID_MISMATCH");
        assertThat(jdbc.queryForList(
                        "SELECT status FROM collector.snapshot_ingestions ORDER BY received_at", String.class))
                .containsExactlyInAnyOrder("ACCEPTED", "CONFLICT", "REJECTED");
    }

    @Test
    void returnsAnEmptyTechnicalStateOnlyForAnExistingAsset() {
        UUID assetId = seedAsset();

        TechnicalStateResponse empty = technicalStateService.find(assetId).orElseThrow();

        assertThat(empty.assetId()).isEqualTo(assetId);
        assertThat(empty.lastSeenAt()).isNull();
        assertThat(empty.lastSnapshotAt()).isNull();
        assertThat(empty.latestObservation()).isNull();
        assertThat(technicalStateService.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void retainedProcessedFilesDoNotConsumeTheBatch() throws Exception {
        UUID assetId = seedAsset();
        UUID agentId = seedActiveAgent(assetId);
        Path directory = Files.createDirectory(mailboxRoot.resolve("agent-" + agentId + "-snapshots"));
        UUID firstMessage = UUID.fromString("00000000-0000-4000-8000-000000000001");
        writeFinal(directory, firstMessage,
                snapshot(agentId, firstMessage, UUID.randomUUID(), "2026-10-16T12:00:00Z", "first-host"));
        SnapshotReconciler reconciler = reconciler(1);
        reconciler.reconcile();

        UUID secondMessage = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        writeFinal(directory, secondMessage,
                snapshot(agentId, secondMessage, UUID.randomUUID(), "2026-10-16T13:00:00Z", "second-host"));
        reconciler.reconcile();

        assertThat(count("collector.accepted_snapshots")).isEqualTo(2);
    }

    @Test
    void protectsTechnicalStateAndUsesContractualEmptyAndNotFoundResponses() throws Exception {
        UUID assetId = seedAsset();
        String cookie = login();

        HttpResponse<String> empty = getTechnicalState(cookie, assetId.toString());
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.body())
                .contains("\"assetId\":\"" + assetId + "\"")
                .contains("\"lastSeenAt\":null")
                .contains("\"lastSnapshotAt\":null")
                .contains("\"latestObservation\":null");
        assertThat(getTechnicalState(null, assetId.toString()).statusCode()).isEqualTo(401);
        assertThat(getTechnicalState(cookie, "not-a-uuid").statusCode()).isEqualTo(404);
        assertThat(getTechnicalState(cookie, UUID.randomUUID().toString()).statusCode()).isEqualTo(404);

        jdbc.update(
                """
                DELETE FROM identity.human_user_roles
                WHERE role_code = 'IT_OPERATOR'
                  AND user_id = (SELECT id FROM identity.human_users
                                 WHERE normalized_username = 'collector.operator')
                """);
        try {
            assertThat(getTechnicalState(cookie, assetId.toString()).statusCode()).isEqualTo(403);
        } finally {
            restoreOperatorRole();
        }
    }

    private SnapshotReconciler reconciler() {
        return reconciler(100);
    }

    private SnapshotReconciler reconciler(int batchSize) {
        return new SnapshotReconciler(
                mailboxRoot.toString(),
                Duration.ZERO,
                batchSize,
                fileReader,
                contractValidator,
                ingestionService,
                new CollectorMetrics(meterRegistry),
                clock);
    }

    private Path writeFinal(Path directory, UUID messageId, String json) throws Exception {
        Path file = directory.resolve(messageId + ".json");
        Files.writeString(file, json);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(5)));
        return file;
    }

    private UUID seedAsset() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO inventory.assets
                    (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, ?, 'DESKTOP', 'ACTIVE', now(), now(), 0)
                """,
                id,
                "Snapshot asset " + id);
        return id;
    }

    private UUID seedActiveAgent(UUID assetId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO collector.agents
                    (id, asset_id, status, sftp_username, activated_at, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', ?, now(), now(), now(), 1)
                """,
                id,
                assetId,
                "agent-" + id);
        jdbc.update(
                """
                INSERT INTO identity.technical_identities
                    (id, agent_id, identity_type, public_key, public_key_fingerprint,
                     created_at, updated_at, version)
                VALUES (?, ?, 'AGENT', 'ssh-ed25519 AAAA', ?, now(), now(), 1)
                """,
                UUID.randomUUID(),
                id,
                "SHA256:" + id.toString().replace("-", ""));
        return id;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private String login() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(uri("/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"Collector.Operator\",\"password\":\"Collector integration passphrase 42!\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String setCookie = response.headers().firstValue("set-cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> getTechnicalState(String cookie, String assetId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/assets/" + assetId + "/technical-state"))
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private void restoreOperatorRole() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'IT_OPERATOR' FROM identity.human_users
                WHERE normalized_username = 'collector.operator'
                ON CONFLICT DO NOTHING
                """);
    }

    private static String snapshot(
            UUID agentId, UUID messageId, UUID snapshotId, String capturedAt, String hostname) {
        return SnapshotContractValidatorTest.valid(agentId, messageId, snapshotId)
                .replace("2026-10-16T12:00:00Z", capturedAt)
                .replace("host.example", hostname);
    }
}
