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
}
