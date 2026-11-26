package com.jaxon.audit_reporting;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Allowlisted audit fact delivered by a producer module after its own transactional outbox
 * captured the event. {@code sourceEventId} is the producer's stable event identifier and the
 * idempotency key of the materialized audit event.
 */
public record AuditFact(
        UUID sourceEventId,
        String eventType,
        String eventVersion,
        String producerModule,
        Instant occurredAt,
        String actorType,
        UUID actorId,
        String resourceType,
        UUID resourceId,
        Long resourceVersion,
        String result,
        UUID correlationId,
        Map<String, String> metadata) {

    public AuditFact {
        metadata = metadata == null ? null : Map.copyOf(metadata);
    }
}
