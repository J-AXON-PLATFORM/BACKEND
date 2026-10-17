package com.jaxon.collector;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
class SnapshotContractValidator {

    private static final String SCHEMA_BASE = "https://schemas.j-axon.invalid/agent/1.0/";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final Schema schema;

    SnapshotContractValidator() {
        Map<String, String> schemas = new HashMap<>();
        schemas.put(SCHEMA_BASE + "common.schema.json", resource("common.schema.json"));
        schemas.put(SCHEMA_BASE + "snapshot.schema.json", resource("snapshot.schema.json"));
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(schemas).schemaRegistryConfig(SchemaRegistryConfig.builder()
                        .formatAssertionsEnabled(true)
                        .failFast(false)
                        .build()));
        schema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + "snapshot.schema.json"));
    }

    SnapshotMessage validate(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
            if (root == null) {
                throw new InvalidSnapshotException("INVALID_JSON");
            }
        } catch (RuntimeException exception) {
            throw new InvalidSnapshotException("INVALID_JSON");
        }
        if (!schema.validate(
                        json,
                        InputFormat.JSON,
                        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                .isEmpty()) {
            throw new InvalidSnapshotException("SCHEMA_INVALID");
        }
        try {
            JsonNode payload = root.get("payload");
            JsonNode os = payload.get("operatingSystem");
            return new SnapshotMessage(
                    root.get("contractVersion").textValue(),
                    uuid(root, "messageId"),
                    uuid(root, "agentId"),
                    instant(root, "createdAt"),
                    uuid(root, "snapshotId"),
                    instant(root, "capturedAt"),
                    payload.get("hostname").textValue(),
                    new SnapshotMessage.OperatingSystem(
                            os.get("family").textValue(),
                            os.get("name").textValue(),
                            os.get("version").textValue()),
                    payload.get("architecture").textValue(),
                    exactInt(payload.get("logicalCpuCount")),
                    exactLong(payload.get("memoryBytes")),
                    java.util.stream.StreamSupport.stream(payload.get("storageVolumes").spliterator(), false)
                            .map(volume -> new SnapshotMessage.StorageVolume(
                                    volume.get("name").textValue(),
                                    exactLong(volume.get("totalBytes")),
                                    exactLong(volume.get("availableBytes"))))
                            .toList());
        } catch (DateTimeParseException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidSnapshotException("SCHEMA_INVALID");
        }
    }

    private static UUID uuid(JsonNode root, String field) {
        UUID value = UUID.fromString(root.get(field).textValue());
        if (value.version() != 4 || value.variant() != 2) {
            throw new IllegalArgumentException("UUID is not version 4");
        }
        return value;
    }

    private static Instant instant(JsonNode root, String field) {
        String value = root.get(field).textValue();
        if (!value.endsWith("Z")) {
            throw new DateTimeParseException("Timestamp is not UTC", value, value.length());
        }
        return Instant.parse(value);
    }

    private static int exactInt(JsonNode value) {
        if (!value.canConvertToInt()) {
            throw new IllegalArgumentException("Integer is outside the supported range");
        }
        return value.intValue();
    }

    private static long exactLong(JsonNode value) {
        if (!value.canConvertToLong()) {
            throw new IllegalArgumentException("Integer is outside the supported range");
        }
        return value.longValue();
    }

    private static String resource(String name) {
        String path = "/contracts/agent/1.0/" + name;
        try (InputStream input = SnapshotContractValidator.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged Agent contract: " + name);
            }
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load packaged Agent contract", exception);
        }
    }

    static final class InvalidSnapshotException extends RuntimeException {
        private final String reasonCode;

        InvalidSnapshotException(String reasonCode) {
            this.reasonCode = reasonCode;
        }

        String reasonCode() {
            return reasonCode;
        }
    }
}
