package com.jaxon;

import java.time.Instant;
import java.util.UUID;

/** Minimal allowlisted fact delivered by an owning producer to Automation. */
public record AutomationTrigger(
        UUID sourceEventId,
        String sourceEventType,
        String sourceEventVersion,
        Instant sourceOccurredAt,
        String producerModule,
        String resourceType,
        UUID resourceId,
        long resourceVersion,
        String actorType,
        UUID actorId,
        UUID correlationId,
        UUID recipientUserId,
        String previousStatus,
        String newStatus) {}
