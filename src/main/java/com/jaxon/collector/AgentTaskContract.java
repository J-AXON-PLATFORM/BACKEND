package com.jaxon.collector;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
class AgentTaskContract {

    static final int MAX_TASK_BYTES = 64 * 1024;
    static final int MAX_RESULT_BYTES = 5 * 1024 * 1024;
    private static final String SCHEMA_BASE = "https://schemas.j-axon.invalid/agent/1.0/";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final Schema taskSchema;
    private final Schema resultSchema;

    AgentTaskContract() {
        Map<String, String> schemas = new HashMap<>();
        for (String name : new String[] {"common.schema.json", "task.schema.json", "result.schema.json"}) {
            schemas.put(SCHEMA_BASE + name, resource(name));
        }
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(schemas).schemaRegistryConfig(SchemaRegistryConfig.builder()
                        .formatAssertionsEnabled(true)
                        .failFast(false)
                        .build()));
        taskSchema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + "task.schema.json"));
        resultSchema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + "result.schema.json"));
    }

    byte[] serialize(AgentTaskMessage message) {
        try {
            Map<String, Object> document = new TreeMap<>();
            document.put("agentId", message.agentId().toString());
            document.put("contractVersion", message.contractVersion());
            document.put("createdAt", message.createdAt().toString());
            document.put("expiresAt", message.expiresAt().toString());
            document.put("messageId", message.messageId().toString());
            document.put("messageType", message.messageType());
            document.put("payload", Map.of());
            document.put("taskId", message.taskId().toString());
            document.put("taskType", message.taskType());
            byte[] bytes = JSON.writeValueAsBytes(document);
            if (bytes.length > MAX_TASK_BYTES || !validate(taskSchema, bytes)) {
                throw new InvalidAgentMessageException("TASK_SCHEMA_INVALID");
            }
            return bytes;
        } catch (InvalidAgentMessageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidAgentMessageException("TASK_SCHEMA_INVALID");
        }
    }

    AgentResultMessage validateResult(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RESULT_BYTES || !validate(resultSchema, bytes)) {
            throw new InvalidAgentMessageException("RESULT_SCHEMA_INVALID");
        }
        try {
            JsonNode root = JSON.readTree(bytes);
            JsonNode error = root.get("error");
            return new AgentResultMessage(
                    root.get("contractVersion").textValue(),
                    uuid(root, "messageId"),
                    uuid(root, "agentId"),
                    instant(root, "createdAt"),
                    uuid(root, "resultId"),
                    uuid(root, "taskId"),
                    root.get("taskType").textValue(),
                    root.get("status").textValue(),
                    instant(root, "completedAt"),
                    error == null
                            ? null
                            : new AgentResultMessage.ResultError(
                                    error.get("code").textValue(), error.get("message").textValue()));
        } catch (DateTimeParseException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidAgentMessageException("RESULT_SCHEMA_INVALID");
        }
    }

    private static boolean validate(Schema schema, byte[] bytes) {
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
            return schema.validate(
                            json,
                            InputFormat.JSON,
                            context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                    .isEmpty();
        } catch (java.nio.charset.CharacterCodingException exception) {
            return false;
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

    private static String resource(String name) {
        String path = "/contracts/agent/1.0/" + name;
        try (InputStream input = AgentTaskContract.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged Agent contract: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load packaged Agent contract", exception);
        }
    }

    static final class InvalidAgentMessageException extends RuntimeException {
        private final String reasonCode;

        InvalidAgentMessageException(String reasonCode) {
            this.reasonCode = reasonCode;
        }

        String reasonCode() {
            return reasonCode;
        }
    }
}
