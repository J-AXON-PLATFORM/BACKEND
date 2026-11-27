package com.jaxon.collector;

import java.time.Instant;
import java.util.UUID;

public record AgentReportRow(
        UUID agentId,
        UUID assetId,
        String status,
        String operationalStatus,
        Instant lastSeenAt,
        Instant lastSnapshotAt,
        Instant createdAt,
        Instant updatedAt) {}
