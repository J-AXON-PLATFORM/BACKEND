package com.jaxon.identity;

import java.text.Normalizer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class IdentityBootstrap implements ApplicationRunner {

    private final boolean enabled;
    private final String username;
    private final String password;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    IdentityBootstrap(
            @Value("${jaxon.identity.bootstrap.enabled}") boolean enabled,
            @Value("${jaxon.identity.bootstrap.username}") String username,
            @Value("${jaxon.identity.bootstrap.password}") String password,
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        String displayUsername = username.strip();
        String normalizedUsername = normalizeUsername(username);
        if (displayUsername.isEmpty() || displayUsername.length() > 128) {
            throw new IllegalStateException("Bootstrap username must contain between 1 and 128 characters");
        }
        passwordPolicy.validate(password);

        transactionTemplate.executeWithoutResult(status -> {
            jdbc.queryForObject(
                    "SELECT pg_advisory_xact_lock(hashtextextended('identity-bootstrap', 0)), 1",
                    (resultSet, row) -> resultSet.getInt(2));
            Integer existingUsers = jdbc.queryForObject("SELECT count(*) FROM identity.human_users", Integer.class);
            if (existingUsers == null || existingUsers != 0) {
                throw new IllegalStateException("Identity bootstrap requires an empty human user store");
            }
            UUID userId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO identity.human_users
                        (id, username, normalized_username, password_hash, status, created_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                    """,
                    userId,
                    displayUsername,
                    normalizedUsername,
                    passwordEncoder.encode(password),
                    Timestamp.from(Instant.now(clock)));
            jdbc.batchUpdate(
                    "INSERT INTO identity.human_user_roles (user_id, role_code) VALUES (?, ?)",
                    java.util.List.of(
                            new Object[] {userId, "SYSTEM_ADMIN"},
                            new Object[] {userId, "IT_OPERATOR"}));
        });
    }

    static String normalizeUsername(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
