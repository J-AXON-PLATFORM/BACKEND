package com.jaxon.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialects;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ExecutableContractsTest {

    private static final Path AGENT_ROOT = Path.of("doc/contracts/agent/1.0");
    private static final Path OPENAPI_V10_ROOT = Path.of("doc/contracts/openapi/1.0");
    private static final Path OPENAPI_V11_ROOT = Path.of("doc/contracts/openapi/1.1");
    private static final Path OPENAPI_V20_ROOT = Path.of("doc/contracts/openapi/2.0");
    private static final Path OPENAPI_V21_ROOT = Path.of("doc/contracts/openapi/2.1");
    private static final Path OPENAPI_V22_ROOT = Path.of("doc/contracts/openapi/2.2");
    private static final Path OPENAPI_V23_ROOT = Path.of("doc/contracts/openapi/2.3");
    private static final Path OPENAPI_V24_ROOT = Path.of("doc/contracts/openapi/2.4");
    private static final Path OPENAPI_V25_ROOT = Path.of("doc/contracts/openapi/2.5");
    private static final Path OPENAPI_V30_ROOT = Path.of("doc/contracts/openapi/3.0");
    private static final Path OPENAPI_V31_ROOT = Path.of("doc/contracts/openapi/3.1");
    private static final Path OPENAPI_V40_ROOT = Path.of("doc/contracts/openapi/4.0");
    private static final Path OPENAPI_V41_ROOT = Path.of("doc/contracts/openapi/4.1");
    private static final Path OPENAPI_V42_ROOT = Path.of("doc/contracts/openapi/4.2");
    private static final String SCHEMA_BASE = "https://schemas.j-axon.invalid/agent/1.0/";
    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final List<String> AGENT_SCHEMAS = List.of(
            "common.schema.json", "snapshot.schema.json", "task.schema.json", "result.schema.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void openApiIsResolvedAndLimitedToApprovedOperations() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);

        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V10_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).isEqualTo("3.1.2");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.0");
        assertThat(api.getServers()).singleElement().extracting("url").isEqualTo("/api/v1");
        assertThat(api.getPaths().keySet())
                .containsExactlyInAnyOrder(
                        "/auth/login",
                        "/auth/me",
                        "/assets",
                        "/assets/{assetId}",
                        "/assets/{assetId}/technical-state");

        List<Operation> operations = operations(api);
        assertThat(operations).hasSize(5);
        assertThat(operations.stream().map(Operation::getOperationId))
                .doesNotContainNull()
                .doesNotHaveDuplicates();

        operations.forEach(operation -> operation.getResponses().forEach((status, response) -> {
            if (Integer.parseInt(status) >= 400) {
                assertThat(response.get$ref() != null
                                || response.getContent().containsKey("application/problem+json"))
                        .as("error response %s for %s uses RFC 9457", status, operation.getOperationId())
                        .isTrue();
            }
        }));
    }

    @Test
    void openApiExamplesMatchTheirSchemas() throws IOException {
        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V10_ROOT.resolve("openapi.yaml").toFile());
        Map<String, String> examples = Map.of(
                "asset-create-request.json", "AssetCreateRequest",
                "asset-response.json", "AssetResponse",
                "technical-state-response.json", "TechnicalStateResponse",
                "technical-state-empty-response.json", "TechnicalStateResponse",
                "problem-validation.json", "Problem");

        examples.forEach((file, component) -> {
            ObjectNode wrapper = JSON.createObjectNode();
            wrapper.put("$schema", DRAFT_2020_12);
            try {
                String definitions = root.get("components")
                        .get("schemas")
                        .toString()
                        .replace("#/components/schemas/", "#/$defs/");
                wrapper.set("$defs", JSON.readTree(definitions));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot prepare OpenAPI schemas", exception);
            }
            wrapper.put("$ref", "#/$defs/" + component);
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(wrapper.toString(), InputFormat.JSON);
            List<com.networknt.schema.Error> errors = schema.validate(
                    read(OPENAPI_V10_ROOT.resolve("examples").resolve(file)),
                    InputFormat.JSON,
                    context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
            assertThat(errors).as("OpenAPI example %s", file).isEmpty();
        });
    }

    @Test
    void openApi11Preserves10AndAddsOnlyApprovedAgentOperations() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);

        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V11_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).isEqualTo("3.1.2");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.1");
        assertThat(api.getServers()).singleElement().extracting("url").isEqualTo("/api/v1");
        assertThat(api.getPaths().keySet())
                .containsExactlyInAnyOrder(
                        "/auth/login",
                        "/auth/me",
                        "/assets",
                        "/assets/{assetId}",
                        "/assets/{assetId}/technical-state",
                        "/agents",
                        "/agents/{agentId}",
                        "/agents/{agentId}/ssh-key");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder(
                        "login",
                        "getCurrentSession",
                        "createAsset",
                        "getAsset",
                        "getAssetTechnicalState",
                        "preRegisterAgent",
                        "getAgent",
                        "registerAgentSshKey");
    }

    @Test
    void openApi11AgentExamplesMatchTheirSchemas() throws IOException {
        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V11_ROOT.resolve("openapi.yaml").toFile());
        Map<String, String> examples = Map.of(
                "agent-create-request.json", "AgentCreateRequest",
                "agent-ssh-key-request.json", "AgentSshKeyRequest",
                "agent-response-pending.json", "AgentResponse",
                "agent-response-key-registered.json", "AgentResponse");

        examples.forEach((file, component) -> {
            ObjectNode wrapper = JSON.createObjectNode();
            wrapper.put("$schema", DRAFT_2020_12);
            try {
                String definitions = root.get("components")
                        .get("schemas")
                        .toString()
                        .replace("#/components/schemas/", "#/$defs/");
                wrapper.set("$defs", JSON.readTree(definitions));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot prepare OpenAPI schemas", exception);
            }
            wrapper.put("$ref", "#/$defs/" + component);
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(wrapper.toString(), InputFormat.JSON);
            List<com.networknt.schema.Error> errors = schema.validate(
                    read(OPENAPI_V11_ROOT.resolve("examples").resolve(file)),
                    InputFormat.JSON,
                    context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
            assertThat(errors).as("OpenAPI 1.1 example %s", file).isEmpty();
        });
    }

    @Test
    void openApi20AddsOnlyAgentActivation() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V20_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("2.0");
        assertThat(api.getPaths().keySet())
                .containsExactlyInAnyOrder(
                        "/auth/login",
                        "/auth/me",
                        "/assets",
                        "/assets/{assetId}",
                        "/assets/{assetId}/technical-state",
                        "/agents",
                        "/agents/{agentId}",
                        "/agents/{agentId}/ssh-key",
                        "/agents/{agentId}/activate");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .contains("activateAgent")
                .doesNotHaveDuplicates();
    }

    @Test
    void openApi20ActivationExamplesMatchTheirSchemas() throws IOException {
        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V20_ROOT.resolve("openapi.yaml").toFile());
        Map<String, String> examples = Map.of(
                "agent-response-active.json", "AgentResponse",
                "agent-activation-response.json", "AgentActivationResponse");

        examples.forEach((file, component) -> {
            ObjectNode wrapper = JSON.createObjectNode();
            wrapper.put("$schema", DRAFT_2020_12);
            try {
                String definitions = root.get("components")
                        .get("schemas")
                        .toString()
                        .replace("#/components/schemas/", "#/$defs/");
                wrapper.set("$defs", JSON.readTree(definitions));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot prepare OpenAPI schemas", exception);
            }
            wrapper.put("$ref", "#/$defs/" + component);
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(wrapper.toString(), InputFormat.JSON);
            List<com.networknt.schema.Error> errors = schema.validate(
                    read(OPENAPI_V20_ROOT.resolve("examples").resolve(file)),
                    InputFormat.JSON,
                    context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
            assertThat(errors).as("OpenAPI 2.0 example %s", file).isEmpty();
        });
    }

    @Test
    void openApi21Preserves20AndAddsOnlyTechnicalStateEvents() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V21_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("2.1");
        assertThat(api.getPaths().keySet())
                .containsExactlyInAnyOrder(
                        "/auth/login",
                        "/auth/me",
                        "/assets",
                        "/assets/{assetId}",
                        "/assets/{assetId}/technical-state",
                        "/assets/{assetId}/technical-state/events",
                        "/agents",
                        "/agents/{agentId}",
                        "/agents/{agentId}/ssh-key",
                        "/agents/{agentId}/activate");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .contains("subscribeAssetTechnicalStateEvents")
                .doesNotHaveDuplicates();
        JsonNode event = Yaml31.mapper()
                .readTree(OPENAPI_V21_ROOT.resolve("openapi.yaml").toFile())
                .path("components")
                .path("schemas")
                .path("TechnicalStateUpdatedEvent");
        assertThat(event.path("required").toString())
                .isEqualTo("[\"eventId\",\"eventType\",\"occurredAt\",\"assetId\",\"correlationId\"]");
        assertThat(event.path("additionalProperties").asBoolean()).isFalse();
        assertThat(Yaml31.mapper()
                        .readTree(OPENAPI_V21_ROOT.resolve("openapi.yaml").toFile())
                        .at("/paths/~1assets~1{assetId}~1technical-state~1events/get/responses/200/content/text~1event-stream/x-jaxon-sse-events/ASSET_TECHNICAL_STATE_UPDATED/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/TechnicalStateUpdatedEvent");
    }

    @Test
    void openApi22Preserves21AndExtendsOnlyTheAgentResponse() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V22_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("2.2");
        assertThat(api.getPaths().keySet())
                .containsExactlyInAnyOrder(
                        "/auth/login",
                        "/auth/me",
                        "/assets",
                        "/assets/{assetId}",
                        "/assets/{assetId}/technical-state",
                        "/assets/{assetId}/technical-state/events",
                        "/agents",
                        "/agents/{agentId}",
                        "/agents/{agentId}/ssh-key",
                        "/agents/{agentId}/activate");

        JsonNode schema = Yaml31.mapper()
                .readTree(OPENAPI_V22_ROOT.resolve("openapi.yaml").toFile())
                .path("components")
                .path("schemas")
                .path("AgentResponse");
        assertThat(textValues(schema.path("required")))
                .contains("operationalStatus", "lastSeenAt", "lastSnapshotAt");
        assertThat(textValues(schema.path("properties").path("operationalStatus").path("enum")))
                .containsExactly("UNKNOWN", "ONLINE", "OFFLINE");
    }

    @Test
    void openApi22AgentExamplesMatchTheirSchema() throws IOException {
        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V22_ROOT.resolve("openapi.yaml").toFile());
        List<String> examples = List.of(
                "agent-response-unknown.json",
                "agent-response-online.json",
                "agent-response-offline.json");

        examples.forEach(file -> {
            ObjectNode wrapper = JSON.createObjectNode();
            wrapper.put("$schema", DRAFT_2020_12);
            try {
                String definitions = root.get("components")
                        .get("schemas")
                        .toString()
                        .replace("#/components/schemas/", "#/$defs/");
                wrapper.set("$defs", JSON.readTree(definitions));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot prepare OpenAPI schemas", exception);
            }
            wrapper.put("$ref", "#/$defs/AgentResponse");
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(wrapper.toString(), InputFormat.JSON);
            List<com.networknt.schema.Error> errors = schema.validate(
                    read(OPENAPI_V22_ROOT.resolve("examples").resolve(file)),
                    InputFormat.JSON,
                    context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
            assertThat(errors).as("OpenAPI 2.2 example %s", file).isEmpty();
        });
    }

    @Test
    void openApi23Preserves22AndAddsOnlyAgentTaskOperations() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V23_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("2.3");
        assertThat(api.getPaths().keySet())
                .containsExactlyInAnyOrder(
                        "/auth/login",
                        "/auth/me",
                        "/assets",
                        "/assets/{assetId}",
                        "/assets/{assetId}/technical-state",
                        "/assets/{assetId}/technical-state/events",
                        "/agents",
                        "/agents/{agentId}",
                        "/agents/{agentId}/ssh-key",
                        "/agents/{agentId}/activate",
                        "/agents/{agentId}/tasks",
                        "/agents/{agentId}/tasks/{taskId}");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder("createAgentTask", "getAgentTask");

        JsonNode schemas = Yaml31.mapper()
                .readTree(OPENAPI_V23_ROOT.resolve("openapi.yaml").toFile())
                .path("components")
                .path("schemas");
        assertThat(schemas.path("AgentTaskCreateRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(schemas.path("AgentTaskCreateRequest")
                        .path("properties")
                        .path("taskType")
                        .path("const")
                        .asText())
                .isEqualTo("COLLECT_SNAPSHOT");
        assertThat(textValues(schemas.path("AgentTaskResponse").path("required")))
                .containsExactlyInAnyOrder(
                        "taskId",
                        "agentId",
                        "taskType",
                        "status",
                        "createdAt",
                        "expiresAt",
                        "dispatchedAt",
                        "completedAt",
                        "error");
    }

    @Test
    void openApi24Preserves23AndAddsOnlyMinimumTicketOperations() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V24_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("2.4");
        assertThat(api.getPaths().keySet())
                .containsExactlyInAnyOrder(
                        "/auth/login",
                        "/auth/me",
                        "/assets",
                        "/assets/{assetId}",
                        "/assets/{assetId}/technical-state",
                        "/assets/{assetId}/technical-state/events",
                        "/agents",
                        "/agents/{agentId}",
                        "/agents/{agentId}/ssh-key",
                        "/agents/{agentId}/activate",
                        "/agents/{agentId}/tasks",
                        "/agents/{agentId}/tasks/{taskId}",
                        "/tickets",
                        "/tickets/{ticketId}");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder("createTicket", "getOwnTicket");

        JsonNode schemas = Yaml31.mapper()
                .readTree(OPENAPI_V24_ROOT.resolve("openapi.yaml").toFile())
                .path("components")
                .path("schemas");
        assertThat(schemas.path("TicketCreateRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(textValues(schemas.path("TicketCreateRequest").path("required")))
                .containsExactlyInAnyOrder("type", "summary", "description");
        assertThat(textValues(schemas.path("TicketResponse").path("required")))
                .contains("requesterUserId", "createdByUserId", "assigneeUserId", "assetId");
        assertThat(schemas.path("TicketResponse").path("properties").path("status").path("const").asText())
                .isEqualTo("OPEN");
        assertThat(schemas.path("TicketResponse").path("properties").path("priority").path("const").asText())
                .isEqualTo("NORMAL");
    }

    @Test
    void openApi25Preserves24AndAddsOnlyOwnTicketListAndConversationOperations() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V25_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("2.5");
        assertThat(api.getPaths().keySet())
                .containsExactlyInAnyOrder(
                        "/auth/login",
                        "/auth/me",
                        "/assets",
                        "/assets/{assetId}",
                        "/assets/{assetId}/technical-state",
                        "/assets/{assetId}/technical-state/events",
                        "/agents",
                        "/agents/{agentId}",
                        "/agents/{agentId}/ssh-key",
                        "/agents/{agentId}/activate",
                        "/agents/{agentId}/tasks",
                        "/agents/{agentId}/tasks/{taskId}",
                        "/tickets",
                        "/tickets/{ticketId}",
                        "/tickets/{ticketId}/comments");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder(
                        "createTicket", "listOwnTickets", "listOwnTicketComments", "addOwnTicketComment");

        JsonNode schemas = Yaml31.mapper()
                .readTree(OPENAPI_V25_ROOT.resolve("openapi.yaml").toFile())
                .path("components")
                .path("schemas");
        assertThat(schemas.path("TicketPage").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("TicketCommentCreateRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(schemas.path("TicketCommentResponse").path("properties").path("visibility").path("const").asText())
                .isEqualTo("PUBLIC");
        assertThat(textValues(schemas.path("TicketCommentPage").path("required")))
                .containsExactlyInAnyOrder("items", "page", "size", "totalElements", "totalPages");
    }

    @Test
    void openApi30UsesAClosedSummaryForOwnTicketListItems() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V30_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).isEqualTo("3.1.2");
        assertThat(api.getInfo().getVersion()).isEqualTo("3.0");
        assertThat(api.getServers()).singleElement().extracting("url").isEqualTo("/api/v1");
        assertThat(api.getPaths().keySet()).containsExactlyInAnyOrderElementsOf(
                Yaml31.mapper()
                        .readTree(OPENAPI_V25_ROOT.resolve("openapi.yaml").toFile())
                        .path("paths")
                        .properties()
                        .stream()
                        .map(Map.Entry::getKey)
                        .toList());

        JsonNode schemas = Yaml31.mapper()
                .readTree(OPENAPI_V30_ROOT.resolve("openapi.yaml").toFile())
                .path("components")
                .path("schemas");
        JsonNode listItem = schemas.path("TicketListItem");
        assertThat(listItem.path("additionalProperties").asBoolean()).isFalse();
        assertThat(textValues(listItem.path("required")))
                .containsExactlyInAnyOrder(
                        "ticketId", "type", "summary", "status", "priority", "source",
                        "requesterUserId", "createdByUserId", "assigneeUserId", "assetId",
                        "createdAt", "updatedAt", "version")
                .doesNotContain("description");
        assertThat(listItem.path("properties").has("description")).isFalse();
        assertThat(schemas.at("/TicketPage/properties/items/items/$ref").asText())
                .isEqualTo("#/components/schemas/TicketListItem");
    }

    @Test
    void openApi31AddsOnlyLogoutAndAdministrativeUserCreation() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V31_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).isEqualTo("3.1.2");
        assertThat(api.getInfo().getVersion()).isEqualTo("3.1");
        assertThat(api.getServers()).singleElement().extracting("url").isEqualTo("/api/v1");
        assertThat(api.getPaths().keySet())
                .contains("/auth/logout", "/users")
                .containsAll(Yaml31.mapper()
                        .readTree(OPENAPI_V30_ROOT.resolve("openapi.yaml").toFile())
                        .path("paths")
                        .properties()
                        .stream()
                        .map(Map.Entry::getKey)
                        .toList());
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .contains("logout", "createHumanUser")
                .doesNotHaveDuplicates();
        JsonNode schemas = Yaml31.mapper()
                .readTree(OPENAPI_V31_ROOT.resolve("openapi.yaml").toFile())
                .path("components")
                .path("schemas");
        assertThat(schemas.path("HumanUserCreateRequest").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("HumanUserResponse").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.at("/HumanUserCreateRequest/properties/roles/minItems").asInt()).isEqualTo(1);
        assertThat(schemas.at("/HumanUserCreateRequest/properties/roles/uniqueItems").asBoolean()).isTrue();
    }

    @Test
    void openApi40AddsTheClosedTechnicianQueueAndAtomicClaimContract() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V40_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).isEqualTo("3.1.2");
        assertThat(api.getInfo().getVersion()).isEqualTo("4.0");
        assertThat(api.getPaths().keySet())
                .contains(
                        "/tickets/technician-queue",
                        "/tickets/technician-queue/{ticketId}",
                        "/tickets/technician-queue/{ticketId}/comments",
                        "/tickets/{ticketId}/claim")
                .containsAll(Yaml31.mapper()
                        .readTree(OPENAPI_V31_ROOT.resolve("openapi.yaml").toFile())
                        .path("paths")
                        .properties()
                        .stream()
                        .map(Map.Entry::getKey)
                        .toList());
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .contains(
                        "listTechnicianTicketQueue",
                        "getTechnicianQueueTicket",
                        "listTechnicianQueueTicketComments",
                        "claimTicket",
                        "getOwnTicket",
                        "listOwnTicketComments")
                .doesNotHaveDuplicates();

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V40_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(schemas.path("TicketClaimRequest").path("additionalProperties").asBoolean()).isFalse();
        assertThat(textValues(schemas.path("TicketClaimRequest").path("required")))
                .containsExactly("expectedVersion");
        assertThat(schemas.at("/TicketClaimRequest/properties/expectedVersion/minimum").asInt()).isZero();
        assertThat(schemas.path("TicketListItem").path("properties").has("description")).isFalse();
        assertThat(schemas.path("TicketResponse").path("properties").path("version").has("const")).isFalse();
        assertThat(schemas.path("TicketResponse").path("properties").path("assigneeUserId").path("oneOf"))
                .hasSize(2);
        assertThat(root.at("/paths/~1tickets~1{ticketId}~1claim/post/responses").has("409")).isTrue();
    }

    @Test
    void openApi41AddsOnlyAssignedTechnicianConversation() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V41_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).isEqualTo("3.1.2");
        assertThat(api.getInfo().getVersion()).isEqualTo("4.1");
        assertThat(api.getPaths().keySet())
                .contains("/tickets/technician-queue/{ticketId}/conversation")
                .containsAll(Yaml31.mapper()
                        .readTree(OPENAPI_V40_ROOT.resolve("openapi.yaml").toFile())
                        .path("paths")
                        .properties()
                        .stream()
                        .map(Map.Entry::getKey)
                        .toList());
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .contains(
                        "listAssignedTechnicianTicketConversation",
                        "addAssignedTechnicianTicketComment")
                .doesNotHaveDuplicates();

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V41_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(schemas.path("TechnicianTicketCommentCreateRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(textValues(schemas.path("TechnicianTicketCommentCreateRequest").path("required")))
                .containsExactlyInAnyOrder("visibility", "body");
        assertThat(textValues(schemas.at("/TechnicianTicketCommentResponse/properties/visibility/enum")))
                .containsExactly("PUBLIC", "INTERNAL");
        assertThat(schemas.path("TechnicianTicketCommentResponse").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(schemas.path("TechnicianTicketCommentPage").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(root.at("/paths/~1tickets~1technician-queue~1{ticketId}~1conversation/post/responses")
                        .has("409"))
                .isTrue();
    }

    @Test
    void openApi42AddsOnlyAssignedTechnicianPriorityAndStatusMutations() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V42_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).isEqualTo("3.1.2");
        assertThat(api.getInfo().getVersion()).isEqualTo("4.2");
        var previousPaths = Yaml31.mapper()
                .readTree(OPENAPI_V41_ROOT.resolve("openapi.yaml").toFile())
                .path("paths")
                .properties()
                .stream()
                .map(Map.Entry::getKey)
                .toList();
        assertThat(api.getPaths().keySet())
                .contains("/tickets/{ticketId}/priority", "/tickets/{ticketId}/status")
                .containsAll(previousPaths)
                .hasSize(previousPaths.size() + 2);
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .contains("updateAssignedTicketPriority", "updateAssignedTicketStatus")
                .doesNotHaveDuplicates();

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V42_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(textValues(schemas.at("/TicketPriorityUpdateRequest/required")))
                .containsExactlyInAnyOrder("priority", "expectedVersion");
        assertThat(textValues(schemas.at("/TicketStatusUpdateRequest/properties/status/enum")))
                .containsExactly("OPEN", "IN_PROGRESS", "WAITING_FOR_REQUESTER");
        assertThat(schemas.path("TicketPriorityUpdateRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(schemas.path("TicketStatusUpdateRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(root.at("/paths/~1tickets~1{ticketId}~1priority/put/responses").has("409")).isTrue();
        assertThat(root.at("/paths/~1tickets~1{ticketId}~1status/put/responses").has("409")).isTrue();
    }

    @Test
    void previousOpenApiContractsRemainByteIdentical() throws IOException {
        assertThat(sha256(Files.readAllBytes(OPENAPI_V10_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("bf3b1df5f28fb2e1c34f55b645312e7df896aa8a50fa8fbbec4b7e72d2068236");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V11_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("96dfa1d01c63339089685a2e6c3011e050d24727dad71863df244a631b3c1d3b");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V20_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("832037d2e554ca680b89776430d8a01a12a945215ba5cac0335c849e349732fe");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V21_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("9abd5f0b4f9200f30ae71b2edae1eb7fa32ef8a238a27d62325fc464176cc326");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V22_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("24ffc076b43dc61b8779c9b367e1189b0904544fcbec85bb8d3ca72bd91f7e05");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V23_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("ad03306e05b8bd28d4933a3d6b37e72088e5f490a79e2180f5ae4de9fe9a8b36");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V24_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("ef6a21e8a53a03cb7888b8cd49dfc65def31fd3d28ebcb8d6a1771c0490edbd6");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V25_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("bbf7bf28316ab6b81553f2aabdc2c3529c482e1d72c5a56f82b9411cf1090335");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V30_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("cd80013004c2304015d28102436d729d73e29fe922e8e178650b4c5f40cd0950");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V31_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("244a760407bfa5dc6c2fbd35f29a4dc1ae4c56cb43757924a2e3fd1687767c9e");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V40_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("41d47b69b517ee8d9384e609f7aba518e69f363c98ec8b12f92df7d0d07f44ab");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V41_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("805c2fc9f8015fde0db161da39c9c58f54aae2c9789a8937f3cb324a2836a748");
    }

    @Test
    void agentSchemasAreValidDraft202012AndResolveOffline() throws IOException {
        Map<String, String> schemas = agentSchemaData();
        Schema metaSchema = SchemaRegistry.withDialect(Dialects.getDraft202012())
                .getSchema(SchemaLocation.of(DRAFT_2020_12));

        for (String file : AGENT_SCHEMAS) {
            List<com.networknt.schema.Error> errors = metaSchema.validate(
                    schemas.get(SCHEMA_BASE + file), InputFormat.JSON);
            assertThat(errors).as("meta-schema validation for %s", file).isEmpty();
        }

        SchemaRegistry registry = agentRegistry(schemas);
        for (String file : List.of("snapshot.schema.json", "task.schema.json", "result.schema.json")) {
            assertThat(registry.getSchema(SchemaLocation.of(SCHEMA_BASE + file))).isNotNull();
        }
    }

    @Test
    void runtimeSnapshotSchemasAreByteIdenticalToCanonicalSources() throws IOException {
        for (String file : AGENT_SCHEMAS) {
            try (var packaged = getClass().getResourceAsStream("/contracts/agent/1.0/" + file)) {
                assertThat(packaged).as("packaged runtime schema %s", file).isNotNull();
                assertThat(packaged.readAllBytes()).isEqualTo(Files.readAllBytes(AGENT_ROOT.resolve(file)));
            }
        }
    }

    @Test
    void agentFixturesEnforceApprovedBoundaries() throws IOException {
        SchemaRegistry registry = agentRegistry(agentSchemaData());

        try (Stream<Path> files = Files.list(AGENT_ROOT.resolve("examples/valid"))) {
            files.sorted().forEach(path -> assertThat(validate(registry, schemaFor(path), path))
                    .as("valid fixture %s", path.getFileName())
                    .isEmpty());
        }
        try (Stream<Path> files = Files.list(AGENT_ROOT.resolve("examples/invalid"))) {
            files.sorted().forEach(path -> assertThat(validate(registry, schemaFor(path), path))
                    .as("invalid fixture %s", path.getFileName())
                    .isNotEmpty());
        }
    }

    @Test
    void agentSchemasRejectRemoteAndTraversalReferences() throws IOException {
        for (String file : AGENT_SCHEMAS) {
            JsonNode schema = JSON.readTree(AGENT_ROOT.resolve(file).toFile());
            assertThat(invalidReferences(schema)).as("safe references in %s", file).isEmpty();
        }

        assertThat(invalidReferences(JSON.readTree("{\"$ref\":\"https://example.com/schema.json\"}")))
                .containsExactly("https://example.com/schema.json");
        assertThat(invalidReferences(JSON.readTree("{\"$ref\":\"../secret.schema.json\"}")))
                .containsExactly("../secret.schema.json");
    }

    @Test
    void manifestIsCompleteDeterministicAndTamperEvident() throws IOException {
        JsonNode manifest = JSON.readTree(AGENT_ROOT.resolve("manifest.json").toFile());
        assertThat(verifyManifest(manifest)).isTrue();

        ObjectNode tampered = manifest.deepCopy();
        ((ObjectNode) tampered.get("files").get(0)).put("sha256", "0".repeat(64));
        assertThat(verifyManifest(tampered)).isFalse();
    }

    private static List<Operation> operations(OpenAPI api) {
        List<Operation> result = new ArrayList<>();
        api.getPaths().values().forEach(path -> {
            add(result, path.getGet());
            add(result, path.getPost());
            add(result, path.getPut());
            add(result, path.getPatch());
            add(result, path.getDelete());
        });
        return result;
    }

    private static void add(List<Operation> operations, Operation operation) {
        if (operation != null) {
            operations.add(operation);
        }
    }

    private static Map<String, String> agentSchemaData() throws IOException {
        Map<String, String> schemas = new HashMap<>();
        for (String file : AGENT_SCHEMAS) {
            schemas.put(SCHEMA_BASE + file, read(AGENT_ROOT.resolve(file)));
        }
        return schemas;
    }

    private static SchemaRegistry agentRegistry(Map<String, String> schemas) {
        return SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(schemas).schemaRegistryConfig(SchemaRegistryConfig.builder()
                        .formatAssertionsEnabled(true)
                        .failFast(false)
                        .build()));
    }

    private static List<com.networknt.schema.Error> validate(
            SchemaRegistry registry, String schemaFile, Path fixture) {
        Schema schema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + schemaFile));
        return schema.validate(
                read(fixture),
                InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
    }

    private static String schemaFor(Path fixture) {
        String name = fixture.getFileName().toString();
        if (name.equals("snapshot.json") || name.startsWith("snapshot-")) {
            return "snapshot.schema.json";
        }
        if (name.startsWith("task-")) {
            return "task.schema.json";
        }
        if (name.startsWith("result-")) {
            return "result.schema.json";
        }
        throw new IllegalArgumentException("Unknown fixture family: " + name);
    }

    private static List<String> invalidReferences(JsonNode node) {
        List<String> invalid = new ArrayList<>();
        node.findValuesAsText("$ref").forEach(reference -> {
            if (reference.contains("..")
                    || (!reference.startsWith("#") && !reference.startsWith(SCHEMA_BASE))) {
                invalid.add(reference);
            }
        });
        return invalid;
    }

    private static boolean verifyManifest(JsonNode manifest) throws IOException {
        if (!"1.0".equals(manifest.path("manifestVersion").asText())
                || !"agent".equals(manifest.path("contractFamily").asText())
                || !"1.0".equals(manifest.path("contractVersion").asText())
                || !DRAFT_2020_12.equals(manifest.path("schemaDialect").asText())) {
            return false;
        }

        List<String> manifestPaths = new ArrayList<>();
        StringBuilder canonical = new StringBuilder();
        for (JsonNode entry : manifest.path("files")) {
            String relative = entry.path("path").asText();
            if (relative.isBlank() || relative.contains("..") || relative.startsWith("/")) {
                return false;
            }
            Path file = AGENT_ROOT.resolve(relative).normalize();
            if (!file.startsWith(AGENT_ROOT) || !Files.isRegularFile(file)) {
                return false;
            }
            String digest = sha256(Files.readAllBytes(file));
            if (!digest.equals(entry.path("sha256").asText())) {
                return false;
            }
            manifestPaths.add(relative);
            canonical.append(digest).append("  ").append(relative).append('\n');
        }

        List<String> sorted = manifestPaths.stream().sorted().toList();
        if (!manifestPaths.equals(sorted) || new HashSet<>(manifestPaths).size() != manifestPaths.size()) {
            return false;
        }
        List<String> diskPaths;
        try (Stream<Path> files = Files.walk(AGENT_ROOT)) {
            diskPaths = files.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(AGENT_ROOT.resolve("manifest.json")))
                    .map(AGENT_ROOT::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        return manifestPaths.equals(diskPaths)
                && sha256(canonical.toString().getBytes(StandardCharsets.UTF_8))
                        .equals(manifest.path("bundleSha256").asText());
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> textValues(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }
}
