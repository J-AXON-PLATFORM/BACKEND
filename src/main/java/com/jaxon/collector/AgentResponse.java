package com.jaxon.collector;

import java.time.Instant;
import java.util.UUID;

record AgentResponse(
        UUID agentId,
        UUID assetId,
        String status,
        boolean publicKeyRegistered,
        String publicKeyFingerprint,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
