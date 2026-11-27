package com.jaxon.audit_reporting;

import com.jaxon.AuditFact;

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
                || acceptsReportExported(fact)
                || acceptsAiSourceLifecycle(fact)
                || acceptsHumanUserCreated(fact)
                || acceptsHumanAuthenticationSucceeded(fact)
                || acceptsHumanLoginFailed(fact)
                || acceptsTechnicalIdentityPublicKeyRegistered(fact)
                || acceptsAssetCreated(fact)
                || acceptsAgentLifecycle(fact)
                || acceptsDiscoveryLifecycle(fact)
                || acceptsDiscoveryJobLifecycle(fact)
                || acceptsDiscoveredDeviceReview(fact)
                || acceptsSupportTicket(fact);
    }

    private static boolean acceptsReportExported(AuditFact fact) {
        String rowCount = fact.metadata().get("rowCount");
        return "REPORT_EXPORTED".equals(fact.eventType())
                && "1.0".equals(fact.eventVersion())
                && "AUDIT_REPORTING".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && "REPORT_EXPORT".equals(fact.resourceType())
                && fact.sourceEventId().equals(fact.resourceId())
                && fact.resourceVersion() == null
                && "SUCCESS".equals(fact.result())
                && fact.metadata().size() == 3
                && "CSV".equals(fact.metadata().get("format"))
                && Set.of("ASSETS", "AGENTS", "TICKETS").contains(fact.metadata().get("reportType"))
                && rowCount != null
                && rowCount.matches("^(0|[1-9]\\d{0,4})$")
                && Integer.parseInt(rowCount) <= 10_000;
    }

    private static boolean acceptsAiSourceLifecycle(AuditFact fact) {
        boolean registered = "AI_SOURCE_REGISTERED".equals(fact.eventType())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && Long.valueOf(0).equals(fact.resourceVersion())
                && "SUCCESS".equals(fact.result())
                && fact.metadata().equals(Map.of("status", "PENDING"));
        boolean requested = "AI_SOURCE_INDEXING_REQUESTED".equals(fact.eventType())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && Long.valueOf(1).equals(fact.resourceVersion())
                && "SUCCESS".equals(fact.result())
                && fact.metadata().equals(Map.of("status", "INDEXING"));
        boolean indexed = "AI_SOURCE_INDEXED".equals(fact.eventType())
                && "SYSTEM".equals(fact.actorType())
                && fact.actorId() == null
                && Long.valueOf(2).equals(fact.resourceVersion())
                && "SUCCESS".equals(fact.result())
                && fact.metadata().equals(Map.of("status", "READY"));
        boolean failed = "AI_SOURCE_INDEXING_FAILED".equals(fact.eventType())
                && "SYSTEM".equals(fact.actorType())
                && fact.actorId() == null
                && Long.valueOf(2).equals(fact.resourceVersion())
                && "FAILED".equals(fact.result())
                && fact.metadata().equals(Map.of("status", "FAILED"));
        return (registered || requested || indexed || failed)
                && "2.0".equals(fact.eventVersion())
                && "AI".equals(fact.producerModule())
                && "AI_SOURCE".equals(fact.resourceType());
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

    private static boolean acceptsDiscoveryJobLifecycle(AuditFact fact) {
        boolean created = "DISCOVERY_JOB_CREATED".equals(fact.eventType())
                && "HUMAN_USER".equals(fact.actorType())
                && Long.valueOf(0).equals(fact.resourceVersion())
                && "SUCCESS".equals(fact.result());
        boolean dispatched = "DISCOVERY_JOB_DISPATCHED".equals(fact.eventType())
                && "HUMAN_USER".equals(fact.actorType())
                && Long.valueOf(1).equals(fact.resourceVersion())
                && "SUCCESS".equals(fact.result());
        boolean completed = "DISCOVERY_JOB_COMPLETED".equals(fact.eventType())
                && "DISCOVERY_EXECUTOR".equals(fact.actorType())
                && Long.valueOf(2).equals(fact.resourceVersion())
                && Set.of("SUCCESS", "FAILED").contains(fact.result());
        return (created || dispatched || completed)
                && "2.0".equals(fact.eventVersion())
                && "DISCOVERY".equals(fact.producerModule())
                && uuidV4(fact.actorId())
                && "DISCOVERY_JOB".equals(fact.resourceType())
                && fact.metadata().isEmpty();
    }

    private static boolean acceptsDiscoveredDeviceReview(AuditFact fact) {
        boolean ignored = "DISCOVERED_DEVICE_IGNORED".equals(fact.eventType())
                && fact.metadata().equals(Map.of(
                        "previousStatus", "UNREVIEWED",
                        "newStatus", "IGNORED"));
        boolean incorporated = "DISCOVERED_DEVICE_INCORPORATED".equals(fact.eventType())
                && fact.metadata().size() == 3
                && "UNREVIEWED".equals(fact.metadata().get("previousStatus"))
                && "INCORPORATED".equals(fact.metadata().get("newStatus"))
                && fact.metadata().get("assetId") != null
                && UUID_V4.matcher(fact.metadata().get("assetId")).matches();
        return (ignored || incorporated)
                && "2.0".equals(fact.eventVersion())
                && "DISCOVERY".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && "DISCOVERED_DEVICE".equals(fact.resourceType())
                && Long.valueOf(1).equals(fact.resourceVersion())
                && "SUCCESS".equals(fact.result());
    }

    private static boolean acceptsSupportTicket(AuditFact fact) {
        boolean created = "TICKET_CREATED".equals(fact.eventType())
                && Long.valueOf(0).equals(fact.resourceVersion())
                && fact.metadata().size() == 4
                && Set.of("INCIDENT", "SERVICE_REQUEST", "QUESTION", "OTHER")
                        .contains(fact.metadata().get("type"))
                && "OPEN".equals(fact.metadata().get("status"))
                && "NORMAL".equals(fact.metadata().get("priority"))
                && "WEB".equals(fact.metadata().get("source"));
        boolean commentAdded = "TICKET_COMMENT_ADDED".equals(fact.eventType())
                && nonNegativeVersion(fact)
                && fact.metadata().size() == 2
                && canonicalUuid(fact.metadata().get("commentId"))
                && Set.of("PUBLIC", "INTERNAL").contains(fact.metadata().get("visibility"));
        boolean assigned = "TICKET_ASSIGNED".equals(fact.eventType())
                && positiveVersion(fact)
                && uuidV4(fact.actorId())
                && fact.metadata().equals(Map.of("assigneeUserId", fact.actorId().toString()));
        boolean priorityChanged = "TICKET_PRIORITY_CHANGED".equals(fact.eventType())
                && positiveVersion(fact)
                && ticketPriorityMetadata(fact.metadata());
        boolean statusChanged = "TICKET_STATUS_CHANGED".equals(fact.eventType())
                && positiveVersion(fact)
                && operationalStatusMetadata(fact.metadata());
        boolean resolved = "TICKET_RESOLVED".equals(fact.eventType())
                && positiveVersion(fact)
                && lifecycleMetadata(fact.metadata(), 3)
                && Set.of("IN_PROGRESS", "WAITING_FOR_REQUESTER")
                        .contains(fact.metadata().get("fromStatus"))
                && "RESOLVED".equals(fact.metadata().get("toStatus"));
        boolean closed = "TICKET_CLOSED".equals(fact.eventType())
                && positiveVersion(fact)
                && lifecycleMetadata(fact.metadata(), 4)
                && "CLOSED".equals(fact.metadata().get("toStatus"))
                && closureMetadata(fact.metadata());
        boolean reopened = "TICKET_REOPENED".equals(fact.eventType())
                && positiveVersion(fact)
                && lifecycleMetadata(fact.metadata(), 3)
                && Set.of("RESOLVED", "CLOSED").contains(fact.metadata().get("fromStatus"))
                && "OPEN".equals(fact.metadata().get("toStatus"));
        return (created || commentAdded || assigned || priorityChanged
                        || statusChanged || resolved || closed || reopened)
                && "2.0".equals(fact.eventVersion())
                && "SUPPORT".equals(fact.producerModule())
                && "HUMAN_USER".equals(fact.actorType())
                && uuidV4(fact.actorId())
                && "TICKET".equals(fact.resourceType())
                && "SUCCESS".equals(fact.result());
    }

    private static boolean ticketPriorityMetadata(Map<String, String> metadata) {
        String previous = metadata.get("previousPriority");
        String next = metadata.get("newPriority");
        Set<String> priorities = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
        return metadata.size() == 2
                && priorities.contains(previous)
                && priorities.contains(next)
                && !previous.equals(next);
    }

    private static boolean operationalStatusMetadata(Map<String, String> metadata) {
        String previous = metadata.get("previousStatus");
        String next = metadata.get("newStatus");
        return metadata.size() == 2
                && (("OPEN".equals(previous) && "IN_PROGRESS".equals(next))
                        || ("IN_PROGRESS".equals(previous)
                                && "WAITING_FOR_REQUESTER".equals(next))
                        || ("WAITING_FOR_REQUESTER".equals(previous)
                                && "IN_PROGRESS".equals(next)));
    }

    private static boolean lifecycleMetadata(Map<String, String> metadata, int size) {
        return metadata.size() == size
                && canonicalUuid(metadata.get("lifecycleEventId"))
                && metadata.get("fromStatus") != null
                && metadata.get("toStatus") != null;
    }

    private static boolean closureMetadata(Map<String, String> metadata) {
        String previous = metadata.get("fromStatus");
        String reason = metadata.get("closeReason");
        return ("RESOLVED".equals(previous) && "RESOLVED".equals(reason))
                || (Set.of("OPEN", "IN_PROGRESS", "WAITING_FOR_REQUESTER").contains(previous)
                        && Set.of("CANCELLED", "DUPLICATE", "NOT_APPLICABLE").contains(reason));
    }

    private static boolean positiveVersion(AuditFact fact) {
        return fact.resourceVersion() != null && fact.resourceVersion() >= 1;
    }

    private static boolean nonNegativeVersion(AuditFact fact) {
        return fact.resourceVersion() != null && fact.resourceVersion() >= 0;
    }

    private static boolean canonicalUuid(String value) {
        return value != null && UUID_V4.matcher(value).matches();
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
