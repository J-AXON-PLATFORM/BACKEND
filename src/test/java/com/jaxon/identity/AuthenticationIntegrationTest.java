package com.jaxon.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Bootstrap.Admin",
            "jaxon.identity.bootstrap.password=Test-only passphrase 42!"
        })
class AuthenticationIntegrationTest {

    private static final String PASSWORD = "Test-only passphrase 42!";

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

    @Autowired
    IdentityBootstrap bootstrap;

    @Test
    void bootstrapsOneActiveUserWithExplicitRolesAndArgon2id() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.human_users WHERE normalized_username = 'bootstrap.admin'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT password_hash FROM identity.human_users WHERE normalized_username = 'bootstrap.admin'",
                        String.class))
                .startsWith("$argon2id$")
                .contains("m=19456,t=2,p=1");
        assertThat(jdbc.queryForList(
                        """
                        SELECT r.role_code
                        FROM identity.human_user_roles r
                        JOIN identity.human_users u ON u.id = r.user_id
                        WHERE u.normalized_username = 'bootstrap.admin'
                        ORDER BY r.role_code
                        """,
                        String.class))
                .containsExactly("IT_OPERATOR", "SYSTEM_ADMIN");
        assertThatThrownBy(() -> bootstrap.run(null))
                .hasMessage("Identity bootstrap requires an empty human user store");
    }

    @Test
    void logsInWithOpaqueCookieAndRecoversThePersistedSession() throws Exception {
        HttpResponse<String> login = login(" bootstrap.ADMIN ", PASSWORD);

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        String setCookie = login.headers().firstValue("set-cookie").orElseThrow();
        assertThat(setCookie)
                .startsWith("__Host-JAXON_SESSION=")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .doesNotContain("Domain=", "Max-Age=", "Expires=");
        assertThat(login.body())
                .contains("\"username\":\"Bootstrap.Admin\"")
                .contains("\"IT_OPERATOR\"")
                .contains("\"SYSTEM_ADMIN\"")
                .contains("\"csrfToken\"");

        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        HttpResponse<String> me = getMe(cookie);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"Bootstrap.Admin\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.human_sessions", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM identity.outbox_events
                        WHERE event_type = 'HUMAN_LOGIN_SUCCEEDED'
                          AND event_version = '2.0'
                          AND payload = '{}'::jsonb
                        """,
                        Integer.class))
                .isPositive();
    }

    @Test
    void rejectsUnknownWrongAndDisabledUsersWithTheSameExternalProblem() throws Exception {
        HttpResponse<String> unknown = login("missing-" + UUID.randomUUID(), "Wrong passphrase 42!");
        HttpResponse<String> wrong = login("Bootstrap.Admin", "Wrong passphrase 42!");
        jdbc.update("UPDATE identity.human_users SET status = 'DISABLED'");
        HttpResponse<String> disabled;
        try {
            disabled = login("Bootstrap.Admin", PASSWORD);
        } finally {
            jdbc.update("UPDATE identity.human_users SET status = 'ACTIVE'");
        }

        assertGenericAuthenticationFailure(unknown);
        assertGenericAuthenticationFailure(wrong);
        assertGenericAuthenticationFailure(disabled);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM identity.outbox_events
                        WHERE event_type = 'HUMAN_LOGIN_FAILED' AND result = 'DENIED'
                        """,
                        Integer.class))
                .isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM identity.outbox_events
                        WHERE event_type = 'HUMAN_LOGIN_FAILED' AND actor_id IS NULL
                        """,
                        Integer.class))
                .isPositive();
    }

    @Test
    void allowsFiveFailuresThenRateLimitsTheSixthAndSuccessClearsFailures() throws Exception {
        String username = "rate-" + UUID.randomUUID();
        int attemptsBefore = jdbc.queryForObject("SELECT count(*) FROM identity.login_attempts", Integer.class);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(login(username, "Wrong passphrase 42!").statusCode()).isEqualTo(401);
        }
        int deniedEvents = jdbc.queryForObject(
                "SELECT count(*) FROM identity.outbox_events WHERE event_type = 'HUMAN_LOGIN_FAILED'",
                Integer.class);
        assertThat(login(username, "Wrong passphrase 42!").statusCode()).isEqualTo(429);
        assertThat(login(username, "Wrong passphrase 42!").statusCode()).isEqualTo(429);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.login_attempts", Integer.class))
                .isEqualTo(attemptsBefore + 5);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.outbox_events WHERE event_type = 'HUMAN_LOGIN_FAILED'",
                        Integer.class))
                .isEqualTo(deniedEvents);

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThat(login("Bootstrap.Admin", "Wrong passphrase 42!").statusCode()).isEqualTo(401);
        }
        assertThat(login("Bootstrap.Admin", PASSWORD).statusCode()).isEqualTo(200);
        assertThat(login("Bootstrap.Admin", "Wrong passphrase 42!").statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsAbsentAndExpiredSessions() throws Exception {
        assertThat(getMe(null).statusCode()).isEqualTo(401);
        HttpResponse<String> unknown = getMe("__Host-JAXON_SESSION=unknown-session");
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertExpiredSessionCookie(unknown);

        HttpResponse<String> login = login("Bootstrap.Admin", PASSWORD);
        String setCookie = login.headers().firstValue("set-cookie").orElseThrow();
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        jdbc.update(
                """
                UPDATE identity.human_sessions
                SET created_at = now() - interval '32 minutes',
                    last_activity_at = now() - interval '31 minutes'
                """);

        HttpResponse<String> expired = getMe(cookie);
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(expired.body()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
        assertExpiredSessionCookie(expired);

        HttpResponse<String> secondLogin = login("Bootstrap.Admin", PASSWORD);
        String secondCookie = secondLogin.headers().firstValue("set-cookie").orElseThrow();
        secondCookie = secondCookie.substring(0, secondCookie.indexOf(';'));
        jdbc.update(
                """
                UPDATE identity.human_sessions
                SET created_at = now() - interval '13 hours',
                    last_activity_at = now() - interval '1 hour',
                    absolute_expires_at = now() - interval '1 second'
                """);
        HttpResponse<String> absoluteExpired = getMe(secondCookie);
        assertThat(absoluteExpired.statusCode()).isEqualTo(401);
        assertExpiredSessionCookie(absoluteExpired);

        HttpResponse<String> thirdLogin = login("Bootstrap.Admin", PASSWORD);
        String thirdCookie = thirdLogin.headers().firstValue("set-cookie").orElseThrow();
        thirdCookie = thirdCookie.substring(0, thirdCookie.indexOf(';'));
        jdbc.update("UPDATE identity.human_users SET status = 'DISABLED'");
        try {
            HttpResponse<String> disabled = getMe(thirdCookie);
            assertThat(disabled.statusCode()).isEqualTo(401);
            assertExpiredSessionCookie(disabled);
        } finally {
            jdbc.update("UPDATE identity.human_users SET status = 'ACTIVE'");
        }
    }

    @Test
    void rejectsCsrfWithoutRenewingAndRenewsValidAuthenticatedMutations() throws Exception {
        HttpResponse<String> login = login("Bootstrap.Admin", PASSWORD);
        String setCookie = login.headers().firstValue("set-cookie").orElseThrow();
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        var matcher = Pattern.compile("\\\"csrfToken\\\":\\\"([^\\\"]+)\\\"").matcher(login.body());
        assertThat(matcher.find()).isTrue();
        String csrfToken = matcher.group(1);
        jdbc.update(
                """
                UPDATE identity.human_sessions
                SET created_at = now() - interval '6 minutes',
                    last_activity_at = now() - interval '5 minutes'
                WHERE token_hash = ?
                """,
                tokenHash(cookie));
        Timestamp activityBefore = lastActivity(cookie);

        HttpResponse<String> missingCsrf = postAsset(cookie, null);
        assertThat(missingCsrf.statusCode()).isEqualTo(403);
        assertThat(missingCsrf.body()).contains("\"code\":\"CSRF_INVALID\"");
        assertThat(lastActivity(cookie)).isEqualTo(activityBefore);

        HttpResponse<String> validCsrf = postAsset(cookie, csrfToken);
        assertThat(validCsrf.statusCode()).isEqualTo(422);
        assertThat(lastActivity(cookie)).isAfter(activityBefore);

        HttpResponse<String> deniedByDefault = postDeniedPath(cookie, csrfToken);
        assertThat(deniedByDefault.statusCode()).isEqualTo(403);
        assertThat(deniedByDefault.body()).contains("\"code\":\"ACCESS_DENIED\"");
    }

    @Test
    void enforcesTheClosedLoginRequestAndProblemDetailsBoundary() throws Exception {
        HttpResponse<String> extraProperty = postLogin(
                "application/json",
                "{\"username\":\"Bootstrap.Admin\",\"password\":\"value\",\"extra\":true}");
        assertThat(extraProperty.statusCode()).isEqualTo(400);
        assertThat(extraProperty.body()).contains("\"code\":\"MALFORMED_REQUEST\"");

        HttpResponse<String> unsupported = postLogin("text/plain", "not-json");
        assertThat(unsupported.statusCode()).isEqualTo(400);
        assertThat(unsupported.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(unsupported.body()).contains("\"code\":\"MALFORMED_REQUEST\"");
    }

    @Test
    void createsAnActiveHumanUserWithExplicitRolesAndAllowsSupportOnlyAuthentication() throws Exception {
        SessionCredentials administrator = session(login("Bootstrap.Admin", PASSWORD));
        String suffix = UUID.randomUUID().toString();
        String username = "Support." + suffix;
        HttpResponse<String> created = postUser(
                administrator.cookie(),
                administrator.csrfToken(),
                "{\"username\":\"  " + username + "  \","
                        + "\"password\":\"Support-only passphrase 42!\","
                        + "\"roles\":[\"SUPPORT_TECHNICIAN\",\"AUDITOR\"]}");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("location")).hasValueSatisfying(
                value -> assertThat(value).startsWith("/api/v1/users/"));
        assertThat(created.body())
                .contains("\"username\":\"" + username + "\"")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"roles\":[\"AUDITOR\",\"SUPPORT_TECHNICIAN\"]")
                .doesNotContain("password", "passwordHash");
        UUID userId = UUID.fromString(created.headers().firstValue("location").orElseThrow()
                .substring("/api/v1/users/".length()));
        assertThat(jdbc.queryForObject(
                        "SELECT normalized_username FROM identity.human_users WHERE id = ?",
                        String.class,
                        userId))
                .isEqualTo(username.toLowerCase());
        assertThat(jdbc.queryForObject(
                        "SELECT password_hash FROM identity.human_users WHERE id = ?",
                        String.class,
                        userId))
                .startsWith("$argon2id$");
        assertThat(jdbc.queryForList(
                        "SELECT role_code FROM identity.human_user_roles WHERE user_id = ? ORDER BY role_code",
                        String.class,
                        userId))
                .containsExactly("AUDITOR", "SUPPORT_TECHNICIAN");
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM identity.outbox_events
                        WHERE event_type = 'HUMAN_USER_CREATED'
                          AND actor_id = ? AND resource_id = ?
                          AND event_version = '2.0'
                          AND payload = '{"status":"ACTIVE","roles":"AUDITOR|SUPPORT_TECHNICIAN"}'::jsonb
                        """,
                        Integer.class,
                        administrator.userId(),
                        userId))
                .isEqualTo(1);

        HttpResponse<String> supportLogin = login(username.toUpperCase(), "Support-only passphrase 42!");
        assertThat(supportLogin.statusCode()).isEqualTo(200);
        assertThat(supportLogin.body())
                .contains("\"roles\":[\"AUDITOR\",\"SUPPORT_TECHNICIAN\"]")
                .doesNotContain("IT_OPERATOR", "SYSTEM_ADMIN");
        SessionCredentials support = session(supportLogin);
        assertThat(postUser(
                                support.cookie(),
                                support.csrfToken(),
                                "{\"username\":\"denied-user\",\"password\":\"Valid passphrase 42!\","
                                        + "\"roles\":[\"END_USER\"]}")
                        .statusCode())
                .isEqualTo(403);
    }

    @Test
    void rejectsInvalidAndDuplicateHumanUsersWithoutPartialPersistence() throws Exception {
        SessionCredentials administrator = session(login("Bootstrap.Admin", PASSWORD));
        String suffix = UUID.randomUUID().toString();
        String username = "duplicate." + suffix;
        String valid = "{\"username\":\"" + username + "\",\"password\":\"Valid passphrase 42!\","
                + "\"roles\":[\"END_USER\"]}";
        assertThat(postUser(administrator.cookie(), administrator.csrfToken(), valid).statusCode()).isEqualTo(201);
        int usersAfterCreation = jdbc.queryForObject("SELECT count(*) FROM identity.human_users", Integer.class);
        int eventsAfterCreation = jdbc.queryForObject(
                "SELECT count(*) FROM identity.outbox_events WHERE event_type = 'HUMAN_USER_CREATED'",
                Integer.class);

        assertThat(postUser(
                                administrator.cookie(),
                                administrator.csrfToken(),
                                "{\"username\":\"  " + username.toUpperCase() + "  \","
                                        + "\"password\":\"Valid passphrase 42!\",\"roles\":[\"END_USER\"]}")
                        .statusCode())
                .isEqualTo(409);
        assertThat(postUser(
                                administrator.cookie(),
                                administrator.csrfToken(),
                                "{\"username\":\"weak-" + suffix + "\",\"password\":\"short\","
                                        + "\"roles\":[\"END_USER\"]}")
                        .statusCode())
                .isEqualTo(422);
        assertThat(postUser(
                                administrator.cookie(),
                                administrator.csrfToken(),
                                "{\"username\":\"blocked-" + suffix + "\",\"password\":\"123456789012345\","
                                        + "\"roles\":[\"END_USER\"]}")
                        .statusCode())
                .isEqualTo(422);
        assertThat(postUser(
                                administrator.cookie(),
                                administrator.csrfToken(),
                                "{\"username\":\"role-" + suffix + "\",\"password\":\"Valid passphrase 42!\","
                                        + "\"roles\":[\"END_USER\",\"END_USER\"]}")
                        .statusCode())
                .isEqualTo(422);
        assertThat(postUser(
                                administrator.cookie(),
                                administrator.csrfToken(),
                                "{\"username\":\"unknown-" + suffix + "\",\"password\":\"Valid passphrase 42!\","
                                        + "\"roles\":[\"SUPERUSER\"]}")
                        .statusCode())
                .isEqualTo(422);
        assertThat(postUser(
                                administrator.cookie(),
                                administrator.csrfToken(),
                                valid.substring(0, valid.length() - 1) + ",\"extra\":true}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.human_users", Integer.class))
                .isEqualTo(usersAfterCreation);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.outbox_events WHERE event_type = 'HUMAN_USER_CREATED'",
                        Integer.class))
                .isEqualTo(eventsAfterCreation);
    }

    @Test
    void logsOutOnlyThePresentedSessionAfterCsrfValidation() throws Exception {
        SessionCredentials first = session(login("Bootstrap.Admin", PASSWORD));
        SessionCredentials second = session(login("Bootstrap.Admin", PASSWORD));

        HttpResponse<String> missingCsrf = postLogout(first.cookie(), null);
        assertThat(missingCsrf.statusCode()).isEqualTo(403);
        assertThat(getMe(first.cookie()).statusCode()).isEqualTo(200);

        HttpResponse<String> logout = postLogout(first.cookie(), first.csrfToken());
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.body()).isEmpty();
        assertExpiredSessionCookie(logout);
        assertThat(getMe(first.cookie()).statusCode()).isEqualTo(401);
        assertThat(getMe(second.cookie()).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM identity.outbox_events
                        WHERE event_type = 'HUMAN_LOGOUT_SUCCEEDED'
                          AND actor_id = ? AND resource_type = 'HUMAN_SESSION'
                          AND event_version = '2.0'
                          AND payload = '{}'::jsonb
                        """,
                        Integer.class,
                        first.userId()))
                .isEqualTo(1);
    }

    private void assertGenericAuthenticationFailure(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body())
                .contains("\"code\":\"AUTHENTICATION_FAILED\"")
                .contains("Las credenciales proporcionadas no son válidas.")
                .doesNotContain("missing", "disabled", "password");
    }

    private void assertExpiredSessionCookie(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("set-cookie")).hasValueSatisfying(cookie -> assertThat(cookie)
                .startsWith("__Host-JAXON_SESSION=")
                .contains("Path=/", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain="));
    }

    private Timestamp lastActivity(String cookie) {
        return jdbc.queryForObject(
                "SELECT last_activity_at FROM identity.human_sessions WHERE token_hash = ?",
                Timestamp.class,
                tokenHash(cookie));
    }

    private static String tokenHash(String cookie) {
        try {
            String rawToken = cookie.substring(cookie.indexOf('=') + 1);
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return postLogin("application/json", body);
    }

    private HttpResponse<String> postLogin(String contentType, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getMe(String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me"))
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postUser(String cookie, String csrfToken, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/users"))
                        .header("Cookie", cookie)
                        .header("X-CSRF-Token", csrfToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postLogout(String cookie, String csrfToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/logout"))
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (csrfToken != null) {
            request.header("X-CSRF-Token", csrfToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static SessionCredentials session(HttpResponse<String> login) {
        assertThat(login.statusCode()).isEqualTo(200);
        String setCookie = login.headers().firstValue("set-cookie").orElseThrow();
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        var csrfMatcher = Pattern.compile("\\\"csrfToken\\\":\\\"([^\\\"]+)\\\"").matcher(login.body());
        var userMatcher = Pattern.compile("\\\"userId\\\":\\\"([^\\\"]+)\\\"").matcher(login.body());
        assertThat(csrfMatcher.find()).isTrue();
        assertThat(userMatcher.find()).isTrue();
        return new SessionCredentials(cookie, csrfMatcher.group(1), UUID.fromString(userMatcher.group(1)));
    }

    private HttpResponse<String> postAsset(String cookie, String csrfToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/assets"))
                .header("Cookie", cookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        if (csrfToken != null) {
            request.header("X-CSRF-Token", csrfToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postDeniedPath(String cookie, String csrfToken) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/not-authorized"))
                        .header("Cookie", cookie)
                        .header("X-CSRF-Token", csrfToken)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private record SessionCredentials(String cookie, String csrfToken, UUID userId) {}
}
