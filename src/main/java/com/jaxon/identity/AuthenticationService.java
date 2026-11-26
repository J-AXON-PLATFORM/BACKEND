package com.jaxon.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class AuthenticationService {

    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration INACTIVITY_LIMIT = Duration.ofMinutes(30);
    private static final Duration ABSOLUTE_LIMIT = Duration.ofHours(12);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String dummyHash;

    AuthenticationService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(randomToken());
    }

    @Transactional
    LoginResult login(String username, String password, String remoteAddress, UUID correlationId) {
        Instant now = Instant.now(clock);
        String normalizedUsername = IdentityBootstrap.normalizeUsername(username);
        String attemptKey = sha256(normalizedUsername + "\n" + remoteAddress);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0)), 1",
                (resultSet, row) -> resultSet.getInt(2),
                attemptKey);
        jdbc.update("DELETE FROM identity.login_attempts WHERE attempted_at < ?", timestamp(now.minus(ATTEMPT_WINDOW)));
        Integer failures = jdbc.queryForObject(
                "SELECT count(*) FROM identity.login_attempts WHERE attempt_key_hash = ? AND attempted_at >= ?",
                Integer.class,
                attemptKey,
                timestamp(now.minus(ATTEMPT_WINDOW)));
        if (failures != null && failures >= 5) {
            return LoginResult.rateLimited();
        }

        Optional<UserRecord> user = findUser(normalizedUsername);
        String encodedPassword = user.map(UserRecord::passwordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(password, encodedPassword);
        if (user.isEmpty() || !passwordMatches || !"ACTIVE".equals(user.get().status())) {
            recordDeniedAttempt(attemptKey, user.map(UserRecord::id).orElse(null), correlationId, now, "INVALID_CREDENTIALS");
            return LoginResult.invalid();
        }

        UserRecord authenticated = user.orElseThrow();
        jdbc.update("DELETE FROM identity.login_attempts WHERE attempt_key_hash = ?", attemptKey);
        String sessionToken = randomToken();
        String csrfToken = randomToken();
        UUID sessionId = UUID.randomUUID();
        Instant absoluteExpiry = now.plus(ABSOLUTE_LIMIT);
        jdbc.update(
                """
                INSERT INTO identity.human_sessions
                    (id, user_id, token_hash, csrf_token, created_at, last_activity_at, absolute_expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                sessionId,
                authenticated.id(),
                sha256(sessionToken),
                csrfToken,
                timestamp(now),
                timestamp(now),
                timestamp(absoluteExpiry));
        UUID attemptId = UUID.randomUUID();
        insertOutbox(
                "HUMAN_LOGIN_SUCCEEDED",
                "2.0",
                authenticated.id(),
                attemptId,
                "SUCCESS",
                correlationId,
                now,
                Map.of());
        SessionPrincipal principal = new SessionPrincipal(
                authenticated.id(),
                authenticated.username(),
                findRoles(authenticated.id()),
                csrfToken,
                minimum(now.plus(INACTIVITY_LIMIT), absoluteExpiry));
        return LoginResult.success(sessionToken, principal);
    }

    @Transactional
    Optional<SessionPrincipal> resolveSession(String rawToken, boolean renew) {
        Instant now = Instant.now(clock);
        Optional<SessionRecord> session = jdbc.query(
                        """
                        SELECT s.id, s.user_id, u.username, s.csrf_token, s.last_activity_at, s.absolute_expires_at
                        FROM identity.human_sessions s
                        JOIN identity.human_users u ON u.id = s.user_id
                        WHERE s.token_hash = ? AND u.status = 'ACTIVE'
                        """,
                        (resultSet, row) -> new SessionRecord(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("user_id", UUID.class),
                                resultSet.getString("username"),
                                resultSet.getString("csrf_token"),
                                resultSet.getTimestamp("last_activity_at").toInstant(),
                                resultSet.getTimestamp("absolute_expires_at").toInstant()),
                        sha256(rawToken))
                .stream()
                .findFirst();
        if (session.isEmpty()) {
            return Optional.empty();
        }
        SessionRecord current = session.orElseThrow();
        if (!current.absoluteExpiresAt().isAfter(now)
                || !current.lastActivityAt().plus(INACTIVITY_LIMIT).isAfter(now)) {
            jdbc.update("DELETE FROM identity.human_sessions WHERE id = ?", current.id());
            return Optional.empty();
        }
        if (renew) {
            now = jdbc.queryForObject(
                            """
                            UPDATE identity.human_sessions
                            SET last_activity_at = GREATEST(last_activity_at, ?)
                            WHERE id = ?
                            RETURNING last_activity_at
                            """,
                            Timestamp.class,
                            timestamp(now),
                            current.id())
                    .toInstant();
        }
        Instant lastActivity = renew ? now : current.lastActivityAt();
        return Optional.of(new SessionPrincipal(
                current.userId(),
                current.username(),
                findRoles(current.userId()),
                current.csrfToken(),
                minimum(lastActivity.plus(INACTIVITY_LIMIT), current.absoluteExpiresAt())));
    }

    @Transactional
    void logout(String rawToken, UUID actorId, UUID correlationId) {
        Instant now = Instant.now(clock);
        Optional<UUID> sessionId = jdbc.query(
                        "DELETE FROM identity.human_sessions WHERE token_hash = ? RETURNING id",
                        (resultSet, row) -> resultSet.getObject("id", UUID.class),
                        sha256(rawToken))
                .stream()
                .findFirst();
        if (sessionId.isEmpty()) {
            throw new IllegalStateException("Authenticated session disappeared before logout");
        }
        jdbc.update(
                """
                INSERT INTO identity.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, result,
                     correlation_id, payload, next_attempt_at)
                VALUES (?, 'HUMAN_LOGOUT_SUCCEEDED', '2.0', ?, 'IDENTITY',
                        'HUMAN_USER', ?, 'HUMAN_SESSION', ?, 'SUCCESS', ?, '{}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                timestamp(now),
                actorId,
                sessionId.orElseThrow(),
                correlationId,
                timestamp(now));
    }

    private Optional<UserRecord> findUser(String normalizedUsername) {
        return jdbc.query(
                        """
                        SELECT id, username, password_hash, status
                        FROM identity.human_users
                        WHERE normalized_username = ?
                        """,
                        (resultSet, row) -> new UserRecord(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("username"),
                                resultSet.getString("password_hash"),
                                resultSet.getString("status")),
                        normalizedUsername)
                .stream()
                .findFirst();
    }

    private List<String> findRoles(UUID userId) {
        return jdbc.queryForList(
                "SELECT role_code FROM identity.human_user_roles WHERE user_id = ? ORDER BY role_code",
                String.class,
                userId);
    }

    private void recordDeniedAttempt(
            String attemptKey, UUID actorId, UUID correlationId, Instant now, String reason) {
        UUID attemptId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO identity.login_attempts (id, attempt_key_hash, attempted_at) VALUES (?, ?, ?)",
                attemptId,
                attemptKey,
                timestamp(now));
        insertOutbox(
                "HUMAN_LOGIN_FAILED",
                "2.0",
                actorId,
                attemptId,
                "DENIED",
                correlationId,
                now,
                Map.of("reason", reason));
    }

    private void insertOutbox(
            String eventType,
            String eventVersion,
            UUID actorId,
            UUID resourceId,
            String result,
            UUID correlationId,
            Instant now,
            Map<String, String> payload) {
        try {
            jdbc.update(
                    """
                    INSERT INTO identity.outbox_events
                        (event_id, event_type, event_version, occurred_at, producer_module,
                         actor_type, actor_id, resource_type, resource_id, result,
                         correlation_id, payload, next_attempt_at)
                    VALUES (?, ?, ?, ?, 'IDENTITY', 'HUMAN_USER', ?,
                            'AUTHENTICATION_ATTEMPT', ?, ?, ?, CAST(? AS jsonb), ?)
                    """,
                    UUID.randomUUID(),
                    eventType,
                    eventVersion,
                    timestamp(now),
                    actorId,
                    resourceId,
                    result,
                    correlationId,
                    objectMapper.writeValueAsString(payload),
                    timestamp(now));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize allowlisted audit metadata", exception);
        }
    }

    static boolean csrfMatches(String expected, String candidate) {
        return candidate != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomToken() {
        byte[] value = new byte[32];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Instant minimum(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    enum LoginStatus {
        SUCCESS,
        INVALID,
        RATE_LIMITED
    }

    record LoginResult(LoginStatus status, String sessionToken, SessionPrincipal principal) {
        static LoginResult success(String sessionToken, SessionPrincipal principal) {
            return new LoginResult(LoginStatus.SUCCESS, sessionToken, principal);
        }

        static LoginResult invalid() {
            return new LoginResult(LoginStatus.INVALID, null, null);
        }

        static LoginResult rateLimited() {
            return new LoginResult(LoginStatus.RATE_LIMITED, null, null);
        }
    }

    private record UserRecord(UUID id, String username, String passwordHash, String status) {}

    private record SessionRecord(
            UUID id,
            UUID userId,
            String username,
            String csrfToken,
            Instant lastActivityAt,
            Instant absoluteExpiresAt) {}
}
