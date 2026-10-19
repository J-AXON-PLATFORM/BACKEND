package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class SftpGoClientIntegrationTest {

    private static final long MAX_UPLOAD_FILE_SIZE = 5L * 1024 * 1024;

    @Test
    void provisionsAndReconcilesPasswordlessUserAgainstRealSftpGo() throws Exception {
        Ed25519PrivateKeyParameters hostPrivate = new Ed25519PrivateKeyParameters(new SecureRandom());
        Path hostKey = Files.createTempFile("jaxon-sftpgo-host-", ".key");
        writePrivateKey(hostKey, hostPrivate);
        try (GenericContainer<?> sftpGo = new GenericContainer<>(
                        DockerImageName.parse("jaxon-sftpgo:2.7.6-local"))
                .withCopyFileToContainer(
                        MountableFile.forHostPath(hostKey, 0644), "/var/lib/sftpgo/id_ed25519")
                .withEnv("SFTPGO_DATA_PROVIDER__CREATE_DEFAULT_ADMIN", "true")
                .withEnv("SFTPGO_DEFAULT_ADMIN_USERNAME", "integration-admin")
                .withEnv("SFTPGO_DEFAULT_ADMIN_PASSWORD", "integration-password-42")
                .withEnv("SFTPGO_SFTPD__PASSWORD_AUTHENTICATION", "false")
                .withEnv("SFTPGO_SFTPD__KEYBOARD_INTERACTIVE_AUTHENTICATION", "false")
                .withExposedPorts(2022, 8080)
                .waitingFor(Wait.forHttp("/healthz").forPort(8080))) {
            sftpGo.start();
            String hostPublicKey = publicKey(hostPrivate);
            ObjectMapper objectMapper = new ObjectMapper();
            String apiUrl = "http://" + sftpGo.getHost() + ":" + sftpGo.getMappedPort(8080) + "/api/v2";
            SftpGoClient client = new SftpGoClient(
                    objectMapper,
                    true,
                    apiUrl,
                    "integration-admin",
                    "integration-password-42",
                    "sftp.example.internal",
                    2022,
                    hostPublicKey,
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(10));
            UUID agentId = UUID.randomUUID();
            Ed25519PrivateKeyParameters agentPrivate = new Ed25519PrivateKeyParameters(new SecureRandom());
            String agentPublicKey = publicKey(agentPrivate);

            AgentActivationResponse first = client.ensureProvisioned(agentId, agentPublicKey);
            AgentActivationResponse repeated = client.ensureProvisioned(agentId, agentPublicKey);

            assertThat(first).isEqualTo(repeated);
            assertThat(first.username()).isEqualTo("agent-" + agentId);
            assertThat(first.publicHostKey()).isEqualTo(hostPublicKey);
            assertTargetProfile(apiUrl, objectMapper, first.username());
            verifyDataPlaneIsolationAndLimits(
                    client, sftpGo, apiUrl, objectMapper, hostPublicKey, first, agentPrivate);

            UUID legacyAgentId = UUID.randomUUID();
            Ed25519PrivateKeyParameters legacyPrivate = new Ed25519PrivateKeyParameters(new SecureRandom());
            String legacyUsername = "agent-" + legacyAgentId;
            createLegacyUser(apiUrl, objectMapper, legacyUsername, publicKey(legacyPrivate));
            Path legacyKnownHosts = Files.createTempFile("jaxon-sftpgo-legacy-known-hosts-", ".txt");
            Path legacyKey = Files.createTempFile("jaxon-agent-legacy-", ".key");
            Process legacySession = null;
            try {
                Files.writeString(
                        legacyKnownHosts,
                        "[" + sftpGo.getHost() + "]:" + sftpGo.getMappedPort(2022) + " " + hostPublicKey + "\n");
                writePrivateKey(legacyKey, legacyPrivate);
                legacySession = openSftpSession(
                        legacyUsername,
                        sftpGo.getHost(),
                        sftpGo.getMappedPort(2022),
                        legacyKey,
                        legacyKnownHosts);
                waitForConnection(apiUrl, objectMapper, legacyUsername);

                AgentActivationResponse upgraded =
                        client.ensureProvisioned(legacyAgentId, publicKey(legacyPrivate));

                assertThat(upgraded.username()).isEqualTo(legacyUsername);
                assertTargetProfile(apiUrl, objectMapper, legacyUsername);
                legacySession.getOutputStream().write("ls /\n".getBytes(StandardCharsets.UTF_8));
                legacySession.getOutputStream().flush();
                assertThat(legacySession.waitFor(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                if (legacySession != null) {
                    legacySession.destroyForcibly();
                }
                Files.deleteIfExists(legacyKnownHosts);
                Files.deleteIfExists(legacyKey);
            }

            expireUser(apiUrl, objectMapper, first.username());
            assertThatThrownBy(() -> client.ensureProvisioned(agentId, agentPublicKey))
                    .isInstanceOf(SftpProvisioningConflictException.class);

            SftpGoClient wrongHostKey = new SftpGoClient(
                    objectMapper,
                    true,
                    apiUrl,
                    "integration-admin",
                    "integration-password-42",
                    "sftp.example.internal",
                    2022,
                    publicKey(new Ed25519PrivateKeyParameters(new SecureRandom())),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(10));
            assertThatThrownBy(() -> wrongHostKey.ensureProvisioned(UUID.randomUUID(), agentPublicKey))
                    .isInstanceOf(SftpHostKeyMismatchException.class);
        } finally {
            Files.deleteIfExists(hostKey);
        }
    }

    private static void verifyDataPlaneIsolationAndLimits(
            SftpGoClient client,
            GenericContainer<?> sftpGo,
            String apiUrl,
            ObjectMapper objectMapper,
            String hostPublicKey,
            AgentActivationResponse first,
            Ed25519PrivateKeyParameters firstPrivate)
            throws Exception {
        UUID secondAgentId = UUID.randomUUID();
        Ed25519PrivateKeyParameters secondPrivate = new Ed25519PrivateKeyParameters(new SecureRandom());
        AgentActivationResponse second = client.ensureProvisioned(secondAgentId, publicKey(secondPrivate));
        assertTargetProfile(apiUrl, objectMapper, second.username());

        Path knownHosts = Files.createTempFile("jaxon-sftpgo-known-hosts-", ".txt");
        Path firstKey = Files.createTempFile("jaxon-agent-first-", ".key");
        Path secondKey = Files.createTempFile("jaxon-agent-second-", ".key");
        Path snapshot = Files.createTempFile("jaxon-snapshot-", ".json");
        Path oversized = Files.createTempFile("jaxon-snapshot-oversized-", ".json");
        String host = sftpGo.getHost();
        int port = sftpGo.getMappedPort(2022);
        String messageId = UUID.randomUUID().toString();
        String temporary = "/snapshots/" + messageId + ".json.part";
        String complete = "/snapshots/" + messageId + ".json";
        String resultMessageId = UUID.randomUUID().toString();
        String responseTemporary = "/responses/" + resultMessageId + ".json.part";
        String responseComplete = "/responses/" + resultMessageId + ".json";
        try {
            Files.writeString(knownHosts, "[" + host + "]:" + port + " " + hostPublicKey + "\n");
            writePrivateKey(firstKey, firstPrivate);
            writePrivateKey(secondKey, secondPrivate);
            Files.writeString(snapshot, "{\"contractVersion\":\"1.0\"}");
            Files.write(oversized, new byte[(int) MAX_UPLOAD_FILE_SIZE + 1]);

            assertThat(sftp(first, host, port, firstKey, knownHosts, "ls /", "ls /snapshots").exitCode())
                    .isZero();
            assertThat(sftp(first, host, port, firstKey, knownHosts, "mkdir /arbitrary").exitCode())
                    .isNotZero();
            assertThat(sftp(first, host, port, firstKey, knownHosts, "mkdir /snapshots/nested").exitCode())
                    .isZero();
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "put " + snapshot + " " + responseTemporary)
                            .exitCode())
                    .isZero();
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "put " + snapshot + " " + responseTemporary)
                            .exitCode())
                    .isNotZero();
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "rename " + responseTemporary + " " + responseComplete)
                            .exitCode())
                    .isZero();
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "put " + snapshot + " " + responseComplete)
                            .exitCode())
                    .isNotZero();
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "put " + oversized + " /snapshots/too-large.json.part")
                            .exitCode())
                    .isNotZero();

            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "put " + snapshot + " " + temporary)
                            .exitCode())
                    .isZero();
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "put " + snapshot + " " + temporary)
                            .exitCode())
                    .isNotZero();
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "rename " + temporary + " " + complete)
                            .exitCode())
                    .isZero();
            assertThat(sftp(first, host, port, firstKey, knownHosts, "ls " + complete).exitCode())
                    .isZero();
            assertThat(sftp(first, host, port, firstKey, knownHosts, "ls " + temporary).exitCode())
                    .isNotZero();

            SftpResult isolated = sftp(second, host, port, secondKey, knownHosts, "ls /snapshots");
            assertThat(isolated.exitCode()).isZero();
            assertThat(isolated.output()).doesNotContain(messageId);

            String cleanup = "/snapshots/" + UUID.randomUUID() + ".json.part";
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "put " + snapshot + " " + cleanup,
                                    "rm " + cleanup)
                            .exitCode())
                    .isZero();
            assertThat(sftp(first, host, port, firstKey, knownHosts, "ls " + cleanup).exitCode())
                    .isNotZero();
            String responseCleanup = "/responses/" + UUID.randomUUID() + ".json.part";
            assertThat(sftp(
                                    first,
                                    host,
                                    port,
                                    firstKey,
                                    knownHosts,
                                    "put " + snapshot + " " + responseCleanup,
                                    "rm " + responseCleanup)
                            .exitCode())
                    .isZero();
        } finally {
            Files.deleteIfExists(knownHosts);
            Files.deleteIfExists(firstKey);
            Files.deleteIfExists(secondKey);
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(oversized);
        }
    }

    private static SftpResult sftp(
            AgentActivationResponse connection,
            String host,
            int port,
            Path privateKey,
            Path knownHosts,
            String... commands)
            throws Exception {
        Path batch = Files.createTempFile("jaxon-sftp-batch-", ".txt");
        try {
            Files.writeString(batch, String.join("\n", commands) + "\n");
            Process process = new ProcessBuilder(
                            "sftp",
                            "-b",
                            batch.toString(),
                            "-oBatchMode=yes",
                            "-oIdentitiesOnly=yes",
                            "-oStrictHostKeyChecking=yes",
                            "-oUserKnownHostsFile=" + knownHosts,
                            "-oGlobalKnownHostsFile=/dev/null",
                            "-oConnectTimeout=10",
                            "-oConnectionAttempts=1",
                            "-i",
                            privateKey.toString(),
                            "-P",
                            Integer.toString(port),
                            connection.username() + "@" + host)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(20, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new AssertionError("SFTP command timed out");
            }
            return new SftpResult(
                    process.exitValue(),
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(batch);
        }
    }

    private static Process openSftpSession(
            String username, String host, int port, Path privateKey, Path knownHosts) throws Exception {
        return new ProcessBuilder(
                        "sftp",
                        "-oBatchMode=yes",
                        "-oIdentitiesOnly=yes",
                        "-oStrictHostKeyChecking=yes",
                        "-oUserKnownHostsFile=" + knownHosts,
                        "-oGlobalKnownHostsFile=/dev/null",
                        "-oConnectTimeout=10",
                        "-oConnectionAttempts=1",
                        "-i",
                        privateKey.toString(),
                        "-P",
                        Integer.toString(port),
                        username + "@" + host)
                .redirectErrorStream(true)
                .start();
    }

    private static void waitForConnection(String apiUrl, ObjectMapper objectMapper, String username)
            throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            HttpRequest request = authorizedRequest(apiUrl, objectMapper, "/connections").GET().build();
            JsonNode connections = objectMapper.readTree(
                    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body());
            for (JsonNode connection : connections) {
                if (username.equals(connection.path("username").asText())) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("SFTP session did not become active");
    }

    private static void assertTargetProfile(String apiUrl, ObjectMapper objectMapper, String username)
            throws Exception {
        ObjectNode user = getUser(apiUrl, objectMapper, username);
        assertThat(user.path("filters").path("max_upload_file_size").asLong())
                .isEqualTo(MAX_UPLOAD_FILE_SIZE);
        assertThat(user.path("permissions").path("/"))
                .extracting(JsonNode::asText)
                .containsExactly("list");
        assertThat(user.path("permissions").path("/tasks"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("list", "download");
        assertThat(user.path("permissions").path("/snapshots"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "list", "upload", "rename_files", "create_dirs", "delete_files");
        assertThat(user.path("permissions").path("/responses"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "list", "download", "upload", "rename_files", "delete_files");
        JsonNode folders = user.path("virtual_folders");
        assertThat(folders).hasSize(3);
        assertThat(folders)
                .extracting(folder -> folder.path("virtual_path").asText())
                .containsExactlyInAnyOrder("/tasks", "/snapshots", "/responses");
        assertThat(folders)
                .filteredOn(folder -> "/tasks".equals(folder.path("virtual_path").asText()))
                .singleElement()
                .satisfies(folder -> assertThat(folder.path("mapped_path").asText())
                        .isEqualTo("/srv/sftpgo/tasks/" + username + "-tasks"));
    }

    private static void createLegacyUser(
            String apiUrl, ObjectMapper objectMapper, String username, String publicKey) throws Exception {
        Map<String, Object> body = Map.of(
                "status", 1,
                "username", username,
                "public_keys", List.of(publicKey),
                "home_dir", "/srv/sftpgo/data/" + username,
                "permissions", Map.of(
                        "/", List.of("list"),
                        "/tasks", List.of("list", "download"),
                        "/snapshots", List.of("list", "upload", "overwrite", "rename_files"),
                        "/responses", List.of("list", "upload", "overwrite", "rename_files")),
                "filters", Map.of(
                        "denied_protocols", List.of("FTP", "DAV", "HTTP"),
                        "denied_login_methods", List.of(
                                "password",
                                "password-over-SSH",
                                "keyboard-interactive",
                                "publickey+password",
                                "publickey+keyboard-interactive",
                                "TLSCertificate",
                                "TLSCertificate+password")),
                "filesystem", Map.of("provider", 0));
        HttpRequest request = authorizedRequest(apiUrl, objectMapper, "/users")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
    }

    private static ObjectNode getUser(String apiUrl, ObjectMapper objectMapper, String username)
            throws Exception {
        HttpRequest request = authorizedRequest(apiUrl, objectMapper, "/users/" + username)
                .GET()
                .build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return (ObjectNode) objectMapper.readTree(response.body());
    }

    private static HttpRequest.Builder authorizedRequest(
            String apiUrl, ObjectMapper objectMapper, String path) throws Exception {
        return HttpRequest.newBuilder(URI.create(apiUrl + path))
                .header("Authorization", "Bearer " + token(apiUrl, objectMapper));
    }

    private static String token(String apiUrl, ObjectMapper objectMapper) throws Exception {
        String basic = Base64.getEncoder().encodeToString(
                "integration-admin:integration-password-42".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(apiUrl + "/token"))
                                .header("Authorization", "Basic " + basic)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).path("access_token").asText();
    }

    private static void writePrivateKey(Path path, Ed25519PrivateKeyParameters key) throws Exception {
        Files.writeString(path, privateKey(key));
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

    private static void expireUser(String apiUrl, ObjectMapper objectMapper, String username)
            throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String basic = Base64.getEncoder().encodeToString(
                "integration-admin:integration-password-42".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> tokenResponse = http.send(
                HttpRequest.newBuilder(URI.create(apiUrl + "/token"))
                        .header("Authorization", "Basic " + basic)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String token = objectMapper.readTree(tokenResponse.body()).path("access_token").asText();
        HttpResponse<String> userResponse = http.send(
                HttpRequest.newBuilder(URI.create(apiUrl + "/users/" + username))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        ObjectNode user = (ObjectNode) objectMapper.readTree(userResponse.body());
        user.put("expiration_date", System.currentTimeMillis() + Duration.ofDays(1).toMillis());
        HttpResponse<String> updated = http.send(
                HttpRequest.newBuilder(URI.create(apiUrl + "/users/" + username))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(user)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(updated.statusCode()).isEqualTo(200);
    }

    private static String privateKey(Ed25519PrivateKeyParameters key) throws Exception {
        StringWriter value = new StringWriter();
        try (PemWriter writer = new PemWriter(value)) {
            writer.writeObject(new PemObject("OPENSSH PRIVATE KEY", OpenSSHPrivateKeyUtil.encodePrivateKey(key)));
        }
        return value.toString();
    }

    private static String publicKey(Ed25519PrivateKeyParameters privateKey) {
        byte[] algorithm = "ssh-ed25519".getBytes(StandardCharsets.US_ASCII);
        byte[] key = privateKey.generatePublicKey().getEncoded();
        ByteBuffer blob = ByteBuffer.allocate(4 + algorithm.length + 4 + key.length);
        blob.putInt(algorithm.length).put(algorithm).putInt(key.length).put(key);
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(blob.array());
    }

    private record SftpResult(int exitCode, String output) {}
}
