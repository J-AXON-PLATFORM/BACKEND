package com.jaxon.discovery;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
class DiscoveryResultContract {

    static final int MAX_RESULT_BYTES = 5 * 1024 * 1024;
    private static final String SCHEMA_BASE = "https://schemas.j-axon.invalid/discovery/1.0/";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<Integer> PORTS = Set.of(22, 53, 80, 443, 445, 3389);

    private final Schema resultSchema;

    DiscoveryResultContract() {
        Map<String, String> schemas = new HashMap<>();
        for (String name : new String[] {"common.schema.json", "result.schema.json"}) {
            schemas.put(SCHEMA_BASE + name, resource(name));
        }
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(schemas).schemaRegistryConfig(SchemaRegistryConfig.builder()
                        .formatAssertionsEnabled(true)
                        .failFast(false)
                        .build()));
        resultSchema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + "result.schema.json"));
    }

    DiscoveryResultMessage validate(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RESULT_BYTES || !validateSchema(bytes)) {
            throw new InvalidDiscoveryResultException("RESULT_SCHEMA_INVALID");
        }
        try {
            JsonNode root = JSON.readTree(bytes);
            JsonNode summary = root.get("summary");
            List<DiscoveryResultMessage.Observation> observations = new ArrayList<>();
            for (JsonNode observation : root.get("observations")) {
                List<String> evidence = new ArrayList<>();
                observation.get("evidence").forEach(value -> evidence.add(value.textValue()));
                List<Integer> ports = new ArrayList<>();
                observation.get("openTcpPorts").forEach(value -> ports.add(value.intValue()));
                observations.add(new DiscoveryResultMessage.Observation(
                        observation.get("ipAddress").textValue(),
                        nullableText(observation.get("macAddress")),
                        nullableText(observation.get("hostname")),
                        List.copyOf(evidence),
                        List.copyOf(ports)));
            }
            DiscoveryResultMessage message = new DiscoveryResultMessage(
                    root.get("schemaVersion").textValue(),
                    uuid(root, "resultId"),
                    uuid(root, "jobId"),
                    uuid(root, "executorId"),
                    uuid(root, "scopeId"),
                    root.get("profile").textValue(),
                    instant(root, "startedAt"),
                    instant(root, "completedAt"),
                    root.get("status").textValue(),
                    nullableText(root.get("failureCode")),
                    new DiscoveryResultMessage.Summary(
                            summary.get("targetsConsidered").bigIntegerValue(),
                            summary.get("targetsScanned").intValue(),
                            summary.get("observationsFound").intValue()),
                    List.copyOf(observations));
            validateSemantics(message);
            return message;
        } catch (InvalidDiscoveryResultException exception) {
            throw exception;
        } catch (DateTimeParseException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidDiscoveryResultException("RESULT_SCHEMA_INVALID");
        }
    }

    private static void validateSemantics(DiscoveryResultMessage message) {
        DiscoveryResultMessage.Summary summary = message.summary();
        if (message.completedAt().isBefore(message.startedAt())
                || summary.observationsFound() != message.observations().size()
                || BigInteger.valueOf(summary.targetsScanned()).compareTo(summary.targetsConsidered()) > 0
                || summary.observationsFound() > summary.targetsScanned()
                || ("SUCCEEDED".equals(message.status())
                        && BigInteger.valueOf(summary.targetsScanned()).compareTo(summary.targetsConsidered()) != 0)) {
            throw new InvalidDiscoveryResultException("RESULT_SEMANTICS_INVALID");
        }
        Set<String> addresses = new HashSet<>();
        for (DiscoveryResultMessage.Observation observation : message.observations()) {
            if (!addresses.add(observation.ipAddress())
                    || !PORTS.containsAll(observation.openTcpPorts())
                    || !observation.openTcpPorts().equals(observation.openTcpPorts().stream().sorted().toList())
                    || (!observation.openTcpPorts().isEmpty()) != observation.evidence().contains("TCP")) {
                throw new InvalidDiscoveryResultException("RESULT_SEMANTICS_INVALID");
            }
        }
    }

    private boolean validateSchema(byte[] bytes) {
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return resultSchema.validate(
                            json,
                            InputFormat.JSON,
                            context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                    .isEmpty();
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private static UUID uuid(JsonNode root, String field) {
        UUID value = UUID.fromString(root.get(field).textValue());
        if (value.version() != 4 || value.variant() != 2) throw new IllegalArgumentException();
        return value;
    }

    private static Instant instant(JsonNode root, String field) {
        String value = root.get(field).textValue();
        if (!value.endsWith("Z")) throw new DateTimeParseException("Timestamp is not UTC", value, value.length());
        return Instant.parse(value);
    }

    private static String nullableText(JsonNode value) {
        return value == null || value.isNull() ? null : value.textValue();
    }

    private static String resource(String name) {
        String path = "/contracts/discovery/1.0/" + name;
        try (InputStream input = DiscoveryResultContract.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing packaged Discovery contract: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load packaged Discovery contract", exception);
        }
    }
}

final class InvalidDiscoveryResultException extends RuntimeException {
    private final String reasonCode;

    InvalidDiscoveryResultException(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    String reasonCode() {
        return reasonCode;
    }
}
