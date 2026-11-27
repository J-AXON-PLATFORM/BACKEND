package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditCatalogTest {

    private static AuditFact fact(String type, String eventVersion, String module, String actorType, UUID actorId,
            String resourceType, Long version, String result, Map<String, String> metadata) {
        return new AuditFact(UUID.randomUUID(), type, eventVersion, module, Instant.now(), actorType, actorId,
                resourceType, UUID.randomUUID(), version, result, UUID.randomUUID(), metadata);
    }

    private static AuditFact aiFact(
            String type, String module, String actorType, UUID actorId, String resourceType,
            Long version, String result, Map<String, String> metadata) {
        return fact(type, "1.0", module, actorType, actorId, resourceType, version, result, metadata);
    }

    private static Map<String, String> metadata(String previous) {
        return Map.of("previousStatus", previous, "newStatus", "DISABLED");
    }

    @Test
    void acceptsOnlyTheClosedAiSourceDisabledFact() {
        UUID actor = UUID.randomUUID();
        assertThat(AuditCatalog.accepts(aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L,
                "SUCCESS", metadata("READY")))).isTrue();
        assertThat(AuditCatalog.accepts(aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 1L,
                "SUCCESS", metadata("FAILED")))).isTrue();

        Map<String, String> extra = new HashMap<>(metadata("READY"));
        extra.put("name", "Operations handbook");
        for (AuditFact invalid : new AuditFact[] {
            aiFact("AI_SOURCE_INDEXED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            aiFact("AI_SOURCE_DISABLED", "IDENTITY", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            aiFact("AI_SOURCE_DISABLED", "AI", "SYSTEM", actor, "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", null, "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "HUMAN_USER", 3L, "SUCCESS", metadata("READY")),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 0L, "SUCCESS", metadata("READY")),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", null, "SUCCESS", metadata("READY")),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "DENIED", metadata("READY")),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", metadata("PENDING")),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", extra),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", Map.of()),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", null),
            aiFact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", UUID.fromString("34844482-1d29-1d30-87fd-1bb67a22e050"),
                    "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            null
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyTheClosedAiSourceLifecycleFacts() {
        UUID actor = UUID.randomUUID();
        List<AuditFact> valid = List.of(
                fact("AI_SOURCE_REGISTERED", "2.0", "AI", "HUMAN_USER", actor,
                        "AI_SOURCE", 0L, "SUCCESS", Map.of("status", "PENDING")),
                fact("AI_SOURCE_INDEXING_REQUESTED", "2.0", "AI", "HUMAN_USER", actor,
                        "AI_SOURCE", 1L, "SUCCESS", Map.of("status", "INDEXING")),
                fact("AI_SOURCE_INDEXED", "2.0", "AI", "SYSTEM", null,
                        "AI_SOURCE", 2L, "SUCCESS", Map.of("status", "READY")),
                fact("AI_SOURCE_INDEXING_FAILED", "2.0", "AI", "SYSTEM", null,
                        "AI_SOURCE", 2L, "FAILED", Map.of("status", "FAILED")));
        assertThat(valid).allSatisfy(value -> assertThat(AuditCatalog.accepts(value)).isTrue());

        for (AuditFact invalid : new AuditFact[] {
            fact("AI_SOURCE_REGISTERED", "1.0", "AI", "HUMAN_USER", actor,
                    "AI_SOURCE", 0L, "SUCCESS", Map.of("status", "PENDING")),
            fact("AI_SOURCE_REGISTERED", "2.0", "AI", "SYSTEM", null,
                    "AI_SOURCE", 0L, "SUCCESS", Map.of("status", "PENDING")),
            fact("AI_SOURCE_INDEXING_REQUESTED", "2.0", "AI", "HUMAN_USER", actor,
                    "AI_SOURCE", 0L, "SUCCESS", Map.of("status", "INDEXING")),
            fact("AI_SOURCE_INDEXED", "2.0", "AI", "SYSTEM", actor,
                    "AI_SOURCE", 2L, "SUCCESS", Map.of("status", "READY")),
            fact("AI_SOURCE_INDEXED", "2.0", "AI", "HUMAN_USER", actor,
                    "AI_SOURCE", 2L, "SUCCESS", Map.of("status", "READY")),
            fact("AI_SOURCE_INDEXING_FAILED", "2.0", "AI", "SYSTEM", null,
                    "AI_SOURCE", 2L, "SUCCESS", Map.of("status", "FAILED")),
            fact("AI_SOURCE_INDEXING_FAILED", "2.0", "AI", "SYSTEM", null,
                    "AI_SOURCE", 2L, "FAILED", Map.of("status", "FAILED", "error", "forbidden")),
            fact("AI_SOURCE_INDEXED", "2.0", "AI", "SYSTEM", null,
                    "AI_SOURCE", 2L, "SUCCESS", Map.of("status", "READY", "model", "forbidden"))
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyCanonicalHumanUserCreatedFacts() {
        UUID actor = UUID.randomUUID();
        assertThat(AuditCatalog.accepts(fact(
                        "HUMAN_USER_CREATED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                        "HUMAN_USER", null, "SUCCESS",
                        Map.of("status", "ACTIVE", "roles", "AUDITOR"))))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "HUMAN_USER_CREATED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                        "HUMAN_USER", null, "SUCCESS",
                        Map.of("status", "ACTIVE",
                                "roles", "AUDITOR|END_USER|IT_OPERATOR|SUPPORT_TECHNICIAN|SYSTEM_ADMIN"))))
                .isTrue();

        for (Map<String, String> metadata : List.of(
            Map.of("status", "ACTIVE", "roles", "END_USER|AUDITOR"),
            Map.of("status", "ACTIVE", "roles", "AUDITOR|AUDITOR"),
            Map.of("status", "ACTIVE", "roles", "SUPERUSER"),
            Map.of("status", "ACTIVE", "roles", "AUDITOR,END_USER"),
            Map.of("status", "DISABLED", "roles", "AUDITOR"),
            Map.of("status", "ACTIVE", "roles", ""),
            Map.of("status", "ACTIVE", "roles", "AUDITOR", "extra", "x")
        )) {
            assertThat(AuditCatalog.accepts(fact(
                            "HUMAN_USER_CREATED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                            "HUMAN_USER", null, "SUCCESS", metadata)))
                    .as(metadata.toString())
                    .isFalse();
        }
        assertThat(AuditCatalog.accepts(fact(
                        "HUMAN_USER_CREATED", "1.0", "IDENTITY", "HUMAN_USER", actor,
                        "HUMAN_USER", null, "SUCCESS",
                        Map.of("status", "ACTIVE", "roles", "AUDITOR"))))
                .isFalse();
        assertThat(AuditCatalog.accepts(fact(
                        "HUMAN_USER_CREATED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                        "HUMAN_USER", 1L, "SUCCESS",
                        Map.of("status", "ACTIVE", "roles", "AUDITOR"))))
                .isFalse();
    }

    @Test
    void acceptsOnlyClosedSuccessfulHumanAuthenticationFacts() {
        UUID actor = UUID.randomUUID();
        assertThat(AuditCatalog.accepts(fact(
                        "HUMAN_LOGIN_SUCCEEDED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                        "AUTHENTICATION_ATTEMPT", null, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "HUMAN_LOGOUT_SUCCEEDED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                        "HUMAN_SESSION", null, "SUCCESS", Map.of())))
                .isTrue();

        for (AuditFact invalid : new AuditFact[] {
            fact("HUMAN_LOGIN_SUCCEEDED", "1.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "SUCCESS", Map.of()),
            fact("HUMAN_LOGIN_FAILED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "DENIED", Map.of()),
            fact("HUMAN_LOGIN_SUCCEEDED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "HUMAN_SESSION", null, "SUCCESS", Map.of()),
            fact("HUMAN_LOGOUT_SUCCEEDED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "SUCCESS", Map.of()),
            fact("HUMAN_LOGOUT_SUCCEEDED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "HUMAN_SESSION", 1L, "SUCCESS", Map.of()),
            fact("HUMAN_LOGIN_SUCCEEDED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "SUCCESS", Map.of("username", "forbidden"))
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyTheClosedDeniedLoginFactWithOptionalVerifiedActor() {
        UUID actor = UUID.randomUUID();
        Map<String, String> reason = Map.of("reason", "INVALID_CREDENTIALS");
        assertThat(AuditCatalog.accepts(fact(
                        "HUMAN_LOGIN_FAILED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                        "AUTHENTICATION_ATTEMPT", null, "DENIED", reason)))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "HUMAN_LOGIN_FAILED", "2.0", "IDENTITY", "HUMAN_USER", null,
                        "AUTHENTICATION_ATTEMPT", null, "DENIED", reason)))
                .isTrue();

        for (AuditFact invalid : new AuditFact[] {
            fact("HUMAN_LOGIN_FAILED", "1.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "DENIED", reason),
            fact("HUMAN_LOGIN_FAILED", "2.0", "IDENTITY", "SYSTEM", null,
                    "AUTHENTICATION_ATTEMPT", null, "DENIED", reason),
            fact("HUMAN_LOGIN_FAILED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "SUCCESS", reason),
            fact("HUMAN_LOGIN_FAILED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "DENIED", Map.of()),
            fact("HUMAN_LOGIN_FAILED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "DENIED", Map.of("reason", "RATE_LIMITED")),
            fact("HUMAN_LOGIN_FAILED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "AUTHENTICATION_ATTEMPT", null, "DENIED",
                    Map.of("reason", "INVALID_CREDENTIALS", "username", "forbidden"))
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyClosedTechnicalIdentityPublicKeyFacts() {
        UUID actor = UUID.randomUUID();
        assertThat(AuditCatalog.accepts(fact(
                        "AGENT_PUBLIC_KEY_REGISTERED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                        "AGENT_TECHNICAL_IDENTITY", null, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                        "DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY", null, "SUCCESS", Map.of())))
                .isTrue();

        for (AuditFact invalid : new AuditFact[] {
            fact("AGENT_PUBLIC_KEY_REGISTERED", "1.0", "IDENTITY", "HUMAN_USER", actor,
                    "AGENT_TECHNICAL_IDENTITY", null, "SUCCESS", Map.of()),
            fact("AGENT_PUBLIC_KEY_REGISTERED", "2.0", "IDENTITY", "HUMAN_USER", null,
                    "AGENT_TECHNICAL_IDENTITY", null, "SUCCESS", Map.of()),
            fact("AGENT_PUBLIC_KEY_REGISTERED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY", null, "SUCCESS", Map.of()),
            fact("DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED", "2.0", "IDENTITY", "HUMAN_USER", actor,
                    "DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY", null, "SUCCESS",
                    Map.of("fingerprint", "forbidden"))
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyClosedInventoryAndCollectorFacts() {
        UUID actor = UUID.randomUUID();
        assertThat(AuditCatalog.accepts(fact(
                        "ASSET_CREATED", "2.0", "INVENTORY", "HUMAN_USER", actor,
                        "ASSET", 0L, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "AGENT_PRE_REGISTERED", "2.0", "COLLECTOR", "HUMAN_USER", actor,
                        "AGENT", 0L, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "AGENT_ACTIVATED", "2.0", "COLLECTOR", "HUMAN_USER", actor,
                        "AGENT", 1L, "SUCCESS", Map.of())))
                .isTrue();

        for (AuditFact invalid : new AuditFact[] {
            fact("ASSET_CREATED", "1.0", "INVENTORY", "HUMAN_USER", actor,
                    "ASSET", 0L, "SUCCESS", Map.of()),
            fact("ASSET_CREATED", "2.0", "COLLECTOR", "HUMAN_USER", actor,
                    "ASSET", 0L, "SUCCESS", Map.of()),
            fact("ASSET_CREATED", "2.0", "INVENTORY", "HUMAN_USER", null,
                    "ASSET", 0L, "SUCCESS", Map.of()),
            fact("ASSET_CREATED", "2.0", "INVENTORY", "HUMAN_USER", actor,
                    "ASSET", 1L, "SUCCESS", Map.of()),
            fact("AGENT_PRE_REGISTERED", "2.0", "COLLECTOR", "HUMAN_USER", actor,
                    "AGENT", 1L, "SUCCESS", Map.of()),
            fact("AGENT_ACTIVATED", "2.0", "COLLECTOR", "HUMAN_USER", actor,
                    "AGENT", 0L, "SUCCESS", Map.of()),
            fact("AGENT_ACTIVATED", "2.0", "COLLECTOR", "HUMAN_USER", actor,
                    "AGENT", 1L, "SUCCESS", Map.of("status", "ACTIVE"))
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyClosedDiscoveryLifecycleFacts() {
        UUID actor = UUID.randomUUID();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_SCOPE_CREATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                        "AUTHORIZED_SCOPE", 0L, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_SCOPE_STATUS_CHANGED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                        "AUTHORIZED_SCOPE", 2L, "SUCCESS",
                        Map.of("previousStatus", "DISABLED", "newStatus", "ACTIVE"))))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_EXECUTOR_PRE_REGISTERED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                        "DISCOVERY_EXECUTOR", 0L, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_EXECUTOR_ACTIVATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                        "DISCOVERY_EXECUTOR", 1L, "SUCCESS", Map.of())))
                .isTrue();

        for (AuditFact invalid : new AuditFact[] {
            fact("DISCOVERY_SCOPE_CREATED", "1.0", "DISCOVERY", "HUMAN_USER", actor,
                    "AUTHORIZED_SCOPE", 0L, "SUCCESS", Map.of()),
            fact("DISCOVERY_SCOPE_CREATED", "2.0", "DISCOVERY", "HUMAN_USER", null,
                    "AUTHORIZED_SCOPE", 0L, "SUCCESS", Map.of()),
            fact("DISCOVERY_SCOPE_CREATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "AUTHORIZED_SCOPE", 1L, "SUCCESS", Map.of()),
            fact("DISCOVERY_SCOPE_STATUS_CHANGED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "AUTHORIZED_SCOPE", 0L, "SUCCESS",
                    Map.of("previousStatus", "ACTIVE", "newStatus", "DISABLED")),
            fact("DISCOVERY_SCOPE_STATUS_CHANGED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "AUTHORIZED_SCOPE", 1L, "SUCCESS",
                    Map.of("previousStatus", "ACTIVE", "newStatus", "ACTIVE")),
            fact("DISCOVERY_SCOPE_STATUS_CHANGED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "AUTHORIZED_SCOPE", 1L, "SUCCESS",
                    Map.of("previousStatus", "ACTIVE", "newStatus", "DISABLED", "name", "forbidden")),
            fact("DISCOVERY_EXECUTOR_PRE_REGISTERED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERY_EXECUTOR", 1L, "SUCCESS", Map.of()),
            fact("DISCOVERY_EXECUTOR_ACTIVATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERY_EXECUTOR", 0L, "SUCCESS", Map.of()),
            fact("DISCOVERY_EXECUTOR_ACTIVATED", "2.0", "COLLECTOR", "HUMAN_USER", actor,
                    "DISCOVERY_EXECUTOR", 1L, "SUCCESS", Map.of())
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyClosedDiscoveryJobLifecycleFacts() {
        UUID human = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_JOB_CREATED", "2.0", "DISCOVERY", "HUMAN_USER", human,
                        "DISCOVERY_JOB", 0L, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_JOB_DISPATCHED", "2.0", "DISCOVERY", "HUMAN_USER", human,
                        "DISCOVERY_JOB", 1L, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_JOB_COMPLETED", "2.0", "DISCOVERY", "DISCOVERY_EXECUTOR", executor,
                        "DISCOVERY_JOB", 2L, "SUCCESS", Map.of())))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERY_JOB_COMPLETED", "2.0", "DISCOVERY", "DISCOVERY_EXECUTOR", executor,
                        "DISCOVERY_JOB", 2L, "FAILED", Map.of())))
                .isTrue();

        for (AuditFact invalid : new AuditFact[] {
            fact("DISCOVERY_JOB_CREATED", "1.0", "DISCOVERY", "HUMAN_USER", human,
                    "DISCOVERY_JOB", 0L, "SUCCESS", Map.of()),
            fact("DISCOVERY_JOB_CREATED", "2.0", "DISCOVERY", "HUMAN_USER", human,
                    "DISCOVERY_JOB", 1L, "SUCCESS", Map.of()),
            fact("DISCOVERY_JOB_DISPATCHED", "2.0", "DISCOVERY", "HUMAN_USER", null,
                    "DISCOVERY_JOB", 1L, "SUCCESS", Map.of()),
            fact("DISCOVERY_JOB_DISPATCHED", "2.0", "DISCOVERY", "DISCOVERY_EXECUTOR", executor,
                    "DISCOVERY_JOB", 1L, "SUCCESS", Map.of()),
            fact("DISCOVERY_JOB_COMPLETED", "2.0", "DISCOVERY", "HUMAN_USER", human,
                    "DISCOVERY_JOB", 2L, "SUCCESS", Map.of()),
            fact("DISCOVERY_JOB_COMPLETED", "2.0", "DISCOVERY", "DISCOVERY_EXECUTOR", executor,
                    "DISCOVERY_JOB", 1L, "SUCCESS", Map.of()),
            fact("DISCOVERY_JOB_COMPLETED", "2.0", "DISCOVERY", "DISCOVERY_EXECUTOR", executor,
                    "DISCOVERY_JOB", 2L, "DENIED", Map.of()),
            fact("DISCOVERY_JOB_COMPLETED", "2.0", "DISCOVERY", "DISCOVERY_EXECUTOR", executor,
                    "DISCOVERY_JOB", 2L, "FAILED", Map.of("status", "FAILED"))
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyClosedDiscoveredDeviceReviewFacts() {
        UUID actor = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Map<String, String> ignored =
                Map.of("previousStatus", "UNREVIEWED", "newStatus", "IGNORED");
        Map<String, String> incorporated = Map.of(
                "previousStatus", "UNREVIEWED",
                "newStatus", "INCORPORATED",
                "assetId", assetId.toString());
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERED_DEVICE_IGNORED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                        "DISCOVERED_DEVICE", 1L, "SUCCESS", ignored)))
                .isTrue();
        assertThat(AuditCatalog.accepts(fact(
                        "DISCOVERED_DEVICE_INCORPORATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                        "DISCOVERED_DEVICE", 1L, "SUCCESS", incorporated)))
                .isTrue();

        for (AuditFact invalid : new AuditFact[] {
            fact("DISCOVERED_DEVICE_IGNORED", "1.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERED_DEVICE", 1L, "SUCCESS", ignored),
            fact("DISCOVERED_DEVICE_IGNORED", "2.0", "DISCOVERY", "HUMAN_USER", null,
                    "DISCOVERED_DEVICE", 1L, "SUCCESS", ignored),
            fact("DISCOVERED_DEVICE_IGNORED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERED_DEVICE", 0L, "SUCCESS", ignored),
            fact("DISCOVERED_DEVICE_IGNORED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERED_DEVICE", 1L, "SUCCESS",
                    Map.of("previousStatus", "UNREVIEWED", "newStatus", "INCORPORATED")),
            fact("DISCOVERED_DEVICE_IGNORED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERED_DEVICE", 1L, "SUCCESS",
                    Map.of("previousStatus", "UNREVIEWED", "newStatus", "IGNORED", "extra", "x")),
            fact("DISCOVERED_DEVICE_INCORPORATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERED_DEVICE", 1L, "SUCCESS",
                    Map.of("previousStatus", "UNREVIEWED", "newStatus", "INCORPORATED")),
            fact("DISCOVERED_DEVICE_INCORPORATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERED_DEVICE", 1L, "SUCCESS",
                    Map.of("previousStatus", "IGNORED", "newStatus", "INCORPORATED",
                            "assetId", assetId.toString())),
            fact("DISCOVERED_DEVICE_INCORPORATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERED_DEVICE", 1L, "SUCCESS",
                    Map.of("previousStatus", "UNREVIEWED", "newStatus", "INCORPORATED",
                            "assetId", assetId.toString().toUpperCase(java.util.Locale.ROOT))),
            fact("DISCOVERED_DEVICE_INCORPORATED", "2.0", "DISCOVERY", "HUMAN_USER", actor,
                    "DISCOVERED_DEVICE", 1L, "SUCCESS",
                    Map.of("previousStatus", "UNREVIEWED", "newStatus", "INCORPORATED",
                            "assetId", "34844482-1d29-1d30-87fd-1bb67a22e050"))
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    @Test
    void acceptsOnlyClosedSupportTicketFacts() {
        UUID actor = UUID.randomUUID();
        UUID comment = UUID.randomUUID();
        UUID lifecycle = UUID.randomUUID();
        List<AuditFact> valid = List.of(
                fact("TICKET_CREATED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 0L,
                        "SUCCESS", Map.of(
                                "type", "INCIDENT", "status", "OPEN",
                                "priority", "NORMAL", "source", "WEB")),
                fact("TICKET_COMMENT_ADDED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 0L,
                        "SUCCESS", Map.of("commentId", comment.toString(), "visibility", "PUBLIC")),
                fact("TICKET_ASSIGNED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 1L,
                        "SUCCESS", Map.of("assigneeUserId", actor.toString())),
                fact("TICKET_PRIORITY_CHANGED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 2L,
                        "SUCCESS", Map.of("previousPriority", "NORMAL", "newPriority", "URGENT")),
                fact("TICKET_STATUS_CHANGED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 3L,
                        "SUCCESS", Map.of("previousStatus", "OPEN", "newStatus", "IN_PROGRESS")),
                fact("TICKET_RESOLVED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 4L,
                        "SUCCESS", Map.of(
                                "lifecycleEventId", lifecycle.toString(),
                                "fromStatus", "IN_PROGRESS",
                                "toStatus", "RESOLVED")),
                fact("TICKET_CLOSED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 5L,
                        "SUCCESS", Map.of(
                                "lifecycleEventId", lifecycle.toString(),
                                "fromStatus", "RESOLVED",
                                "toStatus", "CLOSED",
                                "closeReason", "RESOLVED")),
                fact("TICKET_REOPENED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 6L,
                        "SUCCESS", Map.of(
                                "lifecycleEventId", lifecycle.toString(),
                                "fromStatus", "CLOSED",
                                "toStatus", "OPEN")));
        assertThat(valid).allSatisfy(value -> assertThat(AuditCatalog.accepts(value)).isTrue());

        for (AuditFact invalid : new AuditFact[] {
            fact("TICKET_CREATED", "1.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 0L,
                    "SUCCESS", valid.getFirst().metadata()),
            fact("TICKET_CREATED", "2.0", "DISCOVERY", "HUMAN_USER", actor, "TICKET", 0L,
                    "SUCCESS", valid.getFirst().metadata()),
            fact("TICKET_CREATED", "2.0", "SUPPORT", "HUMAN_USER", null, "TICKET", 0L,
                    "SUCCESS", valid.getFirst().metadata()),
            fact("TICKET_COMMENT_ADDED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 0L,
                    "SUCCESS", Map.of(
                            "commentId", comment.toString().toUpperCase(java.util.Locale.ROOT),
                            "visibility", "PUBLIC")),
            fact("TICKET_ASSIGNED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 1L,
                    "SUCCESS", Map.of("assigneeUserId", UUID.randomUUID().toString())),
            fact("TICKET_PRIORITY_CHANGED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 2L,
                    "SUCCESS", Map.of("previousPriority", "HIGH", "newPriority", "HIGH")),
            fact("TICKET_STATUS_CHANGED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 3L,
                    "SUCCESS", Map.of(
                            "previousStatus", "OPEN", "newStatus", "WAITING_FOR_REQUESTER")),
            fact("TICKET_CLOSED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 5L,
                    "SUCCESS", Map.of(
                            "lifecycleEventId", lifecycle.toString(),
                            "fromStatus", "RESOLVED",
                            "toStatus", "CLOSED",
                            "closeReason", "DUPLICATE")),
            fact("TICKET_REOPENED", "2.0", "SUPPORT", "HUMAN_USER", actor, "TICKET", 6L,
                    "SUCCESS", Map.of(
                            "lifecycleEventId", lifecycle.toString(),
                            "fromStatus", "CLOSED",
                            "toStatus", "OPEN",
                            "extra", "forbidden"))
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }
}
