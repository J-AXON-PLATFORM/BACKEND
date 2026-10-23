package com.jaxon.support;

import java.time.Instant;
import java.util.UUID;

record TicketLifecycleEntryResponse(
        UUID lifecycleEventId,
        String eventType,
        String fromStatus,
        String toStatus,
        String resolutionSummary,
        String closeReason,
        String reopenReason,
        UUID actorUserId,
        Instant occurredAt,
        long resourceVersion) {}
