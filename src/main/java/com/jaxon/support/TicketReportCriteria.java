package com.jaxon.support;

import java.time.Instant;
import java.util.UUID;

public record TicketReportCriteria(
        String type,
        String status,
        String priority,
        UUID requesterUserId,
        UUID assigneeUserId,
        UUID assetId,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size) {}
