package com.jaxon.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DiscoveryExecutorIdentityRegistry {

    void create(UUID executorId, Instant occurredAt);

    KeyState registerPublicKey(
            UUID executorId, String publicKey, UUID actorId, UUID correlationId, Instant occurredAt);

    Optional<KeyState> find(UUID executorId);

    RegisteredPublicKey requireRegisteredPublicKey(UUID executorId);

    record KeyState(
            boolean publicKeyRegistered,
            String publicKeyFingerprint,
            Instant updatedAt,
            long version) {}

    record RegisteredPublicKey(String publicKey, String fingerprint) {}

    final class InvalidPublicKeyException extends RuntimeException {}

    final class PublicKeyConflictException extends RuntimeException {}
}
