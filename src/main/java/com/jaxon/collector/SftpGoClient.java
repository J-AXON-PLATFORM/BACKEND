package com.jaxon.collector;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class SftpGoClient implements SftpGoProvisioner {

    private static final Map<String, List<String>> PERMISSIONS = Map.of(
            "/", List.of("list"),
            "/tasks", List.of("list", "download"),
            "/snapshots", List.of("list", "upload", "overwrite", "rename_files"),
            "/responses", List.of("list", "upload", "overwrite", "rename_files"));
    private static final List<String> DENIED_PROTOCOLS = List.of("FTP", "DAV", "HTTP");
    private static final List<String> DENIED_LOGIN_METHODS = List.of(
            "password",
            "password-over-SSH",
            "keyboard-interactive",
            "publickey+password",
            "publickey+keyboard-interactive",
            "TLSCertificate",
            "TLSCertificate+password");

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String apiUrl;
    private final String adminUsername;
    private final String adminPassword;
    private final String publicHost;
    private final int publicPort;
    private final String publicHostKey;
    private final Duration requestTimeout;
    private final HttpClient http;

    SftpGoClient(
            ObjectMapper objectMapper,
            @Value("${jaxon.sftp.enabled:false}") boolean enabled,
            @Value("${jaxon.sftp.admin-api-url:}") String apiUrl,
            @Value("${jaxon.sftp.admin-username:}") String adminUsername,
            @Value("${jaxon.sftp.admin-password:}") String adminPassword,
            @Value("${jaxon.sftp.public-host:}") String publicHost,
            @Value("${jaxon.sftp.public-port:2022}") int publicPort,
            @Value("${jaxon.sftp.public-host-key:}") String publicHostKey,
            @Value("${jaxon.sftp.connect-timeout:5s}") Duration connectTimeout,
            @Value("${jaxon.sftp.request-timeout:10s}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiUrl = stripTrailingSlash(apiUrl);
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.publicHost = publicHost;
        this.publicPort = publicPort;
        this.publicHostKey = normalizeHostKey(publicHostKey);
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(adminUsername, adminPassword.toCharArray());
                    }
                })
                .build();
    }

    @Override
    public AgentActivationResponse ensureProvisioned(UUID agentId, String publicKey) {
        requireConfiguration();
        String username = "agent-" + agentId;
        try {
            String token = token();
            verifyHostKey(token);
            JsonNode current = getUser(token, username);
            if (current == null) {
                createUser(token, username, publicKey);
                current = getUser(token, username);
            }
            if (current == null || !matches(current, username, publicKey)) {
                throw new SftpProvisioningConflictException();
            }
            return new AgentActivationResponse(publicHost, publicPort, username, publicHostKey);
        } catch (SftpProvisioningConflictException | SftpHostKeyMismatchException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SftpProvisioningUnavailableException();
        } catch (Exception exception) {
            throw new SftpProvisioningUnavailableException();
        }
    }

    private String token() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/token"))
                .timeout(requestTimeout)
                .header("Authorization", basicAuthorization())
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new SftpProvisioningUnavailableException();
        }
        String token = objectMapper.readTree(response.body()).path("access_token").asText();
        if (token.isBlank()) {
            throw new SftpProvisioningUnavailableException();
        }
        return token;
    }

    private void verifyHostKey(String token) throws Exception {
        HttpResponse<String> response = send(authorized(token, apiUrl + "/status").GET().build());
        if (response.statusCode() != 200) {
            throw new SftpProvisioningUnavailableException();
        }
        String expected = fingerprint(publicHostKey);
        JsonNode keys = objectMapper.readTree(response.body()).path("ssh").path("host_keys");
        for (JsonNode key : keys) {
            if (expected.equals(key.path("fingerprint").asText())) {
                return;
            }
        }
        throw new SftpHostKeyMismatchException();
    }

    private JsonNode getUser(String token, String username) throws Exception {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(authorized(token, apiUrl + "/users/" + encoded).GET().build());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new SftpProvisioningUnavailableException();
        }
        return objectMapper.readTree(response.body());
    }

    private void createUser(String token, String username, String publicKey) throws Exception {
        Map<String, Object> body = Map.of(
                "status", 1,
                "username", username,
                "public_keys", List.of(publicKey),
                "home_dir", "/srv/sftpgo/data/" + username,
                "permissions", PERMISSIONS,
                "filters", Map.of(
                        "denied_protocols", DENIED_PROTOCOLS,
                        "denied_login_methods", DENIED_LOGIN_METHODS),
                "filesystem", Map.of("provider", 0));
        HttpRequest request = authorized(token, apiUrl + "/users")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        int status = send(request).statusCode();
        if (status != 201 && status != 409) {
            throw new SftpProvisioningUnavailableException();
        }
    }

    private static boolean matches(JsonNode user, String username, String publicKey) {
        if (!username.equals(user.path("username").asText())
                || user.path("status").asInt() != 1
                || user.path("has_password").asBoolean()
                || !("/srv/sftpgo/data/" + username).equals(user.path("home_dir").asText())
                || user.path("filesystem").path("provider").asInt(-1) != 0
                || user.path("virtual_folders").size() != 0
                || user.path("groups").size() != 0
                || !hasDefaultAccountLimits(user)) {
            return false;
        }
        if (user.path("public_keys").size() != 1
                || !publicKey.equals(user.path("public_keys").get(0).asText())) {
            return false;
        }
        return objectMapperValue(user.path("permissions")).equals(PERMISSIONS)
                && sameValues(user.path("filters").path("denied_protocols"), DENIED_PROTOCOLS)
                && sameValues(user.path("filters").path("denied_login_methods"), DENIED_LOGIN_METHODS)
                && hasDefaultFilters(user.path("filters"));
    }

    private static Map<String, List<String>> objectMapperValue(JsonNode node) {
        java.util.HashMap<String, List<String>> result = new java.util.HashMap<>();
        node.properties().forEach(entry -> result.put(entry.getKey(), stringList(entry.getValue())));
        return result;
    }

    private static List<String> stringList(JsonNode node) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        node.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static boolean sameValues(JsonNode node, List<String> expected) {
        List<String> actual = stringList(node);
        return actual.size() == expected.size()
                && java.util.Set.copyOf(actual).equals(java.util.Set.copyOf(expected));
    }

    private static boolean hasDefaultAccountLimits(JsonNode user) {
        return zero(user, "expiration_date")
                && zero(user, "max_sessions")
                && zero(user, "quota_size")
                && zero(user, "quota_files")
                && zero(user, "upload_bandwidth")
                && zero(user, "download_bandwidth")
                && zero(user, "upload_data_transfer")
                && zero(user, "download_data_transfer")
                && zero(user, "total_data_transfer")
                && user.path("role").asText().isBlank();
    }

    private static boolean hasDefaultFilters(JsonNode filters) {
        return empty(filters, "allowed_ip")
                && empty(filters, "denied_ip")
                && empty(filters, "file_patterns")
                && empty(filters, "bandwidth_limits")
                && empty(filters, "access_time")
                && empty(filters, "two_factor_protocols")
                && empty(filters, "tls_certs")
                && empty(filters, "recovery_codes")
                && zero(filters, "max_upload_file_size")
                && zero(filters, "external_auth_cache_time")
                && zero(filters, "password_expiration")
                && !filters.path("allow_api_key_auth").asBoolean()
                && !filters.path("is_anonymous").asBoolean()
                && !filters.path("require_password_change").asBoolean()
                && !filters.path("disable_fs_checks").asBoolean()
                && filters.path("user_type").asText().isBlank()
                && filters.path("start_directory").asText().isBlank()
                && filters.path("tls_username").asText().isBlank();
    }

    private static boolean empty(JsonNode parent, String field) {
        return parent.path(field).size() == 0;
    }

    private static boolean zero(JsonNode parent, String field) {
        return parent.path(field).asLong() == 0;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder authorized(String token, String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + token);
    }

    private String basicAuthorization() {
        String value = adminUsername + ":" + adminPassword;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void requireConfiguration() {
        if (!enabled
                || apiUrl.isBlank()
                || adminUsername.isBlank()
                || adminPassword.isBlank()
                || publicHost.isBlank()
                || publicPort < 1
                || publicPort > 65535
                || publicHostKey.isBlank()) {
            throw new SftpProvisioningUnavailableException();
        }
    }

    private static String normalizeHostKey(String value) {
        String[] parts = value == null ? new String[0] : value.trim().split("\\s+");
        return parts.length == 2 ? parts[0] + " " + parts[1] : "";
    }

    private static String fingerprint(String key) throws Exception {
        String[] parts = key.split(" ");
        byte[] blob = Base64.getDecoder().decode(parts[1]);
        ByteBuffer buffer = ByteBuffer.wrap(blob);
        if (buffer.remaining() < 4) {
            throw new SftpHostKeyMismatchException();
        }
        int length = buffer.getInt();
        if (length != "ssh-ed25519".length() || buffer.remaining() < length + 4 + 32) {
            throw new SftpHostKeyMismatchException();
        }
        byte[] algorithm = new byte[length];
        buffer.get(algorithm);
        if (!"ssh-ed25519".equals(new String(algorithm, StandardCharsets.US_ASCII))) {
            throw new SftpHostKeyMismatchException();
        }
        return "SHA256:" + Base64.getEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(blob));
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

final class AgentPublicKeyRequiredException extends RuntimeException {}

final class AssetNotActiveException extends RuntimeException {}

final class AgentStateConflictException extends RuntimeException {}

final class AssetActiveAgentConflictException extends RuntimeException {}

final class SftpProvisioningConflictException extends RuntimeException {}

final class SftpProvisioningUnavailableException extends RuntimeException {}

final class SftpHostKeyMismatchException extends RuntimeException {}
