package com.jaxon.audit_reporting;

import java.time.Instant;
import java.util.UUID;

record AuditEventQuery(
        Instant from,
        Instant to,
        String module,
        String eventType,
        UUID actorId,
        String resourceType,
        UUID resourceId,
        String result,
        UUID correlationId,
        int page,
        int size) {}
