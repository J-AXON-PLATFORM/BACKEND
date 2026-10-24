package com.jaxon.discovery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record DiscoveryExecutorResponse(
        UUID executorId,
        String name,
        List<UUID> scopeIds,
        String status,
        boolean publicKeyRegistered,
        String publicKeyFingerprint,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
