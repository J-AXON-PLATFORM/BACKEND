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
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class SftpGoClient implements SftpGoProvisioner {

    private static final long MAX_UPLOAD_FILE_SIZE = 5L * 1024 * 1024;
    private static final Map<String, List<String>> LEGACY_PERMISSIONS = Map.of(
            "/", List.of("list"),
            "/tasks", List.of("list", "download"),
            "/snapshots", List.of("list", "upload", "overwrite", "rename_files"),
            "/responses", List.of("list", "upload", "overwrite", "rename_files"));
    private static final Map<String, List<String>> PERMISSIONS = Map.of(
            "/", List.of("list"),
            "/tasks", List.of("list", "download"),
            "/snapshots", List.of("list", "upload", "rename_files", "create_dirs", "delete_files"),
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
                ensureSnapshotFolder(token, username, false);
                createUser(token, username, publicKey);
            } else if (matchesLegacy(current, username, publicKey)) {
                ensureSnapshotFolder(token, username, false);
                current = getUser(token, username);
                if (current == null || !matchesLegacy(current, username, publicKey)) {
                    throw new SftpProvisioningConflictException();
                }
                updateUser(token, username, publicKey);
            } else if (!matches(current, username, publicKey)) {
                throw new SftpProvisioningConflictException();
            }
            current = getUser(token, username);
            if (current == null || !matches(current, username, publicKey)) {
                throw new SftpProvisioningConflictException();
            }
            ensureSnapshotFolder(token, username, true);
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
        HttpRequest request = authorized(token, apiUrl + "/users")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(userBody(username, publicKey))))
                .build();
        int status = send(request).statusCode();
        if (status != 201 && status != 409) {
            throw new SftpProvisioningUnavailableException();
        }
    }

    private void updateUser(String token, String username, String publicKey) throws Exception {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpRequest request = authorized(token, apiUrl + "/users/" + encoded + "?disconnect=1")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(userBody(username, publicKey))))
                .build();
        if (send(request).statusCode() != 200) {
            throw new SftpProvisioningUnavailableException();
        }
    }

    private Map<String, Object> userBody(String username, String publicKey) {
        return Map.of(
                "status", 1,
                "username", username,
                "public_keys", List.of(publicKey),
                "home_dir", homeDirectory(username),
                "permissions", PERMISSIONS,
                "filters", Map.of(
                        "denied_protocols", DENIED_PROTOCOLS,
                        "denied_login_methods", DENIED_LOGIN_METHODS,
                        "max_upload_file_size", MAX_UPLOAD_FILE_SIZE),
                "virtual_folders", List.of(Map.of(
                        "name", snapshotFolderName(username),
                        "mapped_path", snapshotMappedPath(username),
                        "virtual_path", "/snapshots",
                        "quota_size", 0,
                        "quota_files", 0,
                        "filesystem", Map.of("provider", 0))),
                "filesystem", Map.of("provider", 0));
    }

    private void ensureSnapshotFolder(String token, String username, boolean attached) throws Exception {
        String name = snapshotFolderName(username);
        JsonNode folder = getFolder(token, name);
        if (folder == null) {
            createFolder(token, name, snapshotMappedPath(username));
            folder = getFolder(token, name);
        }
        if (folder == null || !matchesFolder(folder, username, attached)) {
            throw new SftpProvisioningConflictException();
        }
    }

    private JsonNode getFolder(String token, String name) throws Exception {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        HttpResponse<String> response =
                send(authorized(token, apiUrl + "/folders/" + encoded).GET().build());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new SftpProvisioningUnavailableException();
        }
        return objectMapper.readTree(response.body());
    }

    private void createFolder(String token, String name, String mappedPath) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "mapped_path", mappedPath,
                "filesystem", Map.of("provider", 0));
        HttpRequest request = authorized(token, apiUrl + "/folders")
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
                || !hasDefaultIdentityMetadata(user)
                || !homeDirectory(username).equals(user.path("home_dir").asText())
                || !hasDefaultFilesystem(user.path("filesystem"))
                || !matchesVirtualFolder(user.path("virtual_folders"), username)
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
                && hasDefaultFilters(user.path("filters"), MAX_UPLOAD_FILE_SIZE);
    }

    private static boolean matchesLegacy(JsonNode user, String username, String publicKey) {
        if (!username.equals(user.path("username").asText())
                || user.path("status").asInt() != 1
                || user.path("has_password").asBoolean()
                || !hasDefaultIdentityMetadata(user)
                || !homeDirectory(username).equals(user.path("home_dir").asText())
                || !hasDefaultFilesystem(user.path("filesystem"))
                || user.path("virtual_folders").size() != 0
                || user.path("groups").size() != 0
                || !hasDefaultAccountLimits(user)
                || user.path("public_keys").size() != 1
                || !publicKey.equals(user.path("public_keys").get(0).asText())) {
            return false;
        }
        return objectMapperValue(user.path("permissions")).equals(LEGACY_PERMISSIONS)
                && sameValues(user.path("filters").path("denied_protocols"), DENIED_PROTOCOLS)
                && sameValues(user.path("filters").path("denied_login_methods"), DENIED_LOGIN_METHODS)
                && hasDefaultFilters(user.path("filters"), 0);
    }

    private static boolean matchesVirtualFolder(JsonNode folders, String username) {
        if (folders.size() != 1) {
            return false;
        }
        JsonNode folder = folders.get(0);
        return snapshotFolderName(username).equals(folder.path("name").asText())
                && snapshotMappedPath(username).equals(folder.path("mapped_path").asText())
                && "/snapshots".equals(folder.path("virtual_path").asText())
                && folder.path("quota_size").asLong() == 0
                && folder.path("quota_files").asLong() == 0
                && folder.path("description").asText().isBlank()
                && hasDefaultFilesystem(folder.path("filesystem"));
    }

    private static boolean matchesFolder(JsonNode folder, String username, boolean attached) {
        List<String> expectedUsers = attached ? List.of(username) : List.of();
        return snapshotFolderName(username).equals(folder.path("name").asText())
                && snapshotMappedPath(username).equals(folder.path("mapped_path").asText())
                && folder.path("description").asText().isBlank()
                && hasDefaultFilesystem(folder.path("filesystem"))
                && sameValues(folder.path("users"), expectedUsers)
                && folder.path("groups").size() == 0;
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

    private static boolean hasDefaultIdentityMetadata(JsonNode user) {
        return zero(user, "uid")
                && zero(user, "gid")
                && user.path("email").asText().isBlank()
                && user.path("description").asText().isBlank()
                && user.path("additional_info").asText().isBlank()
                && user.path("oidc_custom_fields").size() == 0;
    }

    private static boolean hasDefaultFilesystem(JsonNode filesystem) {
        return filesystem.path("provider").asInt(-1) == 0
                && filesystem.path("osconfig").size() == 0
                && hasOnlyEmptyObjects(filesystem.path("s3config"), Set.of("access_secret", "sse_customer_key"))
                && hasOnlyEmptyObjects(filesystem.path("gcsconfig"), Set.of("credentials"))
                && hasOnlyEmptyObjects(filesystem.path("azblobconfig"), Set.of("account_key", "sas_url"))
                && hasOnlyEmptyObjects(filesystem.path("cryptconfig"), Set.of("passphrase"))
                && hasOnlyEmptyObjects(
                        filesystem.path("sftpconfig"), Set.of("password", "private_key", "key_passphrase"))
                && hasOnlyEmptyObjects(filesystem.path("httpconfig"), Set.of("password", "api_key"));
    }

    private static boolean hasOnlyEmptyObjects(JsonNode node, Set<String> allowedFields) {
        var fields = node.properties().iterator();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!allowedFields.contains(field.getKey()) || field.getValue().size() != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDefaultFilters(JsonNode filters, long maxUploadFileSize) {
        return empty(filters, "allowed_ip")
                && empty(filters, "denied_ip")
                && empty(filters, "file_patterns")
                && empty(filters, "bandwidth_limits")
                && empty(filters, "access_time")
                && empty(filters, "two_factor_protocols")
                && empty(filters, "tls_certs")
                && empty(filters, "recovery_codes")
                && empty(filters, "additional_emails")
                && empty(filters, "web_client")
                && filters.path("max_upload_file_size").asLong() == maxUploadFileSize
                && zero(filters, "external_auth_cache_time")
                && zero(filters, "password_expiration")
                && zero(filters, "password_strength")
                && zero(filters, "default_shares_expiration")
                && zero(filters, "max_shares_expiration")
                && zero(filters, "ftp_security")
                && !filters.path("allow_api_key_auth").asBoolean()
                && !filters.path("is_anonymous").asBoolean()
                && !filters.path("require_password_change").asBoolean()
                && !filters.path("disable_fs_checks").asBoolean()
                && filters.path("user_type").asText().isBlank()
                && filters.path("start_directory").asText().isBlank()
                && filters.path("tls_username").asText().isBlank()
                && !filters.path("hooks").path("external_auth_disabled").asBoolean()
                && !filters.path("hooks").path("pre_login_disabled").asBoolean()
                && !filters.path("hooks").path("check_password_disabled").asBoolean()
                && !filters.path("totp_config").path("enabled").asBoolean()
                && filters.path("totp_config").path("config_name").asText().isBlank()
                && filters.path("totp_config").path("protocols").size() == 0
                && filters.path("totp_config").path("secret").size() == 0;
    }

    private static String homeDirectory(String username) {
        return "/srv/sftpgo/data/" + username;
    }

    private static String snapshotFolderName(String username) {
        return username + "-snapshots";
    }

    private static String snapshotMappedPath(String username) {
        return "/srv/sftpgo/data/" + snapshotFolderName(username);
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
