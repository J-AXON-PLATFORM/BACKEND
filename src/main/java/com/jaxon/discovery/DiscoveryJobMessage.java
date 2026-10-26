package com.jaxon.discovery;

import java.time.Instant;
import java.util.UUID;

record DiscoveryJobMessage(
        String schemaVersion,
        String messageType,
        UUID jobId,
        UUID executorId,
        UUID scopeId,
        String scopeCidr,
        String profile,
        Instant createdAt,
        Instant expiresAt) {}
