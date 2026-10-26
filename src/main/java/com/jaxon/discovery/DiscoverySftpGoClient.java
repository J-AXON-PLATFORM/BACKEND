package com.jaxon.discovery;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class DiscoverySftpGoClient implements DiscoverySftpGoProvisioner {

    private static final Map<String, List<String>> ORIGINAL_PERMISSIONS =
            Map.of("/", List.of("list"));
    private static final Map<String, List<String>> JOB_PERMISSIONS = Map.of(
            "/", List.of("list"),
            "/jobs", List.of("list", "download"));
    private static final Map<String, List<String>> PERMISSIONS = Map.of(
            "/", List.of("list"),
            "/jobs", List.of("list", "download"),
            "/results", List.of("list", "download", "upload", "rename_files"));
    private static final long MAX_RESULT_BYTES = 5L * 1024 * 1024;
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

    DiscoverySftpGoClient(
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
    public DiscoveryExecutorActivationResponse ensureProvisioned(UUID executorId, String publicKey) {
        requireConfiguration();
        String username = "discovery-executor-" + executorId;
        try {
            String hostKeyFingerprint = fingerprint(publicHostKey);
            String token = token();
            verifyHostKey(token, hostKeyFingerprint);
            JsonNode current = getUser(token, username);
            if (current == null) {
                ensureFolder(token, username, FolderType.JOBS, false);
                ensureFolder(token, username, FolderType.RESULTS, false);
                createUser(token, username, publicKey);
            } else if (matchesOriginal(current, username, publicKey)) {
                ensureFolder(token, username, FolderType.JOBS, false);
                ensureFolder(token, username, FolderType.RESULTS, false);
                updateUser(token, username, publicKey);
            } else if (matchesJobsProfile(current, username, publicKey)) {
                ensureFolder(token, username, FolderType.JOBS, true);
                ensureFolder(token, username, FolderType.RESULTS, false);
                updateUser(token, username, publicKey);
            } else if (!matches(current, username, publicKey)) {
                throw new DiscoverySftpProvisioningConflictException();
            }
            current = getUser(token, username);
            if (current == null || !matches(current, username, publicKey)) {
                throw new DiscoverySftpProvisioningConflictException();
            }
            ensureFolder(token, username, FolderType.JOBS, true);
            ensureFolder(token, username, FolderType.RESULTS, true);
            return new DiscoveryExecutorActivationResponse(
                    publicHost, publicPort, username, publicHostKey, hostKeyFingerprint);
        } catch (DiscoverySftpProvisioningConflictException
                | DiscoverySftpHostKeyMismatchException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DiscoverySftpProvisioningUnavailableException();
        } catch (Exception exception) {
            throw new DiscoverySftpProvisioningUnavailableException();
        }
    }

    private String token() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(apiUrl + "/token"))
                .timeout(requestTimeout)
                .header("Authorization", basicAuthorization())
                .GET()
                .build());
        if (response.statusCode() != 200) {
            throw new DiscoverySftpProvisioningUnavailableException();
        }
        String token = objectMapper.readTree(response.body()).path("access_token").asText();
        if (token.isBlank()) {
            throw new DiscoverySftpProvisioningUnavailableException();
        }
        return token;
    }

    private void verifyHostKey(String token, String expectedFingerprint) throws Exception {
        HttpResponse<String> response = send(authorized(token, apiUrl + "/status").GET().build());
        if (response.statusCode() != 200) {
            throw new DiscoverySftpProvisioningUnavailableException();
        }
        for (JsonNode key : objectMapper.readTree(response.body()).path("ssh").path("host_keys")) {
            if (expectedFingerprint.equals(key.path("fingerprint").asText())) {
                return;
            }
        }
        throw new DiscoverySftpHostKeyMismatchException();
    }

    private JsonNode getUser(String token, String username) throws Exception {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(authorized(token, apiUrl + "/users/" + encoded).GET().build());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new DiscoverySftpProvisioningUnavailableException();
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
            throw new DiscoverySftpProvisioningUnavailableException();
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
            throw new DiscoverySftpProvisioningUnavailableException();
        }
    }

    private static Map<String, Object> userBody(String username, String publicKey) {
        return Map.of(
                "status", 1,
                "username", username,
                "public_keys", List.of(publicKey),
                "home_dir", homeDirectory(username),
                "permissions", PERMISSIONS,
                "filters", Map.of(
                        "denied_protocols", DENIED_PROTOCOLS,
                        "denied_login_methods", DENIED_LOGIN_METHODS,
                        "max_upload_file_size", MAX_RESULT_BYTES),
                "virtual_folders", List.of(
                        virtualFolder(username, FolderType.JOBS),
                        virtualFolder(username, FolderType.RESULTS)),
                "filesystem", Map.of("provider", 0));
    }

    private static Map<String, Object> virtualFolder(String username, FolderType type) {
        return Map.of(
                "name", folderName(username, type),
                "mapped_path", mappedPath(username, type),
                "virtual_path", type.virtualPath,
                "quota_size", 0,
                "quota_files", 0,
                "filesystem", Map.of("provider", 0));
    }

    private void ensureFolder(String token, String username, FolderType type, Boolean attached)
            throws Exception {
        String name = folderName(username, type);
        JsonNode folder = getFolder(token, name);
        if (folder == null) {
            Map<String, Object> body = Map.of(
                    "name", name,
                    "mapped_path", mappedPath(username, type),
                    "filesystem", Map.of("provider", 0));
            HttpRequest request = authorized(token, apiUrl + "/folders")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            int status = send(request).statusCode();
            if (status != 201 && status != 409) {
                throw new DiscoverySftpProvisioningUnavailableException();
            }
            folder = getFolder(token, name);
        }
        if (folder == null || !matchesFolder(folder, username, type, attached)) {
            throw new DiscoverySftpProvisioningConflictException();
        }
    }

    private JsonNode getFolder(String token, String name) throws Exception {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(authorized(token, apiUrl + "/folders/" + encoded)
                .GET()
                .build());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new DiscoverySftpProvisioningUnavailableException();
        }
        return objectMapper.readTree(response.body());
    }

    private static boolean matches(JsonNode user, String username, String publicKey) {
        return user.isObject()
                && textEquals(user, "username", username)
                && integerEquals(user, "status", 1)
                && optionalBooleanEquals(user, "has_password", false)
                && sameValues(user.path("public_keys"), List.of(publicKey))
                && textEquals(user, "home_dir", homeDirectory(username))
                && permissions(user.path("permissions")).equals(PERMISSIONS)
                && sameValues(user.path("filters").path("denied_protocols"), DENIED_PROTOCOLS)
                && sameValues(
                        user.path("filters").path("denied_login_methods"), DENIED_LOGIN_METHODS)
                && hasFilters(user.path("filters"), MAX_RESULT_BYTES)
                && matchesVirtualFolders(user.path("virtual_folders"), username)
                && emptyArray(user, "groups")
                && hasDefaultAccountLimits(user)
                && hasDefaultIdentityMetadata(user)
                && hasDefaultFilesystem(user.path("filesystem"));
    }

    private static boolean matchesOriginal(JsonNode user, String username, String publicKey) {
        return user.isObject()
                && textEquals(user, "username", username)
                && integerEquals(user, "status", 1)
                && optionalBooleanEquals(user, "has_password", false)
                && sameValues(user.path("public_keys"), List.of(publicKey))
                && textEquals(user, "home_dir", homeDirectory(username))
                && permissions(user.path("permissions")).equals(ORIGINAL_PERMISSIONS)
                && sameValues(user.path("filters").path("denied_protocols"), DENIED_PROTOCOLS)
                && sameValues(
                        user.path("filters").path("denied_login_methods"), DENIED_LOGIN_METHODS)
                && hasFilters(user.path("filters"), 0)
                && emptyArray(user, "virtual_folders")
                && emptyArray(user, "groups")
                && hasDefaultAccountLimits(user)
                && hasDefaultIdentityMetadata(user)
                && hasDefaultFilesystem(user.path("filesystem"));
    }

    private static boolean matchesJobsProfile(JsonNode user, String username, String publicKey) {
        return user.isObject()
                && textEquals(user, "username", username)
                && integerEquals(user, "status", 1)
                && optionalBooleanEquals(user, "has_password", false)
                && sameValues(user.path("public_keys"), List.of(publicKey))
                && textEquals(user, "home_dir", homeDirectory(username))
                && permissions(user.path("permissions")).equals(JOB_PERMISSIONS)
                && sameValues(user.path("filters").path("denied_protocols"), DENIED_PROTOCOLS)
                && sameValues(
                        user.path("filters").path("denied_login_methods"), DENIED_LOGIN_METHODS)
                && hasFilters(user.path("filters"), 0)
                && matchesSingleVirtualFolder(
                        user.path("virtual_folders"), username, FolderType.JOBS)
                && emptyArray(user, "groups")
                && hasDefaultAccountLimits(user)
                && hasDefaultIdentityMetadata(user)
                && hasDefaultFilesystem(user.path("filesystem"));
    }

    private static boolean matchesVirtualFolders(JsonNode folders, String username) {
        if (!folders.isArray() || folders.size() != 2) {
            return false;
        }
        Map<String, JsonNode> byPath = new HashMap<>();
        folders.forEach(folder -> byPath.put(folder.path("virtual_path").asText(), folder));
        return byPath.size() == 2
                && matchesVirtualFolder(byPath.get("/jobs"), username, FolderType.JOBS)
                && matchesVirtualFolder(byPath.get("/results"), username, FolderType.RESULTS);
    }

    private static boolean matchesSingleVirtualFolder(
            JsonNode folders, String username, FolderType type) {
        return folders.isArray()
                && folders.size() == 1
                && matchesVirtualFolder(folders.get(0), username, type);
    }

    private static boolean matchesVirtualFolder(JsonNode folder, String username, FolderType type) {
        return folder != null
                && folderName(username, type).equals(folder.path("name").asText())
                && mappedPath(username, type).equals(folder.path("mapped_path").asText())
                && type.virtualPath.equals(folder.path("virtual_path").asText())
                && folder.path("quota_size").asLong() == 0
                && folder.path("quota_files").asLong() == 0
                && hasDefaultFilesystem(folder.path("filesystem"));
    }

    private static boolean matchesFolder(
            JsonNode folder, String username, FolderType type, Boolean attached) {
        if (!folderName(username, type).equals(folder.path("name").asText())
                || !mappedPath(username, type).equals(folder.path("mapped_path").asText())
                || !folder.path("description").asText().isBlank()
                || !hasDefaultFilesystem(folder.path("filesystem"))
                || !emptyArray(folder, "groups")) {
            return false;
        }
        if (attached == null) return true;
        return attached
                ? sameValues(folder.path("users"), List.of(username))
                : emptyArray(folder, "users");
    }

    private static Map<String, List<String>> permissions(JsonNode node) {
        Map<String, List<String>> result = new HashMap<>();
        if (!node.isObject()) {
            return result;
        }
        node.properties().forEach(entry -> {
            List<String> values = stringList(entry.getValue());
            if (values != null) {
                result.put(entry.getKey(), values);
            }
        });
        if (result.size() != node.size()) {
            result.clear();
        }
        return result;
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                return null;
            }
            result.add(value.asText());
        }
        return result;
    }

    private static boolean sameValues(JsonNode node, List<String> expected) {
        List<String> actual = stringList(node);
        return actual != null
                && actual.size() == expected.size()
                && Set.copyOf(actual).equals(Set.copyOf(expected));
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
                && blankText(user, "role");
    }

    private static boolean hasDefaultIdentityMetadata(JsonNode user) {
        return zero(user, "uid")
                && zero(user, "gid")
                && blankText(user, "email")
                && blankText(user, "description")
                && blankText(user, "additional_info")
                && emptyObject(user, "oidc_custom_fields");
    }

    private static boolean hasDefaultFilesystem(JsonNode filesystem) {
        return filesystem.isObject()
                && integerEquals(filesystem, "provider", 0)
                && emptyObject(filesystem, "osconfig")
                && hasOnlyEmptyObjects(
                        filesystem.path("s3config"), Set.of("access_secret", "sse_customer_key"))
                && hasOnlyEmptyObjects(filesystem.path("gcsconfig"), Set.of("credentials"))
                && hasOnlyEmptyObjects(
                        filesystem.path("azblobconfig"), Set.of("account_key", "sas_url"))
                && hasOnlyEmptyObjects(filesystem.path("cryptconfig"), Set.of("passphrase"))
                && hasOnlyEmptyObjects(
                        filesystem.path("sftpconfig"),
                        Set.of("password", "private_key", "key_passphrase"))
                && hasOnlyEmptyObjects(
                        filesystem.path("httpconfig"), Set.of("password", "api_key"));
    }

    private static boolean hasOnlyEmptyObjects(JsonNode node, Set<String> allowedFields) {
        if (!node.isObject()) {
            return false;
        }
        var fields = node.properties().iterator();
        while (fields.hasNext()) {
            var field = fields.next();
            JsonNode value = field.getValue();
            if (!allowedFields.contains(field.getKey())
                    || !(value.isNull()
                            || (value.isTextual() && value.asText().isEmpty())
                            || ((value.isObject() || value.isArray()) && value.isEmpty()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasFilters(JsonNode filters, long maxUploadFileSize) {
        return filters.isObject()
                && emptyArray(filters, "allowed_ip")
                && emptyArray(filters, "denied_ip")
                && emptyArray(filters, "file_patterns")
                && emptyArray(filters, "bandwidth_limits")
                && emptyArray(filters, "access_time")
                && emptyArray(filters, "two_factor_protocols")
                && emptyArray(filters, "tls_certs")
                && emptyArray(filters, "recovery_codes")
                && emptyArray(filters, "additional_emails")
                && emptyArray(filters, "web_client")
                && optionalIntegerEquals(filters, "max_upload_file_size", maxUploadFileSize)
                && zero(filters, "external_auth_cache_time")
                && zero(filters, "password_expiration")
                && zero(filters, "password_strength")
                && zero(filters, "default_shares_expiration")
                && zero(filters, "max_shares_expiration")
                && zero(filters, "ftp_security")
                && optionalBooleanEquals(filters, "allow_api_key_auth", false)
                && optionalBooleanEquals(filters, "is_anonymous", false)
                && optionalBooleanEquals(filters, "require_password_change", false)
                && optionalBooleanEquals(filters, "disable_fs_checks", false)
                && blankText(filters, "user_type")
                && blankText(filters, "start_directory")
                && blankText(filters, "tls_username")
                && booleanEquals(filters.path("hooks"), "external_auth_disabled", false)
                && booleanEquals(filters.path("hooks"), "pre_login_disabled", false)
                && booleanEquals(filters.path("hooks"), "check_password_disabled", false)
                && optionalBooleanEquals(filters.path("totp_config"), "enabled", false)
                && blankText(filters.path("totp_config"), "config_name")
                && emptyArray(filters.path("totp_config"), "protocols")
                && emptyObject(filters.path("totp_config"), "secret");
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
                || publicHost.length() > 253
                || publicHost.chars().anyMatch(character -> character < 33 || character == 127)
                || publicPort < 1
                || publicPort > 65535
                || publicHostKey.isBlank()) {
            throw new DiscoverySftpProvisioningUnavailableException();
        }
    }

    private static String normalizeHostKey(String value) {
        String[] parts = value == null ? new String[0] : value.trim().split("\\s+");
        return parts.length == 2 && "ssh-ed25519".equals(parts[0])
                ? parts[0] + " " + parts[1]
                : "";
    }

    private static String fingerprint(String key) throws Exception {
        String[] parts = key.split(" ");
        byte[] blob = Base64.getDecoder().decode(parts[1]);
        ByteBuffer buffer = ByteBuffer.wrap(blob);
        if (buffer.remaining() < 4) {
            throw new DiscoverySftpHostKeyMismatchException();
        }
        int length = buffer.getInt();
        if (length != "ssh-ed25519".length() || buffer.remaining() < length + 4 + 32) {
            throw new DiscoverySftpHostKeyMismatchException();
        }
        byte[] algorithm = new byte[length];
        buffer.get(algorithm);
        if (!"ssh-ed25519".equals(new String(algorithm, StandardCharsets.US_ASCII))) {
            throw new DiscoverySftpHostKeyMismatchException();
        }
        return "SHA256:" + Base64.getEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(blob));
    }

    private static String homeDirectory(String username) {
        return "/srv/sftpgo/data/" + username;
    }

    private static String folderName(String username, FolderType type) {
        return username + type.suffix;
    }

    private static String mappedPath(String username, FolderType type) {
        return type.mappedRoot + "/" + folderName(username, type);
    }

    private static boolean textEquals(JsonNode parent, String field, String expected) {
        JsonNode value = parent.get(field);
        return value != null && value.isTextual() && expected.equals(value.asText());
    }

    private static boolean blankText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        return value == null || (value.isTextual() && value.asText().isBlank());
    }

    private static boolean integerEquals(JsonNode parent, String field, long expected) {
        JsonNode value = parent.get(field);
        return value != null && value.isIntegralNumber() && value.asLong() == expected;
    }

    private static boolean booleanEquals(JsonNode parent, String field, boolean expected) {
        JsonNode value = parent.get(field);
        return value != null && value.isBoolean() && value.asBoolean() == expected;
    }

    private static boolean optionalBooleanEquals(JsonNode parent, String field, boolean expected) {
        JsonNode value = parent.get(field);
        return value == null || (value.isBoolean() && value.asBoolean() == expected);
    }

    private static boolean optionalIntegerEquals(JsonNode parent, String field, long expected) {
        JsonNode value = parent.get(field);
        return value == null
                ? expected == 0
                : value.isIntegralNumber() && value.asLong() == expected;
    }

    private static boolean emptyArray(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        return value == null || (value.isArray() && value.isEmpty());
    }

    private static boolean emptyObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        return value == null || (value.isObject() && value.isEmpty());
    }

    private static boolean zero(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        return value == null || (value.isIntegralNumber() && value.asLong() == 0);
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private enum FolderType {
        JOBS("-jobs", "/jobs", "/srv/sftpgo/discovery-jobs"),
        RESULTS("-results", "/results", "/srv/sftpgo/discovery-results");

        private final String suffix;
        private final String virtualPath;
        private final String mappedRoot;

        FolderType(String suffix, String virtualPath, String mappedRoot) {
            this.suffix = suffix;
            this.virtualPath = virtualPath;
            this.mappedRoot = mappedRoot;
        }
    }
}

final class DiscoveryExecutorPublicKeyRequiredException extends RuntimeException {}

final class DiscoveryExecutorActiveScopeRequiredException extends RuntimeException {}

final class DiscoveryExecutorStateConflictException extends RuntimeException {}

final class DiscoverySftpProvisioningConflictException extends RuntimeException {}

final class DiscoverySftpProvisioningUnavailableException extends RuntimeException {}

final class DiscoverySftpHostKeyMismatchException extends RuntimeException {}
