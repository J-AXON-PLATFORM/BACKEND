package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DiscoveryJobContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DiscoveryJobContract contract = new DiscoveryJobContract();

    @Test
    void serializesTheExactClosedDiscoveryJobContract() throws Exception {
        UUID jobId = UUID.fromString("ea9dc882-57ce-4b40-a552-969760f33196");
        byte[] document = contract.serialize(new DiscoveryJobMessage(
                "1.0",
                "DISCOVERY_JOB",
                jobId,
                UUID.fromString("33482f80-4772-45a5-adc9-72c96bf46185"),
                UUID.fromString("b37ab92c-7b53-4ef5-aa75-74aec4215c70"),
                "192.0.2.0/24",
                "BASIC",
                Instant.parse("2026-10-25T12:00:00Z"),
                Instant.parse("2026-10-25T13:00:00Z")));

        assertThat(json.readTree(document).properties()).hasSize(9);
        assertThat(json.readTree(document).path("jobId").asText()).isEqualTo(jobId.toString());
        assertThat(json.readTree(document)).isEqualTo(json.readTree(Files.readString(
                Path.of("doc/contracts/discovery/1.0/examples/valid/job-basic.json"))));
    }

    @Test
    void rejectsUnsupportedOrNonIncreasingMessages() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> contract.serialize(new DiscoveryJobMessage(
                        "2.0", "DISCOVERY_JOB", id, UUID.randomUUID(), UUID.randomUUID(),
                        "192.0.2.0/24", "BASIC", Instant.EPOCH, Instant.EPOCH.plusSeconds(1))))
                .isInstanceOf(InvalidDiscoveryJobMessageException.class);
        assertThatThrownBy(() -> contract.serialize(new DiscoveryJobMessage(
                        "1.0", "DISCOVERY_JOB", id, UUID.randomUUID(), UUID.randomUUID(),
                        "192.0.2.0/24", "BASIC", Instant.EPOCH, Instant.EPOCH)))
                .isInstanceOf(InvalidDiscoveryJobMessageException.class);
        assertThatThrownBy(() -> contract.serialize(new DiscoveryJobMessage(
                        "1.0", "DISCOVERY_JOB", id, UUID.randomUUID(), UUID.randomUUID(),
                        "192.0.2.0/24", "BASIC", Instant.EPOCH, Instant.EPOCH.plusSeconds(30))))
                .isInstanceOf(InvalidDiscoveryJobMessageException.class);
    }
}
