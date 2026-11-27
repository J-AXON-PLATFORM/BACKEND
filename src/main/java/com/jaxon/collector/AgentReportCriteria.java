package com.jaxon.collector;

import java.time.Instant;
import java.util.UUID;

public record AgentReportCriteria(
        String status,
        String operationalStatus,
        UUID assetId,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size) {}
