package com.jaxon.discovery;

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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
class DiscoveryJobContract {

    static final int MAX_JOB_BYTES = 64 * 1024;
    private static final Duration JOB_TTL = Duration.ofHours(1);
    private static final String SCHEMA_BASE = "https://schemas.j-axon.invalid/discovery/1.0/";
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final Schema schema;

    DiscoveryJobContract() {
        Map<String, String> schemas = new HashMap<>();
        for (String name : new String[] {"common.schema.json", "job.schema.json"}) {
            schemas.put(SCHEMA_BASE + name, resource(name));
        }
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(schemas).schemaRegistryConfig(SchemaRegistryConfig.builder()
                        .formatAssertionsEnabled(true)
                        .failFast(false)
                        .build()));
        schema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + "job.schema.json"));
    }

    byte[] serialize(DiscoveryJobMessage message) {
        if (!message.expiresAt().equals(message.createdAt().plus(JOB_TTL))) {
            throw new InvalidDiscoveryJobMessageException();
        }
        try {
            Map<String, Object> document = new TreeMap<>();
            document.put("createdAt", message.createdAt().toString());
            document.put("executorId", message.executorId().toString());
            document.put("expiresAt", message.expiresAt().toString());
            document.put("jobId", message.jobId().toString());
            document.put("messageType", message.messageType());
            document.put("profile", message.profile());
            document.put("schemaVersion", message.schemaVersion());
            document.put("scopeCidr", message.scopeCidr());
            document.put("scopeId", message.scopeId().toString());
            byte[] bytes = JSON.writeValueAsBytes(document);
            if (bytes.length > MAX_JOB_BYTES || !validate(bytes)) {
                throw new InvalidDiscoveryJobMessageException();
            }
            return bytes;
        } catch (InvalidDiscoveryJobMessageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidDiscoveryJobMessageException();
        }
    }

    private boolean validate(byte[] bytes) {
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

    private static String resource(String name) {
        try (InputStream input = DiscoveryJobContract.class.getResourceAsStream(
                "/contracts/discovery/1.0/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged Discovery contract: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load packaged Discovery contract", exception);
        }
    }
}

final class InvalidDiscoveryJobMessageException extends RuntimeException {}
