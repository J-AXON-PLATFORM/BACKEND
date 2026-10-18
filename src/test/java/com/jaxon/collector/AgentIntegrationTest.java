package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Agent.Operator",
            "jaxon.identity.bootstrap.password=Agent integration passphrase 42!"
        })
class AgentIntegrationTest {

    private static final String USERNAME = "Agent.Operator";
    private static final String PASSWORD = "Agent integration passphrase 42!";

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

    @MockitoBean
    SftpGoProvisioner sftpGoProvisioner;

    @Test
    void preRegistersPendingAgentAndTechnicalIdentityAtomically() throws Exception {
        Session session = login();
        UUID assetId = seedAsset();
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> created = postAgent(session, assetId.toString(), correlationId);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID agentId = UUID.fromString(extract(created.body(), "agentId"));
        assertThat(agentId.version()).isEqualTo(4);
        assertThat(created.headers().firstValue("location"))
                .contains("/api/v1/agents/" + agentId);
        assertThat(created.body())
                .contains("\"assetId\":\"" + assetId + "\"")
                .contains("\"status\":\"PENDING\"")
                .contains("\"operationalStatus\":\"UNKNOWN\"")
                .contains("\"lastSeenAt\":null")
                .contains("\"lastSnapshotAt\":null")
                .contains("\"publicKeyRegistered\":false")
                .contains("\"publicKeyFingerprint\":null")
                .contains("\"version\":0");
        assertThat(jdbc.queryForMap(
                        "SELECT asset_id, status, version FROM collector.agents WHERE id = ?", agentId))
                .containsEntry("asset_id", assetId)
                .containsEntry("status", "PENDING")
                .containsEntry("version", 0L);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT identity_type, public_key, public_key_fingerprint, version
                        FROM identity.technical_identities WHERE agent_id = ?
                        """,
                        agentId))
                .containsEntry("identity_type", "AGENT")
                .containsEntry("public_key", null)
                .containsEntry("public_key_fingerprint", null)
                .containsEntry("version", 0L);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, event_version, producer_module, actor_type,
                               resource_type, resource_id, result, correlation_id, payload
                        FROM collector.outbox_events WHERE resource_id = ?
                        """,
                        agentId))
                .containsEntry("event_type", "AGENT_PRE_REGISTERED")
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "COLLECTOR")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("resource_type", "AGENT")
                .containsEntry("resource_id", agentId)
                .containsEntry("result", "SUCCESS")
                .containsEntry("correlation_id", correlationId);

        HttpResponse<String> retrieved = getAgent(session.cookie(), agentId.toString());
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(retrieved.body()).isEqualTo(created.body());
    }

    @Test
    void rejectsMissingAssetsInvalidIdsAndUnauthorizedRequests() throws Exception {
        Session session = login();
        int agentsBefore = count("collector.agents");
        int identitiesBefore = count("identity.technical_identities");
        int eventsBefore = count("collector.outbox_events");

        HttpResponse<String> missing = postAgent(session, UUID.randomUUID().toString(), UUID.randomUUID());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("\"code\":\"ASSET_NOT_FOUND\"");
        assertThat(count("collector.agents")).isEqualTo(agentsBefore);
        assertThat(count("identity.technical_identities")).isEqualTo(identitiesBefore);
        assertThat(count("collector.outbox_events")).isEqualTo(eventsBefore);

        HttpResponse<String> invalid = postAgent(session, "not-a-uuid", UUID.randomUUID());
        assertThat(invalid.statusCode()).isEqualTo(422);
        assertThat(invalid.body()).contains("\"pointer\":\"/assetId\"");
        assertThat(getAgent(session.cookie(), "not-a-uuid").statusCode()).isEqualTo(404);
        assertThat(getAgent(session.cookie(), UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        assertThat(getAgent(null, UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
        assertThat(postAgent(null, seedAsset().toString(), UUID.randomUUID()).statusCode())
                .isEqualTo(401);

        HttpRequest missingCsrf = HttpRequest.newBuilder()
                .uri(uri("/api/v1/agents"))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"assetId\":\"" + seedAsset() + "\"}"))
                .build();
        HttpResponse<String> csrfRejected =
                httpClient.send(missingCsrf, HttpResponse.BodyHandlers.ofString());
        assertThat(csrfRejected.statusCode()).isEqualTo(403);
        assertThat(csrfRejected.body()).contains("\"code\":\"CSRF_INVALID\"");

        removeOperatorRole();
        try {
            assertThat(getAgent(session.cookie(), UUID.randomUUID().toString()).statusCode())
                    .isEqualTo(403);
            assertThat(postAgent(session, seedAsset().toString(), UUID.randomUUID()).statusCode())
                    .isEqualTo(403);
        } finally {
            restoreOperatorRole();
        }
    }

    @Test
    void registersEd25519KeyIdempotentlyWithoutActivatingTheAgent() throws Exception {
        Session session = login();
        UUID agentId = createAgent(session, seedAsset());
        String key = publicKey();
        String fingerprint = fingerprint(key);

        HttpResponse<String> registered = putKey(session, agentId, key + " operator comment");

        assertThat(registered.statusCode()).isEqualTo(200);
        assertThat(registered.body())
                .contains("\"status\":\"PENDING\"")
                .contains("\"publicKeyRegistered\":true")
                .contains("\"publicKeyFingerprint\":\"" + fingerprint + "\"")
                .doesNotContain(key)
                .doesNotContain("PRIVATE KEY");
        Map<String, Object> identity = jdbc.queryForMap(
                """
                SELECT public_key, public_key_fingerprint, version
                FROM identity.technical_identities WHERE agent_id = ?
                """,
                agentId);
        assertThat(identity)
                .containsEntry("public_key", key)
                .containsEntry("public_key_fingerprint", fingerprint)
                .containsEntry("version", 1L);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, event_version, producer_module, actor_type,
                               resource_type, resource_id, result, payload
                        FROM identity.outbox_events
                        WHERE event_type = 'AGENT_PUBLIC_KEY_REGISTERED' AND resource_id = ?
                        """,
                        agentId))
                .containsEntry("event_type", "AGENT_PUBLIC_KEY_REGISTERED")
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "IDENTITY")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("resource_type", "AGENT_TECHNICAL_IDENTITY")
                .containsEntry("resource_id", agentId)
                .containsEntry("result", "SUCCESS");

        HttpResponse<String> repeated = putKey(session, agentId, key);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(repeated.body()).isEqualTo(registered.body());
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM identity.outbox_events
                        WHERE event_type = 'AGENT_PUBLIC_KEY_REGISTERED' AND resource_id = ?
                        """,
                        Integer.class,
                        agentId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM collector.agents WHERE id = ?", String.class, agentId))
                .isEqualTo("PENDING");
    }

    @Test
    void rejectsMalformedDuplicateAndReplacementKeysSafely() throws Exception {
        Session session = login();
        UUID firstAgent = createAgent(session, seedAsset());
        UUID secondAgent = createAgent(session, seedAsset());
        String firstKey = publicKey();
        String secondKey = publicKey();
        assertThat(putKey(session, firstAgent, firstKey).statusCode()).isEqualTo(200);

        HttpResponse<String> malformed = putKey(session, secondAgent, "ssh-ed25519 not-base64");
        assertThat(malformed.statusCode()).isEqualTo(422);
        assertThat(malformed.body())
                .contains("\"code\":\"VALIDATION_FAILED\"")
                .doesNotContain("not-base64");

        HttpResponse<String> wrongAlgorithm = putKey(session, secondAgent, "ssh-rsa AAAA");
        assertThat(wrongAlgorithm.statusCode()).isEqualTo(422);

        HttpResponse<String> duplicate = putKey(session, secondAgent, firstKey);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("\"code\":\"AGENT_KEY_CONFLICT\"");

        HttpResponse<String> replacement = putKey(session, firstAgent, secondKey);
        assertThat(replacement.statusCode()).isEqualTo(409);
        assertThat(replacement.body())
                .contains("\"code\":\"AGENT_KEY_CONFLICT\"")
                .doesNotContain(secondKey);
        assertThat(putKey(session, UUID.randomUUID(), secondKey).statusCode()).isEqualTo(404);
        assertThat(putKey(session, "not-a-uuid", secondKey).statusCode()).isEqualTo(404);
    }

    @Test
    void activatesAgentIdempotentlyAndWritesOneOutboxEvent() throws Exception {
        Session session = login();
        UUID assetId = seedAsset();
        UUID agentId = createAgent(session, assetId);
        String key = publicKey();
        assertThat(putKey(session, agentId, key).statusCode()).isEqualTo(200);
        AgentActivationResponse connection = new AgentActivationResponse(
                "sftp.example.internal", 2022, "agent-" + agentId, publicKey());
        when(sftpGoProvisioner.ensureProvisioned(agentId, key)).thenReturn(connection);

        HttpResponse<String> activated = activate(session, agentId);

        assertThat(activated.statusCode()).isEqualTo(200);
        assertThat(activated.body())
                .contains("\"host\":\"sftp.example.internal\"")
                .contains("\"port\":2022")
                .contains("\"username\":\"agent-" + agentId + "\"")
                .contains("\"publicHostKey\":\"")
                .doesNotContain(key);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT status, sftp_username, activated_at, version
                        FROM collector.agents WHERE id = ?
                        """,
                        agentId))
                .containsEntry("status", "ACTIVE")
                .containsEntry("sftp_username", "agent-" + agentId)
                .containsEntry("version", 1L);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM collector.outbox_events
                        WHERE event_type = 'AGENT_ACTIVATED' AND resource_id = ?
                        """,
                        Integer.class,
                        agentId))
                .isEqualTo(1);
        assertThat(getAgent(session.cookie(), agentId.toString()).body())
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"operationalStatus\":\"UNKNOWN\"")
                .contains("\"lastSeenAt\":null")
                .contains("\"lastSnapshotAt\":null")
                .contains("\"version\":1");

        assertThat(activate(session, agentId).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM collector.outbox_events
                        WHERE event_type = 'AGENT_ACTIVATED' AND resource_id = ?
                        """,
                        Integer.class,
                        agentId))
                .isEqualTo(1);
        verify(sftpGoProvisioner, org.mockito.Mockito.times(2)).ensureProvisioned(agentId, key);
    }

    @Test
    void refusesActivationWithoutKeyOrWhenProvisioningIsUnavailable() throws Exception {
        Session session = login();
        UUID agentId = createAgent(session, seedAsset());
        assertThat(activate(session, agentId).statusCode()).isEqualTo(409);

        String key = publicKey();
        assertThat(putKey(session, agentId, key).statusCode()).isEqualTo(200);
        when(sftpGoProvisioner.ensureProvisioned(agentId, key))
                .thenThrow(new SftpProvisioningUnavailableException());
        HttpResponse<String> unavailable = activate(session, agentId);
        assertThat(unavailable.statusCode()).isEqualTo(503);
        assertThat(unavailable.body()).contains("\"code\":\"SFTP_PROVISIONING_UNAVAILABLE\"");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM collector.agents WHERE id = ?", String.class, agentId))
                .isEqualTo("PENDING");
    }

    @Test
    void keepsModuleOwnershipWithoutCrossSchemaForeignKeysOrSecretColumns() {
        List<String> crossSchemaForeignKeys = jdbc.queryForList(
                """
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class source_table ON source_table.oid = con.conrelid
                JOIN pg_namespace source_schema ON source_schema.oid = source_table.relnamespace
                JOIN pg_class target_table ON target_table.oid = con.confrelid
                JOIN pg_namespace target_schema ON target_schema.oid = target_table.relnamespace
                WHERE con.contype = 'f'
                  AND source_schema.nspname <> target_schema.nspname
                """,
                String.class);
        List<String> forbiddenColumns = jdbc.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema IN ('collector', 'identity')
                  AND table_name IN ('agents', 'technical_identities')
                  AND column_name IN ('private_key', 'passphrase', 'enrollment_token', 'shared_secret')
                """,
                String.class);

        assertThat(crossSchemaForeignKeys).isEmpty();
        assertThat(forbiddenColumns).isEmpty();
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO collector.agents
                            (id, asset_id, status, created_at, updated_at, version)
                        VALUES (?, ?, 'ACTIVE', now(), now(), 0)
                        """,
                        UUID.randomUUID(),
                        UUID.randomUUID()))
                .hasMessageContaining("collector_agents_activation_state_check");
    }

    private UUID createAgent(Session session, UUID assetId) throws Exception {
        HttpResponse<String> response = postAgent(session, assetId.toString(), UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(extract(response.body(), "agentId"));
    }

    private UUID seedAsset() {
        UUID assetId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO inventory.assets
                    (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, ?, 'DESKTOP', 'ACTIVE', ?, ?, 0)
                """,
                assetId,
                "Agent test asset " + assetId,
                OffsetDateTime.now(),
                OffsetDateTime.now());
        return assetId;
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

    private HttpResponse<String> postAgent(Session session, String assetId, UUID correlationId)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/agents"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofString("{\"assetId\":\"" + assetId + "\"}"));
        if (session != null) {
            request.header("Cookie", session.cookie()).header("X-CSRF-Token", session.csrfToken());
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getAgent(String cookie, String agentId) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder().uri(uri("/api/v1/agents/" + agentId)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> putKey(Session session, UUID agentId, String publicKey)
            throws Exception {
        return putKey(session, agentId.toString(), publicKey);
    }

    private HttpResponse<String> putKey(Session session, String agentId, String publicKey)
            throws Exception {
        String body = "{\"publicKey\":\"" + publicKey + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/agents/" + agentId + "/ssh-key"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> activate(Session session, UUID agentId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/agents/" + agentId + "/activate"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
                                 WHERE normalized_username = 'agent.operator')
                """);
    }

    private void restoreOperatorRole() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'IT_OPERATOR' FROM identity.human_users
                WHERE normalized_username = 'agent.operator'
                ON CONFLICT DO NOTHING
                """);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String publicKey() {
        byte[] key = new Ed25519PrivateKeyParameters(new SecureRandom()).generatePublicKey().getEncoded();
        byte[] algorithm = "ssh-ed25519".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer blob = ByteBuffer.allocate(4 + algorithm.length + 4 + key.length);
        blob.putInt(algorithm.length).put(algorithm).putInt(key.length).put(key);
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(blob.array());
    }

    private static String fingerprint(String publicKey) throws Exception {
        byte[] blob = Base64.getDecoder().decode(publicKey.split(" ")[1]);
        return "SHA256:" + Base64.getEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(blob));
    }

    private static String extract(String body, String property) {
        var matcher = Pattern.compile("\\\"" + property + "\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private record Session(String cookie, String csrfToken) {}
}
