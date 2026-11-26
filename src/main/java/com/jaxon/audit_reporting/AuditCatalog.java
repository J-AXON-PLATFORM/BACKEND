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
        return acceptsAiSourceDisabled(fact)
                || acceptsHumanUserCreated(fact)
                || acceptsHumanAuthenticationSucceeded(fact)
                || acceptsHumanLoginFailed(fact)
                || acceptsTechnicalIdentityPublicKeyRegistered(fact)
                || acceptsAssetCreated(fact)
                || acceptsAgentLifecycle(fact)
                || acceptsDiscoveryLifecycle(fact);
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

    private static boolean acceptsHumanAuthenticationSucceeded(AuditFact fact) {
        boolean login = "HUMAN_LOGIN_SUCCEEDED".equals(fact.eventType())
                && "AUTHENTICATION_ATTEMPT".equals(fact.resourceType());
        boolean logout = "HUMAN_LOGOUT_SUCCEEDED".equals(fact.eventType())
                && "HUMAN_SESSION".equals(fact.resourceType());
        return (login || logout)
                && "2.0".equals(fact.eventVersion())
                && "IDENTITY".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && fact.resourceVersion() == null
                && "SUCCESS".equals(fact.result())
                && fact.metadata().isEmpty();
    }

    private static boolean acceptsHumanLoginFailed(AuditFact fact) {
        return "HUMAN_LOGIN_FAILED".equals(fact.eventType())
                && "2.0".equals(fact.eventVersion())
                && "IDENTITY".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && (fact.actorId() == null || uuidV4(fact.actorId()))
                && "AUTHENTICATION_ATTEMPT".equals(fact.resourceType())
                && fact.resourceVersion() == null
                && "DENIED".equals(fact.result())
                && fact.metadata().equals(Map.of("reason", "INVALID_CREDENTIALS"));
    }

    private static boolean acceptsTechnicalIdentityPublicKeyRegistered(AuditFact fact) {
        boolean agent = "AGENT_PUBLIC_KEY_REGISTERED".equals(fact.eventType())
                && "AGENT_TECHNICAL_IDENTITY".equals(fact.resourceType());
        boolean executor = "DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED".equals(fact.eventType())
                && "DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY".equals(fact.resourceType());
        return (agent || executor)
                && "2.0".equals(fact.eventVersion())
                && "IDENTITY".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && fact.resourceVersion() == null
                && "SUCCESS".equals(fact.result())
                && fact.metadata().isEmpty();
    }

    private static boolean acceptsAssetCreated(AuditFact fact) {
        return "ASSET_CREATED".equals(fact.eventType())
                && "2.0".equals(fact.eventVersion())
                && "INVENTORY".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && "ASSET".equals(fact.resourceType())
                && Long.valueOf(0).equals(fact.resourceVersion())
                && "SUCCESS".equals(fact.result())
                && fact.metadata().isEmpty();
    }

    private static boolean acceptsAgentLifecycle(AuditFact fact) {
        boolean preRegistered = "AGENT_PRE_REGISTERED".equals(fact.eventType())
                && Long.valueOf(0).equals(fact.resourceVersion());
        boolean activated = "AGENT_ACTIVATED".equals(fact.eventType())
                && Long.valueOf(1).equals(fact.resourceVersion());
        return (preRegistered || activated)
                && "2.0".equals(fact.eventVersion())
                && "COLLECTOR".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && "AGENT".equals(fact.resourceType())
                && "SUCCESS".equals(fact.result())
                && fact.metadata().isEmpty();
    }

    private static boolean acceptsDiscoveryLifecycle(AuditFact fact) {
        boolean scopeCreated = "DISCOVERY_SCOPE_CREATED".equals(fact.eventType())
                && "AUTHORIZED_SCOPE".equals(fact.resourceType())
                && Long.valueOf(0).equals(fact.resourceVersion())
                && fact.metadata().isEmpty();
        boolean scopeStatusChanged = "DISCOVERY_SCOPE_STATUS_CHANGED".equals(fact.eventType())
                && "AUTHORIZED_SCOPE".equals(fact.resourceType())
                && fact.resourceVersion() != null
                && fact.resourceVersion() >= 1
                && statusChangeMetadata(fact.metadata());
        boolean executorPreRegistered = "DISCOVERY_EXECUTOR_PRE_REGISTERED".equals(fact.eventType())
                && "DISCOVERY_EXECUTOR".equals(fact.resourceType())
                && Long.valueOf(0).equals(fact.resourceVersion())
                && fact.metadata().isEmpty();
        boolean executorActivated = "DISCOVERY_EXECUTOR_ACTIVATED".equals(fact.eventType())
                && "DISCOVERY_EXECUTOR".equals(fact.resourceType())
                && Long.valueOf(1).equals(fact.resourceVersion())
                && fact.metadata().isEmpty();
        return (scopeCreated || scopeStatusChanged || executorPreRegistered || executorActivated)
                && "2.0".equals(fact.eventVersion())
                && "DISCOVERY".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && "SUCCESS".equals(fact.result());
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

    private static boolean statusChangeMetadata(Map<String, String> metadata) {
        String previous = metadata.get("previousStatus");
        String next = metadata.get("newStatus");
        return metadata.size() == 2
                && Set.of("ACTIVE", "DISABLED").contains(previous)
                && Set.of("ACTIVE", "DISABLED").contains(next)
                && !previous.equals(next);
    }

    private static boolean uuidV4(java.util.UUID value) {
        return value != null && UUID_V4.matcher(value.toString()).matches();
    }
}
