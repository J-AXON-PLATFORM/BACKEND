package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired AgentOperationalStateService agentOperationalStates;
    @Autowired MeterRegistry meterRegistry;
    @Autowired TechnicalStateEventBroadcaster eventBroadcaster;
    @Autowired ApplicationEventPublisher applicationEvents;
    @Autowired TransactionTemplate transactions;
    @Autowired Clock clock;
    @LocalServerPort int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void clearCollectorFixtures() {
        eventBroadcaster.close();
        jdbc.update("DELETE FROM collector.accepted_snapshots");
        jdbc.update("DELETE FROM collector.snapshot_ingestions");
        jdbc.update("DELETE FROM collector.agents");
        jdbc.update("DELETE FROM identity.technical_identities");
        jdbc.update("DELETE FROM inventory.assets");
        restoreOperatorRole();
    }

    @AfterEach
    void closeEventStreams() {
        eventBroadcaster.close();
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
                        contractValidator.validate(newestJson),
                        UUID.randomUUID()))
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
    void derivesAgentStatusOnlyFromAcceptedSnapshotsWithControlledTime() throws Exception {
        UUID assetId = seedAsset();
        UUID agentId = seedActiveAgent(assetId);
        Path directory = Files.createDirectory(mailboxRoot.resolve("agent-" + agentId + "-snapshots"));

        assertThat(agentOperationalStates.find(agentId))
                .isEqualTo(AgentOperationalStateService.AgentOperationalState.unknown());

        Files.writeString(directory.resolve(UUID.randomUUID() + ".json.part"), "partial");
        UUID invalidMessage = UUID.randomUUID();
        writeFinal(directory, invalidMessage, "{}");
        reconciler().reconcile();
        assertThat(agentOperationalStates.find(agentId))
                .isEqualTo(AgentOperationalStateService.AgentOperationalState.unknown());

        UUID messageId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Instant capturedAt = Instant.parse("2026-10-18T09:00:00Z");
        String json = snapshot(agentId, messageId, snapshotId, capturedAt.toString(), "status-host");
        Path acceptedFile = writeFinal(directory, messageId, json);
        reconciler().reconcile();

        UUID olderMessageId = UUID.randomUUID();
        String olderJson = snapshot(
                agentId,
                olderMessageId,
                UUID.randomUUID(),
                "2026-10-18T08:00:00Z",
                "older-status-host");
        assertThat(ingestionService.ingest(
                        agentId,
                        olderMessageId,
                        "direct/" + olderMessageId + ".json",
                        "b".repeat(64),
                        olderJson.getBytes(StandardCharsets.UTF_8).length,
                        contractValidator.validate(olderJson),
                        UUID.randomUUID()))
                .isEqualTo(SnapshotIngestionService.IngestionOutcome.ACCEPTED);
        Instant latestContact = Instant.now(clock).minusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                "UPDATE collector.accepted_snapshots SET received_at = ? WHERE message_id = ?",
                Timestamp.from(latestContact.minusSeconds(1)),
                messageId);
        jdbc.update(
                "UPDATE collector.accepted_snapshots SET received_at = ? WHERE message_id = ?",
                Timestamp.from(latestContact),
                olderMessageId);

        AgentOperationalStateService.AgentOperationalState accepted = agentOperationalStates.find(agentId);
        assertThat(accepted.operationalStatus()).isEqualTo(AgentOperationalStatus.ONLINE);
        assertThat(accepted.lastSeenAt()).isEqualTo(latestContact);
        assertThat(accepted.lastSnapshotAt()).isEqualTo(capturedAt);
        assertThat(accepted.lastSeenAt()).isNotEqualTo(accepted.lastSnapshotAt());

        String sha256 = jdbc.queryForObject(
                "SELECT content_sha256 FROM collector.accepted_snapshots WHERE message_id = ?",
                String.class,
                messageId);
        assertThat(ingestionService.ingest(
                        agentId,
                        messageId,
                        "duplicate/" + messageId + ".json",
                        sha256,
                        json.getBytes(StandardCharsets.UTF_8).length,
                        contractValidator.validate(json),
                        UUID.randomUUID()))
                .isEqualTo(SnapshotIngestionService.IngestionOutcome.DUPLICATE);

        Files.writeString(acceptedFile,
                snapshot(agentId, messageId, UUID.randomUUID(), "2026-10-18T10:00:00Z", "conflict"));
        Files.setLastModifiedTime(acceptedFile, FileTime.from(Instant.now().minusSeconds(5)));
        UUID rejectedPath = UUID.randomUUID();
        writeFinal(directory, rejectedPath,
                snapshot(agentId, UUID.randomUUID(), UUID.randomUUID(),
                        "2026-10-18T11:00:00Z", "rejected"));
        reconciler().reconcile();

        AgentOperationalStateService.AgentOperationalState unchanged = agentOperationalStates.find(agentId);
        assertThat(unchanged.lastSeenAt()).isEqualTo(accepted.lastSeenAt());
        assertThat(unchanged.lastSnapshotAt()).isEqualTo(accepted.lastSnapshotAt());
        assertThat(jdbc.queryForList(
                        "SELECT status FROM collector.snapshot_ingestions ORDER BY status", String.class))
                .contains("ACCEPTED", "CONFLICT", "REJECTED");

        Clock boundaryClock = Clock.fixed(accepted.lastSeenAt().plus(Duration.ofMinutes(5)), ZoneOffset.UTC);
        AgentOperationalStateService boundaryStates = new AgentOperationalStateService(
                jdbc, boundaryClock, Duration.ofMinutes(5));
        assertThat(boundaryStates.find(agentId).operationalStatus())
                .isEqualTo(AgentOperationalStatus.ONLINE);

        jdbc.update(
                "UPDATE collector.accepted_snapshots SET received_at = ? WHERE agent_id = ?",
                Timestamp.from(accepted.lastSeenAt().minusNanos(1_000)),
                agentId);
        assertThat(boundaryStates.find(agentId).operationalStatus())
                .isEqualTo(AgentOperationalStatus.OFFLINE);
        assertThat(boundaryStates.find(agentId).lastSnapshotAt()).isEqualTo(capturedAt);

        jdbc.update(
                "UPDATE collector.accepted_snapshots SET received_at = ? WHERE agent_id = ?",
                Timestamp.from(Instant.now(clock).minus(Duration.ofMinutes(6))),
                agentId);
        assertThat(agentStatusGauge("jaxon.collector.agents.administrative", "active")).isEqualTo(1);
        assertThat(agentStatusGauge("jaxon.collector.agents.operational", "offline")).isEqualTo(1);
        assertThat(agentStatusGauge("jaxon.collector.agents.operational", "online")).isZero();
        assertThat(agentStatusGauge("jaxon.collector.agents.operational", "unknown")).isZero();
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

    @Test
    void publishesOneIsolatedEventOnlyAfterAnAcceptedSnapshotCommits() throws Exception {
        UUID assetId = seedAsset();
        UUID otherAssetId = seedAsset();
        UUID agentId = seedActiveAgent(assetId);
        Path directory = Files.createDirectory(mailboxRoot.resolve("agent-" + agentId + "-snapshots"));
        String cookie = login();
        HttpResponse<InputStream> stream = getTechnicalStateEvents(cookie, assetId.toString());
        HttpResponse<InputStream> otherStream = getTechnicalStateEvents(cookie, otherAssetId.toString());
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("text/event-stream"));

        double sentBefore = sseCounter("jaxon.collector.sse.events.sent");
        UUID messageId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        String json = snapshot(agentId, messageId, snapshotId, "2026-10-17T20:00:00Z", "sse-host");
        Path accepted = writeFinal(directory, messageId, json);
        reconciler().reconcile();

        String event = readEvent(stream.body());
        assertThat(event)
                .contains("event:ASSET_TECHNICAL_STATE_UPDATED")
                .contains("data:{\"eventId\":")
                .contains("\"eventType\":\"ASSET_TECHNICAL_STATE_UPDATED\"")
                .contains("\"assetId\":\"" + assetId + "\"")
                .contains("\"correlationId\":")
                .doesNotContain("sse-host", snapshotId.toString(), agentId.toString());
        awaitSseCounter("jaxon.collector.sse.events.sent", sentBefore + 1);

        String sha256 = jdbc.queryForObject(
                "SELECT content_sha256 FROM collector.accepted_snapshots WHERE message_id = ?",
                String.class,
                messageId);
        assertThat(ingestionService.ingest(
                        agentId,
                        messageId,
                        "duplicate/" + messageId + ".json",
                        sha256,
                        json.getBytes(StandardCharsets.UTF_8).length,
                        contractValidator.validate(json),
                        UUID.randomUUID()))
                .isEqualTo(SnapshotIngestionService.IngestionOutcome.DUPLICATE);
        Files.writeString(accepted,
                snapshot(agentId, messageId, UUID.randomUUID(), "2026-10-17T21:00:00Z", "conflict-host"));
        Files.setLastModifiedTime(accepted, FileTime.from(Instant.now().minusSeconds(5)));
        UUID rejectedPathId = UUID.randomUUID();
        writeFinal(directory, rejectedPathId,
                snapshot(agentId, UUID.randomUUID(), UUID.randomUUID(), "2026-10-17T22:00:00Z", "rejected-host"));
        reconciler().reconcile();

        assertThat(sseCounter("jaxon.collector.sse.events.sent")).isEqualTo(sentBefore + 1);
        stream.body().close();
        otherStream.body().close();
    }

    @Test
    void protectsAndLimitsEventStreamsWithoutRenewingThePassiveSession() throws Exception {
        UUID assetId = seedAsset();
        String cookie = login();
        Timestamp activityBefore = lastActivity(cookie);
        Timestamp absoluteExpiryBefore = absoluteExpiry(cookie);

        HttpResponse<InputStream> first = getTechnicalStateEvents(cookie, assetId.toString());
        HttpResponse<InputStream> second = getTechnicalStateEvents(cookie, assetId.toString());
        HttpResponse<InputStream> third = getTechnicalStateEvents(cookie, assetId.toString());
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(third.statusCode()).isEqualTo(200);
        assertThat(meterRegistry.get("jaxon.collector.sse.connections.active").gauge().value()).isEqualTo(3);
        assertThat(lastActivity(cookie)).isEqualTo(activityBefore);
        assertThat(absoluteExpiry(cookie)).isEqualTo(absoluteExpiryBefore);

        double rejectionsBefore = sseCounter("jaxon.collector.sse.limit.rejections");
        HttpResponse<String> limited = getTechnicalStateEventsProblem(cookie, assetId.toString());
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(limited.body()).contains("\"code\":\"SSE_CONNECTION_LIMIT_REACHED\"");
        assertThat(sseCounter("jaxon.collector.sse.limit.rejections")).isEqualTo(rejectionsBefore + 1);
        assertThat(getTechnicalStateEventsProblem(null, assetId.toString()).statusCode()).isEqualTo(401);
        assertThat(getTechnicalStateEventsProblem(cookie, "not-a-uuid").statusCode()).isEqualTo(404);
        assertThat(getTechnicalStateEventsProblem(cookie, UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        eventBroadcaster.close();
        assertThat(meterRegistry.get("jaxon.collector.sse.connections.active").gauge().value()).isZero();
        HttpResponse<InputStream> replacement = getTechnicalStateEvents(cookie, assetId.toString());
        assertThat(replacement.statusCode()).isEqualTo(200);
        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'IT_OPERATOR'");
        try {
            assertThat(getTechnicalStateEventsProblem(cookie, assetId.toString()).statusCode()).isEqualTo(403);
        } finally {
            restoreOperatorRole();
        }

        first.body().close();
        second.body().close();
        third.body().close();
        replacement.body().close();
    }

    @Test
    void doesNotPublishAnEventFromARolledBackTransaction() throws Exception {
        UUID assetId = seedAsset();
        HttpResponse<InputStream> stream = getTechnicalStateEvents(login(), assetId.toString());
        double sentBefore = sseCounter("jaxon.collector.sse.events.sent");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    applicationEvents.publishEvent(new TechnicalStateUpdatedEvent(
                            UUID.randomUUID(),
                            TechnicalStateUpdatedEvent.TYPE,
                            Instant.now(clock),
                            assetId,
                            UUID.randomUUID()));
                    throw new IllegalStateException("force rollback");
                }))
                .isInstanceOf(IllegalStateException.class);
        Thread.sleep(100);

        assertThat(sseCounter("jaxon.collector.sse.events.sent")).isEqualTo(sentBefore);
        stream.body().close();
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

    private HttpResponse<InputStream> getTechnicalStateEvents(String cookie, String assetId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/assets/" + assetId + "/technical-state/events"))
                .header("Accept", "text/event-stream")
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpResponse<String> getTechnicalStateEventsProblem(String cookie, String assetId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/assets/" + assetId + "/technical-state/events"))
                .header("Accept", "text/event-stream")
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String readEvent(InputStream input) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                        StringBuilder event = new StringBuilder();
                        boolean eventStarted = false;
                        for (String line; (line = reader.readLine()) != null; ) {
                            if (line.startsWith("id:") || line.startsWith("event:") || line.startsWith("data:")) {
                                eventStarted = true;
                                event.append(line).append('\n');
                            } else if (eventStarted && line.isEmpty()) {
                                return event.toString();
                            }
                        }
                        throw new IllegalStateException("SSE stream ended before an event was received");
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .get(5, TimeUnit.SECONDS);
    }

    private double sseCounter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private void awaitSseCounter(String name, double expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        double observed;
        do {
            observed = sseCounter(name);
            if (observed == expected) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
        } while (System.nanoTime() < deadline);
        assertThat(observed).isEqualTo(expected);
    }

    private double agentStatusGauge(String name, String status) {
        return meterRegistry.get(name).tag("status", status).gauge().value();
    }

    private Timestamp lastActivity(String cookie) throws Exception {
        return jdbc.queryForObject(
                "SELECT last_activity_at FROM identity.human_sessions WHERE token_hash = ?",
                Timestamp.class,
                tokenHash(cookie));
    }

    private Timestamp absoluteExpiry(String cookie) throws Exception {
        return jdbc.queryForObject(
                "SELECT absolute_expires_at FROM identity.human_sessions WHERE token_hash = ?",
                Timestamp.class,
                tokenHash(cookie));
    }

    private static String tokenHash(String cookie) throws Exception {
        String rawToken = cookie.substring(cookie.indexOf('=') + 1);
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
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
