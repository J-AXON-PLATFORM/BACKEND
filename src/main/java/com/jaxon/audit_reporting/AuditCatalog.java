package com.jaxon.audit_reporting;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class AuditCatalog {

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final Set<String> DISABLE_PREVIOUS = Set.of("READY", "FAILED");

    private AuditCatalog() {}

    static boolean accepts(AuditFact fact) {
        if (fact == null
                || !uuidV4(fact.sourceEventId())
                || fact.occurredAt() == null
                || !uuidV4(fact.resourceId())
                || !uuidV4(fact.correlationId())
                || fact.metadata() == null) {
            return false;
        }
        return "AI_SOURCE_DISABLED".equals(fact.eventType())
                && "1.0".equals(fact.eventVersion())
                && "AI".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && "AI_SOURCE".equals(fact.resourceType())
                && fact.resourceVersion() != null
                && fact.resourceVersion() >= 1
                && "SUCCESS".equals(fact.result())
                && disableMetadata(fact.metadata());
    }

    private static boolean disableMetadata(Map<String, String> metadata) {
        return metadata.size() == 2
                && DISABLE_PREVIOUS.contains(metadata.get("previousStatus"))
                && "DISABLED".equals(metadata.get("newStatus"));
    }

    private static boolean uuidV4(java.util.UUID value) {
        return value != null && UUID_V4.matcher(value.toString()).matches();
    }
}
