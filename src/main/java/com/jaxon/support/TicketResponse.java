package com.jaxon.support;

import java.time.Instant;
import java.util.UUID;

record TicketResponse(
        UUID ticketId,
        String type,
        String summary,
        String description,
        String status,
        String priority,
        String source,
        UUID requesterUserId,
        UUID createdByUserId,
        UUID assigneeUserId,
        UUID assetId,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
