package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Ai.Admin",
            "jaxon.identity.bootstrap.password=AI source test passphrase 42!"
        })
class AiSourceIntegrationTest {

    private static final String USERNAME = "Ai.Admin";
    private static final String PASSWORD = "AI source test passphrase 42!";
    private static final Path STORAGE_ROOT = createStorageRoot();

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

    @AfterAll
    static void removeStorageRoot() throws Exception {
        try (var files = Files.list(STORAGE_ROOT)) {
            files.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // Test cleanup only.
                }
            });
        }
        Files.deleteIfExists(STORAGE_ROOT);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jaxon.ai.document-storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void clearSources() throws Exception {
        jdbc.update("DELETE FROM ai.outbox_events");
        jdbc.update("DELETE FROM ai.document_sources");
        try (var files = Files.list(STORAGE_ROOT)) {
            for (Path file : files.toList()) Files.delete(file);
        }
    }

    @AfterEach
    void restoreAdministratorRole() {
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'SYSTEM_ADMIN' FROM identity.human_users
                WHERE normalized_username = 'ai.admin'
                ON CONFLICT DO NOTHING
                """);
        jdbc.execute("DROP TRIGGER IF EXISTS reject_ai_outbox ON ai.outbox_events");
        jdbc.execute("DROP FUNCTION IF EXISTS ai.reject_ai_outbox()");
    }

    @Test
    void registersListsAndGetsAStoredUtf8SourceWithoutLeakingItsPhysicalKey() throws Exception {
        Session session = login();
        byte[] content = "Guía técnica autorizada\nSegunda línea.\n".getBytes(StandardCharsets.UTF_8);
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> created = upload(
                session, "  Guía   de soporte  ", "manual-original.txt", "text/plain", content, correlationId);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID sourceId = UUID.fromString(extract(created.body(), "sourceId"));
        assertThat(created.headers().firstValue("location"))
                .contains("/api/v1/ai/sources/" + sourceId);
        assertThat(created.body())
                .contains("\"name\":\"Guía de soporte\"")
                .contains("\"mediaType\":\"text/plain\"")
                .contains("\"sizeBytes\":" + content.length)
                .contains("\"sha256\":\"" + sha256(content) + "\"")
                .contains("\"status\":\"PENDING\"")
                .contains("\"version\":0")
                .doesNotContain("manual-original.txt", "storageKey", STORAGE_ROOT.toString());

        Map<String, Object> source = jdbc.queryForMap(
                """
                SELECT name, media_type, size_bytes, sha256, storage_key, status,
                       created_by_user_id, version
                FROM ai.document_sources WHERE id = ?
                """,
                sourceId);
        String storageKey = (String) source.get("storage_key");
        assertThat(source)
                .containsEntry("name", "Guía de soporte")
                .containsEntry("media_type", "text/plain")
                .containsEntry("size_bytes", (long) content.length)
                .containsEntry("status", "PENDING")
                .containsEntry("version", 0L);
        assertThat(((String) source.get("sha256")).trim()).isEqualTo(sha256(content));
        assertThat(Files.readAllBytes(STORAGE_ROOT.resolve(storageKey))).isEqualTo(content);

        Map<String, Object> event = jdbc.queryForMap(
                """
                SELECT event_type, event_version, producer_module, resource_type,
                       resource_version, correlation_id, payload::text AS payload
                FROM ai.outbox_events WHERE resource_id = ?
                """,
                sourceId);
        assertThat(event)
                .containsEntry("event_type", "AI_SOURCE_REGISTERED")
                .containsEntry("event_version", "1.0")
                .containsEntry("producer_module", "AI")
                .containsEntry("resource_type", "AI_SOURCE")
                .containsEntry("resource_version", 0L)
                .containsEntry("correlation_id", correlationId)
                .containsEntry("payload", "{\"status\": \"PENDING\"}");
        assertThat(event.toString()).doesNotContain("Guía", storageKey, STORAGE_ROOT.toString());

        HttpResponse<String> detail = get(session, "/api/v1/ai/sources/" + sourceId);
        HttpResponse<String> page = get(session, "/api/v1/ai/sources?page=0&size=20");
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).isEqualTo(created.body());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body())
                .contains("\"sourceId\":\"" + sourceId + "\"")
                .contains("\"page\":0", "\"size\":20", "\"totalElements\":1", "\"totalPages\":1")
                .doesNotContain("storageKey", storageKey);
    }

    @Test
    void acceptsIdenticalBytesAsDistinctSources() throws Exception {
        Session session = login();
        byte[] content = "same bytes".getBytes(StandardCharsets.UTF_8);

        UUID first = UUID.fromString(extract(
                upload(session, "First", "first.txt", "text/plain", content, UUID.randomUUID()).body(),
                "sourceId"));
        UUID second = UUID.fromString(extract(
                upload(session, "Second", "second.txt", "text/plain", content, UUID.randomUUID()).body(),
                "sourceId"));

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForList(
                        "SELECT storage_key FROM ai.document_sources ORDER BY name", String.class))
                .hasSize(2)
                .doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ai.outbox_events", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void rejectsInvalidInputPaginationAuthorizationAndCsrf() throws Exception {
        Session administrator = login();
        assertThat(upload(administrator, "Binary", "binary.bin", "application/octet-stream", new byte[] {1}, UUID.randomUUID())
                        .body())
                .contains("\"code\":\"VALIDATION_FAILED\"", "UNSUPPORTED_MEDIA_TYPE");
        assertThat(upload(administrator, "Invalid UTF-8", "bad.txt", "text/plain", new byte[] {(byte) 0xc3, 0x28}, UUID.randomUUID())
                        .body())
                .contains("\"code\":\"VALIDATION_FAILED\"", "INVALID_UTF8");
        assertThat(upload(administrator, "   ", "empty-name.txt", "text/plain", new byte[0], UUID.randomUUID())
                        .statusCode())
                .isEqualTo(422);
        assertThat(get(administrator, "/api/v1/ai/sources?page=-1&size=51").statusCode())
                .isEqualTo(422);
        assertThat(get(administrator, "/api/v1/ai/sources/not-a-uuid").statusCode())
                .isEqualTo(404);
        assertThat(uploadWithExtraPart(administrator).statusCode()).isEqualTo(400);

        HttpResponse<String> noCsrf = upload(
                new Session(administrator.cookie(), "wrong"),
                "No CSRF",
                "no-csrf.txt",
                "text/plain",
                "safe".getBytes(StandardCharsets.UTF_8),
                UUID.randomUUID());
        assertThat(noCsrf.statusCode()).isEqualTo(403);

        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'SYSTEM_ADMIN'");
        jdbc.update(
                """
                INSERT INTO identity.human_user_roles (user_id, role_code)
                SELECT id, 'IT_OPERATOR' FROM identity.human_users
                WHERE normalized_username = 'ai.admin'
                ON CONFLICT DO NOTHING
                """);
        Session operator = login();
        assertThat(get(operator, "/api/v1/ai/sources").statusCode()).isEqualTo(403);
        assertThat(upload(operator, "Denied", "denied.txt", "text/plain", new byte[0], UUID.randomUUID())
                        .statusCode())
                .isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.document_sources", Integer.class))
                .isZero();
    }

    @Test
    void acceptsExactlyOneMebibyteAndRejectsOneAdditionalByte() throws Exception {
        Session administrator = login();
        byte[] maximum = new byte[(int) FileSystemDocumentStorage.MAX_BYTES];
        assertThat(upload(administrator, "Maximum", "maximum.txt", "text/plain", maximum, UUID.randomUUID())
                        .statusCode())
                .isEqualTo(201);

        byte[] oversized = new byte[(int) FileSystemDocumentStorage.MAX_BYTES + 1];
        assertThat(upload(administrator, "Oversized", "oversized.txt", "text/plain", oversized, UUID.randomUUID())
                        .statusCode())
                .isEqualTo(413);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.document_sources", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void removesTheStoredFileAndPublishesNoMetadataWhenTheDatabaseCommitFails() throws Exception {
        Session session = login();
        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION ai.reject_ai_outbox() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced outbox failure'; END; $$
                """);
        jdbc.execute(
                """
                CREATE CONSTRAINT TRIGGER reject_ai_outbox AFTER INSERT ON ai.outbox_events
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW EXECUTE FUNCTION ai.reject_ai_outbox()
                """);

        HttpResponse<String> response = upload(
                session,
                "Rollback",
                "rollback.txt",
                "text/plain",
                "rollback".getBytes(StandardCharsets.UTF_8),
                UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai.document_sources", Integer.class))
                .isZero();
        try (var files = Files.list(STORAGE_ROOT)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    private Session login() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return new Session(
                response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0],
                extract(response.body(), "csrfToken"));
    }

    private HttpResponse<String> get(Session session, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", session.cookie())
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> upload(
            Session session,
            String name,
            String filename,
            String mediaType,
            byte[] content,
            UUID correlationId) throws Exception {
        String boundary = "JAXON-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"name\"\r\n\r\n");
        write(body, name + "\r\n");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        write(body, "Content-Type: " + mediaType + "\r\n\r\n");
        body.write(content);
        write(body, "\r\n--" + boundary + "--\r\n");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ai/sources"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("X-Correlation-ID", correlationId.toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> uploadWithExtraPart(Session session) throws Exception {
        String boundary = "JAXON-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"name\"\r\n\r\nClosed request\r\n");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"source.txt\"\r\n");
        write(body, "Content-Type: text/plain\r\n\r\nvalid\r\n");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"unexpected\"\r\n\r\nvalue\r\n");
        write(body, "--" + boundary + "--\r\n");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ai/sources"))
                .header("Cookie", session.cookie())
                .header("X-CSRF-Token", session.csrfToken())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void write(ByteArrayOutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String extract(String body, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        if (!matcher.find()) throw new AssertionError("Missing " + field + " in " + body);
        return matcher.group(1);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("jaxon-ai-sources-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Session(String cookie, String csrfToken) {}
}
