package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditCatalogTest {

    private static AuditFact fact(String type, String module, String actorType, UUID actorId, String resourceType,
            Long version, String result, Map<String, String> metadata) {
        return new AuditFact(UUID.randomUUID(), type, "1.0", module, Instant.now(), actorType, actorId,
                resourceType, UUID.randomUUID(), version, result, UUID.randomUUID(), metadata);
    }

    private static Map<String, String> metadata(String previous) {
        return Map.of("previousStatus", previous, "newStatus", "DISABLED");
    }

    @Test
    void acceptsOnlyTheClosedAiSourceDisabledFact() {
        UUID actor = UUID.randomUUID();
        assertThat(AuditCatalog.accepts(fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L,
                "SUCCESS", metadata("READY")))).isTrue();
        assertThat(AuditCatalog.accepts(fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 1L,
                "SUCCESS", metadata("FAILED")))).isTrue();

        Map<String, String> extra = new HashMap<>(metadata("READY"));
        extra.put("name", "Operations handbook");
        for (AuditFact invalid : new AuditFact[] {
            fact("AI_SOURCE_INDEXED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            fact("AI_SOURCE_DISABLED", "IDENTITY", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            fact("AI_SOURCE_DISABLED", "AI", "SYSTEM", actor, "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", null, "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "HUMAN_USER", 3L, "SUCCESS", metadata("READY")),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 0L, "SUCCESS", metadata("READY")),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", null, "SUCCESS", metadata("READY")),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "DENIED", metadata("READY")),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", metadata("PENDING")),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", extra),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", Map.of()),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", actor, "AI_SOURCE", 3L, "SUCCESS", null),
            fact("AI_SOURCE_DISABLED", "AI", "HUMAN_USER", UUID.fromString("34844482-1d29-1d30-87fd-1bb67a22e050"),
                    "AI_SOURCE", 3L, "SUCCESS", metadata("READY")),
            null
        }) {
            assertThat(AuditCatalog.accepts(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }
}
