package com.jaxon.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.human_users", Integer.class)).isEqualTo(1);
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
                        "SELECT count(*) FROM identity.outbox_events WHERE event_type = 'HUMAN_LOGIN_SUCCEEDED'",
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
        assertThat(getMe(secondCookie).statusCode()).isEqualTo(401);

        HttpResponse<String> thirdLogin = login("Bootstrap.Admin", PASSWORD);
        String thirdCookie = thirdLogin.headers().firstValue("set-cookie").orElseThrow();
        thirdCookie = thirdCookie.substring(0, thirdCookie.indexOf(';'));
        jdbc.update("UPDATE identity.human_users SET status = 'DISABLED'");
        try {
            assertThat(getMe(thirdCookie).statusCode()).isEqualTo(401);
        } finally {
            jdbc.update("UPDATE identity.human_users SET status = 'ACTIVE'");
        }
    }

    @Test
    void enforcesCsrfBeforeDefaultDenyOnAuthenticatedMutations() throws Exception {
        HttpResponse<String> login = login("Bootstrap.Admin", PASSWORD);
        String setCookie = login.headers().firstValue("set-cookie").orElseThrow();
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        var matcher = Pattern.compile("\\\"csrfToken\\\":\\\"([^\\\"]+)\\\"").matcher(login.body());
        assertThat(matcher.find()).isTrue();
        String csrfToken = matcher.group(1);

        HttpResponse<String> missingCsrf = postAsset(cookie, null);
        assertThat(missingCsrf.statusCode()).isEqualTo(403);
        assertThat(missingCsrf.body()).contains("\"code\":\"CSRF_INVALID\"");

        HttpResponse<String> deniedByDefault = postAsset(cookie, csrfToken);
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

    private void assertGenericAuthenticationFailure(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body())
                .contains("\"code\":\"AUTHENTICATION_FAILED\"")
                .contains("Las credenciales proporcionadas no son válidas.")
                .doesNotContain("missing", "disabled", "password");
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
}
