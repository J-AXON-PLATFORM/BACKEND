package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotContractValidatorTest {

    private final SnapshotContractValidator validator = new SnapshotContractValidator();

    @Test
    void validatesTheCanonicalSnapshotOffline() throws Exception {
        String json = Files.readString(Path.of("doc/contracts/agent/1.0/examples/valid/snapshot.json"));

        SnapshotMessage snapshot = validator.validate(json);

        assertThat(snapshot.messageId()).isEqualTo(UUID.fromString("6f69bb91-ef36-4c9f-a025-9a4c8ee96b43"));
        assertThat(snapshot.storageVolumes()).singleElement().extracting("name").isEqualTo("/");
    }

    @Test
    void rejectsDuplicatePropertiesTrailingJsonAndSchemaViolations() {
        String duplicate = valid(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                .replace("\"contractVersion\":\"1.0\"", "\"contractVersion\":\"1.0\",\"contractVersion\":\"1.0\"");
        assertThatThrownBy(() -> validator.validate(duplicate))
                .isInstanceOf(SnapshotContractValidator.InvalidSnapshotException.class)
                .extracting("reasonCode")
                .isEqualTo("INVALID_JSON");

        String valid = valid(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> validator.validate(valid + "{}"))
                .isInstanceOf(SnapshotContractValidator.InvalidSnapshotException.class)
                .extracting("reasonCode")
                .isEqualTo("INVALID_JSON");

        assertThatThrownBy(() -> validator.validate(valid.replace("AGENT_SNAPSHOT", "AGENT_TASK")))
                .isInstanceOf(SnapshotContractValidator.InvalidSnapshotException.class)
                .extracting("reasonCode")
                .isEqualTo("SCHEMA_INVALID");

        assertThatThrownBy(() -> validator.validate(valid.replace(
                        "\"logicalCpuCount\":4", "\"logicalCpuCount\":4294967297")))
                .isInstanceOf(SnapshotContractValidator.InvalidSnapshotException.class)
                .extracting("reasonCode")
                .isEqualTo("SCHEMA_INVALID");
        assertThatThrownBy(() -> validator.validate(valid.replace(
                        "\"memoryBytes\":8192", "\"memoryBytes\":9223372036854775808")))
                .isInstanceOf(SnapshotContractValidator.InvalidSnapshotException.class)
                .extracting("reasonCode")
                .isEqualTo("SCHEMA_INVALID");
    }

    static String valid(UUID agentId, UUID messageId, UUID snapshotId) {
        return """
                {"contractVersion":"1.0","messageType":"AGENT_SNAPSHOT","messageId":"%s",\
                "agentId":"%s","createdAt":"2026-10-16T12:00:01Z","snapshotId":"%s",\
                "capturedAt":"2026-10-16T12:00:00Z","payload":{"hostname":"host.example",\
                "operatingSystem":{"family":"LINUX","name":"Linux","version":"1"},\
                "architecture":"X86_64","logicalCpuCount":4,"memoryBytes":8192,\
                "storageVolumes":[{"name":"/","totalBytes":1000,"availableBytes":400}]}}
                """.formatted(messageId, agentId, snapshotId).replace("\n", "");
    }
}
