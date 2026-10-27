package com.jaxon.discovery;

import java.time.Instant;
import java.math.BigInteger;
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
        Instant startedAt,
        Instant completedAt,
        String failureCode,
        ResultSummary summary,
        long version) {}

record ResultSummary(
        BigInteger targetsConsidered,
        int targetsScanned,
        int observationsFound) {}
