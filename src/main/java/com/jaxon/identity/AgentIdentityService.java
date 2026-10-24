package com.jaxon.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class AgentIdentityService implements AgentIdentityRegistry {

    private final JdbcTemplate jdbc;

    AgentIdentityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID agentId, Instant occurredAt) {
        jdbc.update(
                """
                INSERT INTO identity.technical_identities
                    (id, agent_id, identity_type, created_at, updated_at, version)
                VALUES (?, ?, 'AGENT', ?, ?, 0)
                """,
                UUID.randomUUID(),
                agentId,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt));
    }

    @Override
    public KeyState registerPublicKey(
            UUID agentId, String publicKey, UUID actorId, UUID correlationId, Instant occurredAt) {
        OpenSshEd25519PublicKey.Parsed parsed;
        try {
            parsed = OpenSshEd25519PublicKey.parse(publicKey);
        } catch (OpenSshEd25519PublicKey.InvalidKeyException exception) {
            throw new InvalidPublicKeyException();
        }
        IdentityRow current = rowForUpdate(agentId);
        if (current.publicKey() != null) {
            if (current.publicKey().equals(parsed.normalized())) {
                return current.toState();
            }
            throw new PublicKeyConflictException();
        }

        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                resultSet -> null,
                parsed.fingerprint());
        Integer duplicate = jdbc.queryForObject(
                """
                SELECT count(*) FROM identity.technical_identities
                WHERE public_key_fingerprint = ? AND id <> ?
                """,
                Integer.class,
                parsed.fingerprint(),
                current.identityId());
        if (duplicate != null && duplicate > 0) {
            throw new PublicKeyConflictException();
        }

        long version = current.version() + 1;
        jdbc.update(
                """
                UPDATE identity.technical_identities
                SET public_key = ?, public_key_fingerprint = ?, updated_at = ?, version = ?
                WHERE agent_id = ? AND version = ?
                """,
                parsed.normalized(),
                parsed.fingerprint(),
                Timestamp.from(occurredAt),
                version,
                agentId,
                current.version());
        jdbc.update(
                """
                INSERT INTO identity.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, result,
                     correlation_id, payload, next_attempt_at)
                VALUES (?, 'AGENT_PUBLIC_KEY_REGISTERED', '1.0', ?, 'IDENTITY',
                        'HUMAN_USER', ?, 'AGENT_TECHNICAL_IDENTITY', ?, 'SUCCESS',
                        ?, '{}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(occurredAt),
                actorId,
                agentId,
                correlationId,
                Timestamp.from(occurredAt));
        return new KeyState(true, parsed.fingerprint(), occurredAt, version);
    }

    @Override
    public Optional<KeyState> find(UUID agentId) {
        return jdbc.query(
                        """
                        SELECT public_key_fingerprint, updated_at, version
                        FROM identity.technical_identities
                        WHERE agent_id = ?
                        """,
                        (resultSet, row) -> new KeyState(
                                resultSet.getString("public_key_fingerprint") != null,
                                resultSet.getString("public_key_fingerprint"),
                                resultSet.getTimestamp("updated_at").toInstant(),
                                resultSet.getLong("version")),
                        agentId)
                .stream()
                .findFirst();
    }

    @Override
    public RegisteredPublicKey requireRegisteredPublicKey(UUID agentId) {
        return jdbc.query(
                        """
                        SELECT public_key, public_key_fingerprint
                        FROM identity.technical_identities
                        WHERE agent_id = ? AND public_key IS NOT NULL
                        """,
                        (resultSet, row) -> new RegisteredPublicKey(
                                resultSet.getString("public_key"),
                                resultSet.getString("public_key_fingerprint")),
                        agentId)
                .stream()
                .findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    private IdentityRow rowForUpdate(UUID agentId) {
        return jdbc.query(
                        """
                        SELECT id, public_key, public_key_fingerprint, updated_at, version
                        FROM identity.technical_identities
                        WHERE agent_id = ?
                        FOR UPDATE
                        """,
                        (resultSet, row) -> new IdentityRow(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("public_key"),
                                resultSet.getString("public_key_fingerprint"),
                                resultSet.getTimestamp("updated_at").toInstant(),
                                resultSet.getLong("version")),
                        agentId)
                .stream()
                .findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    private record IdentityRow(
            UUID identityId, String publicKey, String fingerprint, Instant updatedAt, long version) {
        KeyState toState() {
            return new KeyState(publicKey != null, fingerprint, updatedAt, version);
        }
    }
}
