package com.jaxon.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class AgentIdentityService implements AgentIdentityRegistry {

    private static final String ALGORITHM = "ssh-ed25519";

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
        ParsedPublicKey parsed = parse(publicKey);
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
                WHERE public_key_fingerprint = ? AND agent_id <> ?
                """,
                Integer.class,
                parsed.fingerprint(),
                agentId);
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

    private IdentityRow rowForUpdate(UUID agentId) {
        return jdbc.query(
                        """
                        SELECT public_key, public_key_fingerprint, updated_at, version
                        FROM identity.technical_identities
                        WHERE agent_id = ?
                        FOR UPDATE
                        """,
                        (resultSet, row) -> new IdentityRow(
                                resultSet.getString("public_key"),
                                resultSet.getString("public_key_fingerprint"),
                                resultSet.getTimestamp("updated_at").toInstant(),
                                resultSet.getLong("version")),
                        agentId)
                .stream()
                .findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    private static ParsedPublicKey parse(String value) {
        String[] parts = value == null ? new String[0] : value.trim().split("\\s+", 3);
        if (parts.length < 2 || !ALGORITHM.equals(parts[0])) {
            throw new InvalidPublicKeyException();
        }
        byte[] blob;
        try {
            blob = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPublicKeyException();
        }
        ByteBuffer buffer = ByteBuffer.wrap(blob);
        byte[] algorithm = readField(buffer);
        byte[] key = readField(buffer);
        if (buffer.hasRemaining()
                || !Arrays.equals(algorithm, ALGORITHM.getBytes(StandardCharsets.US_ASCII))
                || key.length != 32) {
            throw new InvalidPublicKeyException();
        }
        String canonicalBlob = Base64.getEncoder().encodeToString(blob);
        String fingerprint = "SHA256:"
                + Base64.getEncoder().withoutPadding().encodeToString(sha256(blob));
        return new ParsedPublicKey(ALGORITHM + " " + canonicalBlob, fingerprint);
    }

    private static byte[] readField(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES) {
            throw new InvalidPublicKeyException();
        }
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new InvalidPublicKeyException();
        }
        byte[] value = new byte[length];
        buffer.get(value);
        return value;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record ParsedPublicKey(String normalized, String fingerprint) {}

    private record IdentityRow(String publicKey, String fingerprint, Instant updatedAt, long version) {
        KeyState toState() {
            return new KeyState(publicKey != null, fingerprint, updatedAt, version);
        }
    }
}
