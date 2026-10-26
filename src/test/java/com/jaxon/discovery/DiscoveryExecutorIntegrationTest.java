package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
            "jaxon.identity.bootstrap.username=Executor.Admin",
            "jaxon.identity.bootstrap.password=Executor test passphrase 42!"
        })
class DiscoveryExecutorIntegrationTest {

    private static final String USERNAME = "Executor.Admin";
    private static final String PASSWORD = "Executor test passphrase 42!";
    private static final String PUBLIC_KEY =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKg2VaOybk2P/sGQKcJkL7QWqLpzaxLfOjfPCsqSy5aL";
    private static final String PUBLIC_KEY_FINGERPRINT =
            "SHA256:s5flX5trJMwyaxA/ME8Kyid8X3Q4XdOM487UdG7jOnA";

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

    @MockitoBean
    DiscoverySftpGoProvisioner sftpGoProvisioner;

    @BeforeEach
    void clearDiscoveryExecutorData() {
        jdbc.update("DELETE FROM discovery.outbox_events");
        jdbc.update("DELETE FROM discovery.discovery_executor_scopes");
        jdbc.update("DELETE FROM discovery.discovery_executors");
        jdbc.update("DELETE FROM discovery.authorized_scopes");
        jdbc.update("DELETE FROM identity.outbox_events");
        jdbc.update("DELETE FROM identity.technical_identities WHERE identity_type = 'DISCOVERY_EXECUTOR'");
    }

