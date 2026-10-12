package com.jaxon.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AgentIdentityRegistry {

    void create(UUID agentId, Instant occurredAt);

    KeyState registerPublicKey(
            UUID agentId, String publicKey, UUID actorId, UUID correlationId, Instant occurredAt);

    Optional<KeyState> find(UUID agentId);

    record KeyState(
            boolean publicKeyRegistered,
            String publicKeyFingerprint,
            Instant updatedAt,
            long version) {}

    final class InvalidPublicKeyException extends RuntimeException {}

    final class PublicKeyConflictException extends RuntimeException {}
}
