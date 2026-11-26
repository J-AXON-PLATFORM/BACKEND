package com.jaxon.audit_reporting;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record AuditEventResponse(
        UUID auditEventId,
        UUID sourceEventId,
        String eventType,
        String eventVersion,
        String producerModule,
        Instant occurredAt,
        Instant recordedAt,
        String actorType,
        UUID actorId,
        String resourceType,
        UUID resourceId,
        Long resourceVersion,
        String result,
        UUID correlationId,
        Map<String, String> metadata) {}
