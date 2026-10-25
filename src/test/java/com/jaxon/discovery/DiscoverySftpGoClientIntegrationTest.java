package com.jaxon.discovery;

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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Tag("sftpgo")
class DiscoverySftpGoClientIntegrationTest {

    @Test
    void provisionsExactIsolatedPasswordlessProfilesAgainstRealSftpGo() throws Exception {
        Ed25519PrivateKeyParameters hostPrivate = new Ed25519PrivateKeyParameters(new SecureRandom());
        Path hostKey = Files.createTempFile("jaxon-discovery-sftpgo-host-", ".key");
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
            ObjectMapper objectMapper = new ObjectMapper();
            String hostPublicKey = publicKey(hostPrivate);
            String apiUrl = "http://" + sftpGo.getHost() + ":" + sftpGo.getMappedPort(8080) + "/api/v2";
            DiscoverySftpGoClient client = client(objectMapper, apiUrl, hostPublicKey);
            UUID firstId = UUID.randomUUID();
            Ed25519PrivateKeyParameters firstPrivate =
                    new Ed25519PrivateKeyParameters(new SecureRandom());

            DiscoveryExecutorActivationResponse first =
                    client.ensureProvisioned(firstId, publicKey(firstPrivate));
            DiscoveryExecutorActivationResponse repeated =
                    client.ensureProvisioned(firstId, publicKey(firstPrivate));

            assertThat(first).isEqualTo(repeated);
            assertThat(first.username()).isEqualTo("discovery-executor-" + firstId);
            assertThat(first.hostKey()).isEqualTo(hostPublicKey);
            assertThat(first.hostKeyFingerprint()).isEqualTo(fingerprint(hostPublicKey));
            assertTargetProfile(apiUrl, objectMapper, first.username());
            verifyAuthenticationAndIsolation(
                    client, sftpGo, apiUrl, objectMapper, hostPublicKey, first, firstPrivate);

            expireUser(apiUrl, objectMapper, first.username());
            assertThatThrownBy(() -> client.ensureProvisioned(firstId, publicKey(firstPrivate)))
                    .isInstanceOf(DiscoverySftpProvisioningConflictException.class);

            DiscoverySftpGoClient wrongHostKey = client(
                    objectMapper,
                    apiUrl,
                    publicKey(new Ed25519PrivateKeyParameters(new SecureRandom())));
            assertThatThrownBy(() -> wrongHostKey.ensureProvisioned(UUID.randomUUID(), publicKey(firstPrivate)))
                    .isInstanceOf(DiscoverySftpHostKeyMismatchException.class);
        } finally {
            Files.deleteIfExists(hostKey);
        }
    }

    private static void verifyAuthenticationAndIsolation(
            DiscoverySftpGoClient client,
            GenericContainer<?> sftpGo,
            String apiUrl,
            ObjectMapper objectMapper,
            String hostPublicKey,
            DiscoveryExecutorActivationResponse first,
            Ed25519PrivateKeyParameters firstPrivate)
            throws Exception {
        UUID secondId = UUID.randomUUID();
        Ed25519PrivateKeyParameters secondPrivate =
                new Ed25519PrivateKeyParameters(new SecureRandom());
        DiscoveryExecutorActivationResponse second =
                client.ensureProvisioned(secondId, publicKey(secondPrivate));
        assertTargetProfile(apiUrl, objectMapper, second.username());
        assertThat(second.username()).isNotEqualTo(first.username());
        assertThat(getUser(apiUrl, objectMapper, second.username()).path("home_dir").asText())
                .isNotEqualTo(getUser(apiUrl, objectMapper, first.username()).path("home_dir").asText());

        Path knownHosts = Files.createTempFile("jaxon-discovery-known-hosts-", ".txt");
        Path firstKey = Files.createTempFile("jaxon-discovery-first-", ".key");
        Path secondKey = Files.createTempFile("jaxon-discovery-second-", ".key");
        try {
            String host = sftpGo.getHost();
            int port = sftpGo.getMappedPort(2022);
            Files.writeString(knownHosts, "[" + host + "]:" + port + " " + hostPublicKey + "\n");
            writePrivateKey(firstKey, firstPrivate);
            writePrivateKey(secondKey, secondPrivate);
            assertThat(sftp(first, host, port, firstKey, knownHosts).exitCode()).isZero();
            assertThat(sftp(second, host, port, secondKey, knownHosts).exitCode()).isZero();
        } finally {
            Files.deleteIfExists(knownHosts);
            Files.deleteIfExists(firstKey);
            Files.deleteIfExists(secondKey);
        }
    }

    private static DiscoverySftpGoClient client(
            ObjectMapper objectMapper, String apiUrl, String hostPublicKey) {
        return new DiscoverySftpGoClient(
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
    }

    private static void assertTargetProfile(String apiUrl, ObjectMapper objectMapper, String username)
            throws Exception {
        ObjectNode user = getUser(apiUrl, objectMapper, username);
        assertThat(user.path("username").asText()).isEqualTo(username);
        assertThat(user.path("has_password").asBoolean()).isFalse();
        assertThat(user.path("home_dir").asText()).isEqualTo("/srv/sftpgo/data/" + username);
        assertThat(user.path("permissions").size()).isEqualTo(1);
        assertThat(user.path("permissions").path("/"))
                .extracting(JsonNode::asText)
                .containsExactly("list");
        assertThat(user.path("virtual_folders")).isEmpty();
        assertThat(user.path("groups")).isEmpty();
        assertThat(user.path("quota_size").asLong()).isZero();
        assertThat(user.path("quota_files").asLong()).isZero();
        assertThat(user.path("filters").path("max_upload_file_size").asLong()).isZero();
    }

    private static SftpResult sftp(
            DiscoveryExecutorActivationResponse connection,
            String host,
            int port,
            Path privateKey,
            Path knownHosts)
            throws Exception {
        Path batch = Files.createTempFile("jaxon-discovery-sftp-batch-", ".txt");
        try {
            Files.writeString(batch, "ls /\n");
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

    private static void expireUser(String apiUrl, ObjectMapper objectMapper, String username)
            throws Exception {
        ObjectNode user = getUser(apiUrl, objectMapper, username);
        user.put("expiration_date", System.currentTimeMillis() + Duration.ofDays(1).toMillis());
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                authorizedRequest(apiUrl, objectMapper, "/users/" + username)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(user)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
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

    private static String privateKey(Ed25519PrivateKeyParameters key) throws Exception {
        StringWriter value = new StringWriter();
        try (PemWriter writer = new PemWriter(value)) {
            writer.writeObject(
                    new PemObject("OPENSSH PRIVATE KEY", OpenSSHPrivateKeyUtil.encodePrivateKey(key)));
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

    private static String fingerprint(String publicKey) throws Exception {
        byte[] blob = Base64.getDecoder().decode(publicKey.split(" ")[1]);
        return "SHA256:" + Base64.getEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(blob));
    }

    private record SftpResult(int exitCode, String output) {}
}
