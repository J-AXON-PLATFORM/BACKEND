package com.jaxon.support;

import java.time.Instant;
import java.util.UUID;

record TicketListItemResponse(
        UUID ticketId,
        String type,
        String summary,
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
