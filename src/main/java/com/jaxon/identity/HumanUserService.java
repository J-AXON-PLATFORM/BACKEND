package com.jaxon.identity;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class HumanUserService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SYSTEM_ADMIN", "IT_OPERATOR", "SUPPORT_TECHNICIAN", "END_USER", "AUDITOR");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    HumanUserService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    @Transactional
    HumanUserResponse create(HumanUserCreateRequest request, UUID actorId, UUID correlationId) {
        if (request == null || request.username() == null) {
            throw new UserValidationException();
        }
        String username = request.username().strip();
        String normalizedUsername = IdentityBootstrap.normalizeUsername(request.username());
        if (username.isEmpty() || username.length() > 128 || normalizedUsername.length() > 128) {
            throw new UserValidationException();
        }
        passwordPolicy.validate(request.password());
        List<String> roles = validateRoles(request.roles());

        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0)), 1",
                (resultSet, row) -> resultSet.getInt(2),
                "identity-user:" + normalizedUsername);
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM identity.human_users WHERE normalized_username = ?",
                Integer.class,
                normalizedUsername);
        if (existing != null && existing > 0) {
            throw new UsernameConflictException();
        }

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        try {
            jdbc.update(
                    """
                    INSERT INTO identity.human_users
                        (id, username, normalized_username, password_hash, status, created_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                    """,
                    userId,
                    username,
                    normalizedUsername,
                    passwordEncoder.encode(request.password()),
                    Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new UsernameConflictException();
        }
        jdbc.batchUpdate(
                "INSERT INTO identity.human_user_roles (user_id, role_code) VALUES (?, ?)",
                roles.stream().map(role -> new Object[] {userId, role}).toList());
        jdbc.update(
                """
                INSERT INTO identity.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, result,
                     correlation_id, payload, next_attempt_at)
                VALUES (?, 'HUMAN_USER_CREATED', '2.0', ?, 'IDENTITY',
                        'HUMAN_USER', ?, 'HUMAN_USER', ?, 'SUCCESS', ?,
                        jsonb_build_object('status', 'ACTIVE', 'roles', ?), ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                userId,
                correlationId,
                String.join("|", roles),
                Timestamp.from(now));
        return new HumanUserResponse(
                userId,
                username,
                "ACTIVE",
                roles.stream().map(HumanRole::valueOf).toList(),
                now);
    }

    private static List<String> validateRoles(List<String> roles) {
        if (roles == null
                || roles.isEmpty()
                || roles.size() > ALLOWED_ROLES.size()
                || !roles.stream().allMatch(ALLOWED_ROLES::contains)
                || new HashSet<>(roles).size() != roles.size()) {
            throw new UserValidationException();
        }
        return roles.stream().sorted(Comparator.naturalOrder()).toList();
    }

    static final class UserValidationException extends RuntimeException {}

    static final class UsernameConflictException extends RuntimeException {}
}
