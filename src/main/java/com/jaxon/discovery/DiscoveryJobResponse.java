package com.jaxon.discovery;

import java.time.Instant;
import java.util.UUID;

record DiscoveryJobResponse(
        UUID jobId,
        UUID executorId,
        UUID scopeId,
        String profile,
        DiscoveryJobStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant dispatchedAt,
        long version) {}