    @AfterEach
    void restoreAdministratorRole() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'SYSTEM_ADMIN' FROM identity.human_users
                WHERE normalized_username = 'executor.admin'
                ON CONFLICT DO NOTHING
                """);
    }

    @Test
    void preregistersListsAndRegistersThePublicKeyIdempotently() throws Exception {
        Session session = login();
        UUID scopeId = createScope(session, "Office", "192.0.2.0/24");
        UUID createCorrelation = UUID.randomUUID();
        HttpResponse<String> created = createExecutor(session, "  Executor   One ", scopeId, createCorrelation);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID executorId = UUID.fromString(extract(created.body(), "executorId"));
        assertThat(created.body())
                .contains("\"name\":\"Executor One\"")
                .contains("\"scopeIds\":[\"" + scopeId + "\"]")
                .contains("\"status\":\"PENDING\"")
                .contains("\"publicKeyRegistered\":false")
                .contains("\"publicKeyFingerprint\":null")
                .contains("\"version\":0");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, resource_type, resource_version, correlation_id,
                               payload::text AS payload
                        FROM discovery.outbox_events WHERE resource_id = ?
                        """,
                        executorId))
                .containsEntry("event_type", "DISCOVERY_EXECUTOR_PRE_REGISTERED")
                .containsEntry("resource_type", "DISCOVERY_EXECUTOR")
                .containsEntry("resource_version", 0L)
                .containsEntry("correlation_id", createCorrelation)
                .containsEntry("payload", "{\"status\": \"PENDING\"}");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT identity_type, agent_id, discovery_executor_id, version
                        FROM identity.technical_identities WHERE discovery_executor_id = ?
                        """,
                        executorId))
                .containsEntry("identity_type", "DISCOVERY_EXECUTOR")
                .containsEntry("agent_id", null)
                .containsEntry("discovery_executor_id", executorId)
                .containsEntry("version", 0L);
        assertThat(send(
                                "GET",
                                "/api/v1/discovery/executors/" + executorId,
                                session,
                                null,
                                null)
                        .body())
                .isEqualTo(created.body());
        assertThat(send(
                                "GET",
                                "/api/v1/discovery/executors?page=0&size=20",
                                session,
                                null,
                                null)
                        .body())
                .contains("\"totalElements\":1", "\"executorId\":\"" + executorId + "\"");

        UUID keyCorrelation = UUID.randomUUID();
        HttpResponse<String> registered = registerKey(session, executorId, PUBLIC_KEY, keyCorrelation);
        assertThat(registered.statusCode()).isEqualTo(200);
        assertThat(registered.body())
                .contains("\"status\":\"PENDING\"")
                .contains("\"publicKeyRegistered\":true")
                .contains("\"publicKeyFingerprint\":\"" + PUBLIC_KEY_FINGERPRINT + "\"")
                .contains("\"version\":0");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, resource_type, correlation_id, payload::text AS payload
                        FROM identity.outbox_events WHERE resource_id = ?
                        """,
                        executorId))
                .containsEntry("event_type", "DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED")
                .containsEntry("resource_type", "DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY")
                .containsEntry("correlation_id", keyCorrelation)
                .containsEntry("payload", "{}");

        assertThat(registerKey(session, executorId, PUBLIC_KEY + " comment", UUID.randomUUID()).statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM identity.outbox_events
                        WHERE event_type = 'DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED'
                        """,
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsDuplicateNamesInvalidAssociationsAndKeyReuse() throws Exception {
        Session session = login();
        UUID scopeId = createScope(session, "Network A", "198.51.100.0/24");
        UUID first = UUID.fromString(extract(
                createExecutor(session, "Executor A", scopeId, UUID.randomUUID()).body(), "executorId"));
        assertThat(createExecutor(session, "executor a", scopeId, UUID.randomUUID()).statusCode())
                .isEqualTo(409);
        assertThat(createExecutor(session, "Missing", UUID.randomUUID(), UUID.randomUUID()).statusCode())
                .isEqualTo(422);

        send(
                "PUT",
                "/api/v1/discovery/scopes/" + scopeId + "/status",
                session,
                "{\"status\":\"DISABLED\",\"expectedVersion\":0}",
                UUID.randomUUID());
        assertThat(createExecutor(session, "Disabled", scopeId, UUID.randomUUID()).statusCode())
                .isEqualTo(422);

        UUID agentId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO identity.technical_identities
                    (id, agent_id, identity_type, public_key, public_key_fingerprint,
                     created_at, updated_at, version)
                VALUES (?, ?, 'AGENT', ?, ?,
                        now(), now(), 1)
                """,
                UUID.randomUUID(),
                agentId,
                PUBLIC_KEY,
                PUBLIC_KEY_FINGERPRINT);
        assertThat(registerKey(session, first, PUBLIC_KEY, UUID.randomUUID()).statusCode())
                .isEqualTo(409);
        jdbc.update("DELETE FROM identity.technical_identities WHERE agent_id = ?", agentId);
        assertThat(registerKey(session, first, PUBLIC_KEY, UUID.randomUUID()).statusCode())
                .isEqualTo(200);
        UUID secondScope = createScope(session, "Network B", "203.0.113.0/24");
        UUID second = UUID.fromString(extract(
                createExecutor(session, "Executor B", secondScope, UUID.randomUUID()).body(), "executorId"));
        assertThat(registerKey(session, second, PUBLIC_KEY, UUID.randomUUID()).statusCode())
                .isEqualTo(409);
        assertThat(registerKey(session, second, "ssh-rsa invalid", UUID.randomUUID()).statusCode())
                .isEqualTo(422);
    }

    @Test
    void preservesAssociationsAndAllowsOperatorReadOnlySelection() throws Exception {
        Session administrator = login();
        UUID scopeId = createScope(administrator, "Persistent", "10.10.0.0/16");
        UUID executorId = UUID.fromString(extract(
                createExecutor(administrator, "Persistent executor", scopeId, UUID.randomUUID()).body(),
                "executorId"));
        send(
                "PUT",
                "/api/v1/discovery/scopes/" + scopeId + "/status",
                administrator,
                "{\"status\":\"DISABLED\",\"expectedVersion\":0}",
                UUID.randomUUID());
        assertThat(send(
                                "GET",
                                "/api/v1/discovery/executors/" + executorId,
                                administrator,
                                null,
                                null)
                        .body())
                .contains("\"scopeIds\":[\"" + scopeId + "\"]");

        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'SYSTEM_ADMIN'");
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'IT_OPERATOR' FROM identity.human_users
                WHERE normalized_username = 'executor.admin'
                ON CONFLICT DO NOTHING
                """);
        Session operator = login();
        assertThat(send("GET", "/api/v1/discovery/executors", operator, null, null).statusCode())
                .isEqualTo(200);
        assertThat(createExecutor(operator, "Denied", scopeId, UUID.randomUUID()).statusCode())
                .isEqualTo(403);
        assertThat(send(
                                "GET",
                                "/api/v1/discovery/executors/" + executorId,
                                operator,
                                null,
                                null)
                        .statusCode())
                .isEqualTo(200);
        assertThat(registerKey(operator, executorId, PUBLIC_KEY, UUID.randomUUID()).statusCode())
                .isEqualTo(403);
        assertThat(activate(operator, executorId, UUID.randomUUID()).statusCode()).isEqualTo(403);
    }

    @Test
    void activatesIdempotentlyAfterProvisioningAndWritesOneEvent() throws Exception {
        Session session = login();
        UUID scopeId = createScope(session, "Activation", "10.20.0.0/16");
        UUID executorId = UUID.fromString(extract(
                createExecutor(session, "Active executor", scopeId, UUID.randomUUID()).body(),
                "executorId"));
        assertThat(registerKey(session, executorId, PUBLIC_KEY, UUID.randomUUID()).statusCode())
                .isEqualTo(200);
        DiscoveryExecutorActivationResponse connection = new DiscoveryExecutorActivationResponse(
                "sftp.example.internal",
                2022,
                "discovery-executor-" + executorId,
                PUBLIC_KEY,
                PUBLIC_KEY_FINGERPRINT);
        when(sftpGoProvisioner.ensureProvisioned(executorId, PUBLIC_KEY)).thenReturn(connection);
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> activated = activate(session, executorId, correlationId);

        assertThat(activated.statusCode()).isEqualTo(200);
        assertThat(activated.body())
                .contains("\"host\":\"sftp.example.internal\"")
                .contains("\"port\":2022")
                .contains("\"username\":\"discovery-executor-" + executorId + "\"")
                .contains("\"hostKey\":\"ssh-ed25519 ")
                .contains("\"hostKeyFingerprint\":\"" + PUBLIC_KEY_FINGERPRINT + "\"")
                .doesNotContain("PRIVATE KEY");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT status, sftp_username, activated_at, version
                        FROM discovery.discovery_executors WHERE id = ?
                        """,
                        executorId))
                .containsEntry("status", "ACTIVE")
                .containsEntry("sftp_username", "discovery-executor-" + executorId)
                .containsEntry("version", 1L);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, event_version, producer_module, resource_version,
                               correlation_id, payload::text AS payload
                        FROM discovery.outbox_events
                        WHERE event_type = 'DISCOVERY_EXECUTOR_ACTIVATED' AND resource_id = ?
                        """,
                        executorId))
                .containsEntry("event_type", "DISCOVERY_EXECUTOR_ACTIVATED")
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "DISCOVERY")
                .containsEntry("resource_version", 1L)
                .containsEntry("correlation_id", correlationId)
                .containsEntry("payload", "{\"status\": \"ACTIVE\"}");
        String updatedAt = jdbc.queryForObject(
                "SELECT updated_at::text FROM discovery.discovery_executors WHERE id = ?",
                String.class,
                executorId);

        HttpResponse<String> repeated = activate(session, executorId, UUID.randomUUID());

        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(repeated.body()).isEqualTo(activated.body());
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM discovery.outbox_events
                        WHERE event_type = 'DISCOVERY_EXECUTOR_ACTIVATED' AND resource_id = ?
                        """,
                        Integer.class,
                        executorId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT updated_at::text FROM discovery.discovery_executors WHERE id = ?",
                        String.class,
                        executorId))
                .isEqualTo(updatedAt);
        assertThat(send(
                                "GET",
                                "/api/v1/discovery/executors/" + executorId,
                                session,
                                null,
                                null)
                        .body())
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"version\":1");
    }

    @Test
    void rejectsActivationWithoutKeyOrAnActiveAssociatedScope() throws Exception {
        Session session = login();
        UUID scopeId = createScope(session, "Preconditions", "10.30.0.0/16");
        UUID executorId = UUID.fromString(extract(
                createExecutor(session, "Blocked executor", scopeId, UUID.randomUUID()).body(),
                "executorId"));

        assertThat(activate(session, executorId, UUID.randomUUID()).body())
                .contains("\"code\":\"DISCOVERY_EXECUTOR_PUBLIC_KEY_REQUIRED\"");
        assertThat(registerKey(session, executorId, PUBLIC_KEY, UUID.randomUUID()).statusCode())
                .isEqualTo(200);
        send(
                "PUT",
                "/api/v1/discovery/scopes/" + scopeId + "/status",
                session,
                "{\"status\":\"DISABLED\",\"expectedVersion\":0}",
                UUID.randomUUID());

        HttpResponse<String> inactive = activate(session, executorId, UUID.randomUUID());

        assertThat(inactive.statusCode()).isEqualTo(409);
        assertThat(inactive.body())
                .contains("\"code\":\"DISCOVERY_EXECUTOR_ACTIVE_SCOPE_REQUIRED\"");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM discovery.discovery_executors WHERE id = ?",
                        String.class,
                        executorId))
                .isEqualTo("PENDING");
        assertThat(activate(session, UUID.randomUUID(), UUID.randomUUID()).statusCode())
                .isEqualTo(404);
    }

    @Test
    void leavesTheExecutorPendingWhenSftpProvisioningIsUnavailable() throws Exception {
        Session session = login();
        UUID scopeId = createScope(session, "Unavailable", "10.40.0.0/16");
        UUID executorId = UUID.fromString(extract(
                createExecutor(session, "Unavailable executor", scopeId, UUID.randomUUID()).body(),
                "executorId"));
        assertThat(registerKey(session, executorId, PUBLIC_KEY, UUID.randomUUID()).statusCode())
                .isEqualTo(200);
        when(sftpGoProvisioner.ensureProvisioned(executorId, PUBLIC_KEY))
                .thenThrow(new DiscoverySftpProvisioningUnavailableException());

        HttpResponse<String> unavailable = activate(session, executorId, UUID.randomUUID());

        assertThat(unavailable.statusCode()).isEqualTo(503);
        assertThat(unavailable.body()).contains("\"code\":\"DISCOVERY_SFTP_PROVISIONING_UNAVAILABLE\"");
        assertThat(jdbc.queryForMap(
                        "SELECT status, sftp_username, activated_at, version FROM discovery.discovery_executors WHERE id = ?",
                        executorId))
                .containsEntry("status", "PENDING")
                .containsEntry("version", 0L)
                .containsEntry("sftp_username", null)
                .containsEntry("activated_at", null);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery.outbox_events WHERE resource_id = ? AND event_type = 'DISCOVERY_EXECUTOR_ACTIVATED'",
                        Integer.class,
                        executorId))
                .isZero();
    }

    @Test
    void rollsBackDiscoveryAndIdentityWhenTheOutboxFails() throws Exception {
        Session session = login();
        UUID scopeId = createScope(session, "Atomic", "172.16.0.0/16");
        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION discovery.reject_executor_outbox() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced outbox failure'; END; $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER reject_executor_outbox BEFORE INSERT ON discovery.outbox_events
                FOR EACH ROW WHEN (NEW.event_type = 'DISCOVERY_EXECUTOR_PRE_REGISTERED')
                EXECUTE FUNCTION discovery.reject_executor_outbox()
                """);
        try {
            assertThat(createExecutor(session, "Rollback", scopeId, UUID.randomUUID()).statusCode())
                    .isEqualTo(503);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM discovery.discovery_executors", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject(
                            """
                            SELECT count(*) FROM identity.technical_identities
                            WHERE identity_type = 'DISCOVERY_EXECUTOR'
                            """,
                            Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_executor_outbox ON discovery.outbox_events");
            jdbc.execute("DROP FUNCTION IF EXISTS discovery.reject_executor_outbox()");
        }
    }

    private UUID createScope(Session session, String name, String cidr) throws Exception {
        HttpResponse<String> response = send(
                "POST",
                "/api/v1/discovery/scopes",
                session,
                "{\"name\":\"" + name + "\",\"cidr\":\"" + cidr + "\"}",
                UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(extract(response.body(), "scopeId"));
    }

    private HttpResponse<String> createExecutor(
            Session session, String name, UUID scopeId, UUID correlationId) throws Exception {
        return send(
                "POST",
                "/api/v1/discovery/executors",
                session,
                "{\"name\":\"" + name + "\",\"scopeIds\":[\"" + scopeId + "\"]}",
                correlationId);
    }

    private HttpResponse<String> registerKey(
            Session session, UUID executorId, String publicKey, UUID correlationId) throws Exception {
        return send(
                "PUT",
                "/api/v1/discovery/executors/" + executorId + "/ssh-key",
                session,
                "{\"publicKey\":\"" + publicKey + "\"}",
                correlationId);
    }

    private HttpResponse<String> activate(
            Session session, UUID executorId, UUID correlationId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/discovery/executors/"
                                + executorId + "/activate"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
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
