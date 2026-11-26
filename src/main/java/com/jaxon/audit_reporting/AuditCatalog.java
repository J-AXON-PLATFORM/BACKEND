package com.jaxon.audit_reporting;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class AuditCatalog {

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final Set<String> DISABLE_PREVIOUS = Set.of("READY", "FAILED");
    private static final Pattern CANONICAL_ROLES = Pattern.compile(
            "^(?:AUDITOR(?:\\|END_USER)?(?:\\|IT_OPERATOR)?(?:\\|SUPPORT_TECHNICIAN)?(?:\\|SYSTEM_ADMIN)?"
                    + "|END_USER(?:\\|IT_OPERATOR)?(?:\\|SUPPORT_TECHNICIAN)?(?:\\|SYSTEM_ADMIN)?"
                    + "|IT_OPERATOR(?:\\|SUPPORT_TECHNICIAN)?(?:\\|SYSTEM_ADMIN)?"
                    + "|SUPPORT_TECHNICIAN(?:\\|SYSTEM_ADMIN)?|SYSTEM_ADMIN)$");

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
        return acceptsAiSourceDisabled(fact) || acceptsHumanUserCreated(fact);
    }

    private static boolean acceptsAiSourceDisabled(AuditFact fact) {
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

    private static boolean acceptsHumanUserCreated(AuditFact fact) {
        return "HUMAN_USER_CREATED".equals(fact.eventType())
                && "2.0".equals(fact.eventVersion())
                && "IDENTITY".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && "HUMAN_USER".equals(fact.resourceType())
                && fact.resourceVersion() == null
                && "SUCCESS".equals(fact.result())
                && humanUserMetadata(fact.metadata());
    }

    private static boolean disableMetadata(Map<String, String> metadata) {
        return metadata.size() == 2
                && DISABLE_PREVIOUS.contains(metadata.get("previousStatus"))
                && "DISABLED".equals(metadata.get("newStatus"));
    }

    private static boolean humanUserMetadata(Map<String, String> metadata) {
        return metadata.size() == 2
                && "ACTIVE".equals(metadata.get("status"))
                && metadata.get("roles") != null
                && CANONICAL_ROLES.matcher(metadata.get("roles")).matches();
    }

    private static boolean uuidV4(java.util.UUID value) {
        return value != null && UUID_V4.matcher(value.toString()).matches();
    }
}
