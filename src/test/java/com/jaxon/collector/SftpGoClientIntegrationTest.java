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
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class SftpGoClientIntegrationTest {

    @Test
    void provisionsAndReconcilesPasswordlessUserAgainstRealSftpGo() throws Exception {
        Ed25519PrivateKeyParameters hostPrivate = new Ed25519PrivateKeyParameters(new SecureRandom());
        Path hostKey = Files.createTempFile("jaxon-sftpgo-host-", ".key");
        Files.writeString(hostKey, privateKey(hostPrivate));
        try (GenericContainer<?> sftpGo = new GenericContainer<>(
                        DockerImageName.parse("jaxon-sftpgo:2.7.6-local"))
                .withCopyFileToContainer(
                        MountableFile.forHostPath(hostKey, 0644), "/var/lib/sftpgo/id_ed25519")
                .withEnv("SFTPGO_DATA_PROVIDER__CREATE_DEFAULT_ADMIN", "true")
                .withEnv("SFTPGO_DEFAULT_ADMIN_USERNAME", "integration-admin")
                .withEnv("SFTPGO_DEFAULT_ADMIN_PASSWORD", "integration-password-42")
                .withEnv("SFTPGO_SFTPD__PASSWORD_AUTHENTICATION", "false")
                .withEnv("SFTPGO_SFTPD__KEYBOARD_INTERACTIVE_AUTHENTICATION", "false")
                .withExposedPorts(8080)
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
            String agentPublicKey = publicKey(new Ed25519PrivateKeyParameters(new SecureRandom()));

            AgentActivationResponse first = client.ensureProvisioned(agentId, agentPublicKey);
            AgentActivationResponse repeated = client.ensureProvisioned(agentId, agentPublicKey);

            assertThat(first).isEqualTo(repeated);
            assertThat(first.username()).isEqualTo("agent-" + agentId);
            assertThat(first.publicHostKey()).isEqualTo(hostPublicKey);

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
}
