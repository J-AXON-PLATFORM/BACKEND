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
    private static final Path DISCOVERY_ROOT = Path.of("doc/contracts/discovery/1.0");
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
    private static final Path OPENAPI_V43_ROOT = Path.of("doc/contracts/openapi/4.3");
    private static final Path OPENAPI_V44_ROOT = Path.of("doc/contracts/openapi/4.4");
    private static final Path OPENAPI_V45_ROOT = Path.of("doc/contracts/openapi/4.5");
    private static final Path OPENAPI_V50_ROOT = Path.of("doc/contracts/openapi/5.0");
    private static final Path OPENAPI_V51_ROOT = Path.of("doc/contracts/openapi/5.1");
    private static final Path OPENAPI_V52_ROOT = Path.of("doc/contracts/openapi/5.2");
    private static final Path OPENAPI_V60_ROOT = Path.of("doc/contracts/openapi/6.0");
    private static final Path OPENAPI_V70_ROOT = Path.of("doc/contracts/openapi/7.0");
    private static final Path OPENAPI_V71_ROOT = Path.of("doc/contracts/openapi/7.1");
    private static final Path OPENAPI_V72_ROOT = Path.of("doc/contracts/openapi/7.2");
    private static final Path OPENAPI_V80_ROOT = Path.of("doc/contracts/openapi/8.0");
    private static final Path OPENAPI_V81_ROOT = Path.of("doc/contracts/openapi/8.1");
    private static final Path OPENAPI_V82_ROOT = Path.of("doc/contracts/openapi/8.2");
    private static final String SCHEMA_BASE = "https://schemas.j-axon.invalid/agent/1.0/";
    private static final String DISCOVERY_SCHEMA_BASE =
            "https://schemas.j-axon.invalid/discovery/1.0/";
    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final List<String> AGENT_SCHEMAS = List.of(
            "common.schema.json", "snapshot.schema.json", "task.schema.json", "result.schema.json");
    private static final List<String> DISCOVERY_SCHEMAS =
            List.of("common.schema.json", "job.schema.json", "result.schema.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void openApi44Preserves43AndAddsOnlyAuthorizedScopeOperations() {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V44_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("4.4");
        assertThat(api.getPaths().keySet()).containsAll(List.of(
                "/discovery/scopes",
                "/discovery/scopes/{scopeId}",
                "/discovery/scopes/{scopeId}/status"));
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .contains(
                        "listAuthorizedScopes",
                        "createAuthorizedScope",
                        "getAuthorizedScope",
                        "updateAuthorizedScopeStatus")
                .doesNotHaveDuplicates();
    }

    @Test
    void openApi45Preserves44AndAddsOnlyDiscoveryExecutorOperations() {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V45_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V44_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("4.5");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 3);
        assertThat(api.getPaths().keySet()).contains(
                "/discovery/executors",
                "/discovery/executors/{executorId}",
                "/discovery/executors/{executorId}/ssh-key");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder(
                        "listDiscoveryExecutors",
                        "createDiscoveryExecutor",
                        "getDiscoveryExecutor",
                        "registerDiscoveryExecutorPublicKey")
                .doesNotHaveDuplicates();
    }

    @Test
    void openApi50Preserves45AndAddsOnlyDiscoveryExecutorActivation() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V50_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V45_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("5.0");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 1);
        assertThat(api.getPaths().keySet())
                .contains("/discovery/executors/{executorId}/activate");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder(
                        "listDiscoveryExecutors",
                        "createDiscoveryExecutor",
                        "getDiscoveryExecutor",
                        "registerDiscoveryExecutorPublicKey",
                        "activateDiscoveryExecutor")
                .doesNotHaveDuplicates();
        JsonNode executorSchema = Yaml31.mapper()
                .readTree(OPENAPI_V50_ROOT.resolve("openapi.yaml").toFile())
                .path("components")
                .path("schemas")
                .path("DiscoveryExecutor");
        assertThat(executorSchema.path("oneOf")).hasSize(3);
        assertThat(executorSchema.path("oneOf").get(0).path("properties")
                        .path("publicKeyRegistered").path("const").asBoolean())
                .isFalse();
        assertThat(executorSchema.path("oneOf").get(0).path("properties")
                        .path("publicKeyFingerprint").path("type").asText())
                .isEqualTo("null");
        assertThat(executorSchema.path("oneOf").get(1).path("properties")
                        .path("publicKeyRegistered").path("const").asBoolean())
                .isTrue();
    }

    @Test
    void openApi51Preserves50AndAddsOnlyDiscoveryJobOperations() {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        OpenAPI api = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V51_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V50_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("5.1");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 2)
                .contains("/discovery/jobs", "/discovery/jobs/{jobId}");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder("createDiscoveryJob", "getDiscoveryJob")
                .doesNotHaveDuplicates();
    }

    @Test
    void openApi52Preserves51AndAddsOnlyDiscoveredDeviceListing() {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V52_ROOT.resolve("openapi.yaml").toString(), null, options);
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V51_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();

        assertThat(result.getMessages()).isEmpty();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("5.2");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 1)
                .contains("/discovery-jobs/{jobId}/discovered-devices");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder(
                        "createDiscoveryJob", "getDiscoveryJob", "listDiscoveredDevices")
                .doesNotHaveDuplicates();
    }

    @Test
    void openApi60Preserves52OperationsAndAddsOnlyDiscoveredDeviceIgnore() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V60_ROOT.resolve("openapi.yaml").toString(), null, options);
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V52_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();

        assertThat(result.getMessages()).isEmpty();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("6.0");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 1)
                .contains("/discovery-jobs/{jobId}/discovered-devices/{discoveredDeviceId}/ignore");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder("listDiscoveredDevices", "ignoreDiscoveredDevice")
                .doesNotHaveDuplicates();

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V60_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(textValues(schemas.at("/DiscoveredDeviceIgnoreRequest/required")))
                .containsExactly("expectedVersion");
        assertThat(schemas.path("DiscoveredDeviceIgnoreRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(textValues(schemas.at("/DiscoveredDevice/properties/status/enum")))
                .containsExactly("UNREVIEWED", "IGNORED");
        assertThat(textValues(schemas.path("DiscoveredDevice").path("required")))
                .contains("version", "reviewedAt")
                .doesNotContain("reviewedByUserId");
        assertThat(schemas.path("DiscoveredDevice").path("oneOf")).hasSize(2);
        assertThat(root.at(
                                "/paths/~1discovery-jobs~1{jobId}~1discovered-devices~1{discoveredDeviceId}~1ignore/post/responses")
                        .has("400"))
                .isTrue();
    }

    @Test
    void openApi70Preserves60OperationsAndAddsOnlyDiscoveredDeviceIncorporation() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V70_ROOT.resolve("openapi.yaml").toString(), null, options);
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V60_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();

        assertThat(result.getMessages()).isEmpty();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("7.0");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 1)
                .contains("/discovery-jobs/{jobId}/discovered-devices/{discoveredDeviceId}/incorporate");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder(
                        "listDiscoveredDevices", "ignoreDiscoveredDevice", "incorporateDiscoveredDevice")
                .doesNotHaveDuplicates();

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V70_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(textValues(schemas.at("/DiscoveredDeviceIncorporateRequest/required")))
                .containsExactly("assetId", "expectedVersion");
        assertThat(schemas.path("DiscoveredDeviceIncorporateRequest")
                        .path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(textValues(schemas.at("/DiscoveredDevice/properties/status/enum")))
                .containsExactly("UNREVIEWED", "IGNORED", "INCORPORATED");
        assertThat(textValues(schemas.path("DiscoveredDevice").path("required")))
                .contains("version", "reviewedAt", "assetId")
                .doesNotContain("reviewedByUserId");
        assertThat(schemas.path("DiscoveredDevice").path("oneOf")).hasSize(3);
        assertThat(root.at(
                                "/paths/~1discovery-jobs~1{jobId}~1discovered-devices~1{discoveredDeviceId}~1incorporate/post/responses")
                        .has("409"))
                .isTrue();
    }

    @Test
    void openApi71Preserves70AndAddsOnlyNewAssetIncorporation() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V71_ROOT.resolve("openapi.yaml").toString(), null, options);
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V70_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();

        assertThat(result.getMessages()).isEmpty();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("7.1");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 1)
                .contains("/discovery-jobs/{jobId}/discovered-devices/{discoveredDeviceId}/incorporate-new-asset");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactly("incorporateDiscoveredDeviceWithNewAsset");

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V71_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schema = root.at("/components/schemas/DiscoveredDeviceNewAssetRequest");
        assertThat(textValues(schema.path("required")))
                .containsExactly("name", "assetType", "expectedVersion");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(root.at(
                                "/paths/~1discovery-jobs~1{jobId}~1discovered-devices~1{discoveredDeviceId}~1incorporate-new-asset/post/responses")
                        .has("409"))
                .isTrue();
    }

    @Test
    void openApi72Preserves71AndAddsOnlyAiSourceOperations() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V72_ROOT.resolve("openapi.yaml").toString(), null, options);
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V71_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();

        assertThat(result.getMessages()).isEmpty();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("7.2");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 2)
                .contains("/ai/sources", "/ai/sources/{sourceId}");
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .containsExactlyInAnyOrder("createAiSource", "listAiSources", "getAiSource")
                .doesNotHaveDuplicates();

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V72_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(textValues(schemas.at("/AiSourceCreateRequest/required")))
                .containsExactly("name", "file");
        assertThat(schemas.path("AiSourceCreateRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(schemas.at("/AiSourceCreateRequest/properties/file/contentMediaType").asText())
                .isEqualTo("text/plain");
        assertThat(schemas.at("/AiSourceCreateRequest/properties/file/x-maxBytes").asInt())
                .isEqualTo(1_048_576);
        assertThat(schemas.path("AiSource").path("additionalProperties").asBoolean()).isFalse();
        assertThat(textValues(schemas.path("AiSource").path("required")))
                .containsExactlyInAnyOrder(
                        "sourceId", "name", "mediaType", "sizeBytes", "sha256", "status",
                        "version", "createdByUserId", "createdAt", "updatedAt")
                .doesNotContain("storageKey", "filename", "content");
        assertThat(schemas.at("/AiSource/properties/status/const").asText()).isEqualTo("PENDING");
        assertThat(schemas.at("/AiSource/properties/sourceId/pattern").asText()).contains("-4");
        assertThat(root.at("/components/parameters/SourceId/schema/pattern").asText()).contains("-4");
        assertThat(schemas.path("AiSourcePage").path("additionalProperties").asBoolean()).isFalse();
        assertThat(root.at("/paths/~1ai~1sources/post/responses").has("413")).isTrue();
    }

    @Test
    void openApi80Preserves72AndAddsOnlyAiSourceIndexing() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V80_ROOT.resolve("openapi.yaml").toString(), null, options);
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V72_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();

        assertThat(result.getMessages()).isEmpty();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("8.0");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 1)
                .contains("/ai/sources/{sourceId}/index");

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V80_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(textValues(schemas.at("/AiSourceIndexRequest/required")))
                .containsExactly("expectedVersion");
        assertThat(schemas.path("AiSourceIndexRequest").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(textValues(schemas.at("/AiSource/properties/status/enum")))
                .containsExactly("PENDING", "INDEXING", "READY", "FAILED", "DISABLED");
        assertThat(root.at("/paths/~1ai~1sources~1{sourceId}~1index/post/operationId").asText())
                .isEqualTo("indexAiSource");
        assertThat(root.at("/paths/~1ai~1sources~1{sourceId}~1index/post/responses").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("202", "400", "401", "403", "404", "409", "422", "500", "503");
    }

    @Test
    void openApi81Preserves80AndAddsOnlyAuthorizedAiRetrieval() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V81_ROOT.resolve("openapi.yaml").toString(), null, options);
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V80_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();

        assertThat(result.getMessages()).isEmpty();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("8.1");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 1)
                .contains("/ai/retrieval");

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V81_ROOT.resolve("openapi.yaml").toFile());
        JsonNode operation = root.at("/paths/~1ai~1retrieval/post");
        assertThat(operation.path("operationId").asText()).isEqualTo("retrieveAiChunks");
        assertThat(operation.path("responses").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("200", "400", "401", "403", "422", "500", "503");
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(textValues(schemas.at("/AiRetrievalRequest/required"))).containsExactly("query");
        assertThat(schemas.path("AiRetrievalRequest").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.at("/AiRetrievalRequest/properties/query/maxLength").asInt()).isEqualTo(2000);
        assertThat(schemas.at("/AiRetrievalRequest/properties/limit/default").asInt()).isEqualTo(5);
        assertThat(schemas.at("/AiRetrievalRequest/properties/limit/maximum").asInt()).isEqualTo(10);
        assertThat(schemas.path("AiRetrievalResponse").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("AiRetrievalResult").path("additionalProperties").asBoolean()).isFalse();
        assertThat(textValues(schemas.at("/AiRetrievalResult/required")))
                .containsExactlyInAnyOrder(
                        "sourceId", "sourceName", "sourceSha256", "chunkId", "ordinal", "startToken",
                        "endToken", "contentSha256", "content", "similarity")
                .doesNotContain("storageKey", "embedding", "path");
        assertThat(schemas.at("/AiRetrievalResult/properties/similarity/minimum").asInt()).isEqualTo(-1);
        assertThat(schemas.at("/AiRetrievalResult/properties/similarity/maximum").asInt()).isEqualTo(1);
    }

    @Test
    void openApi82Preserves81AndAddsOnlyAiSourceDisable() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V82_ROOT.resolve("openapi.yaml").toString(), null, options);
        OpenAPI api = result.getOpenAPI();
        OpenAPI previous = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V81_ROOT.resolve("openapi.yaml").toString(), null, options)
                .getOpenAPI();

        assertThat(result.getMessages()).isEmpty();
        assertThat(api).isNotNull();
        assertThat(previous).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("8.2");
        assertThat(api.getPaths().keySet())
                .containsAll(previous.getPaths().keySet())
                .hasSize(previous.getPaths().size() + 1)
                .contains("/ai/sources/{sourceId}/disable");

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V82_ROOT.resolve("openapi.yaml").toFile());
        JsonNode operation = root.at("/paths/~1ai~1sources~1{sourceId}~1disable/post");
        assertThat(operation.path("operationId").asText()).isEqualTo("disableAiSource");
        assertThat(operation.path("responses").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("200", "400", "401", "403", "404", "409", "422", "500", "503");
        JsonNode request = root.at("/components/schemas/AiSourceDisableRequest");
        assertThat(textValues(request.path("required"))).containsExactly("expectedVersion");
        assertThat(request.path("additionalProperties").asBoolean()).isFalse();
        assertThat(request.path("properties").size()).isEqualTo(1);
        assertThat(request.at("/properties/expectedVersion/minimum").asInt()).isZero();
    }

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
    void openApi43AddsOnlyTerminalLifecycleOperations() throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OPENAPI_V43_ROOT.resolve("openapi.yaml").toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getVersion()).isEqualTo("4.3");
        var previousPaths = Yaml31.mapper()
                .readTree(OPENAPI_V42_ROOT.resolve("openapi.yaml").toFile())
                .path("paths")
                .properties()
                .stream()
                .map(Map.Entry::getKey)
                .toList();
        assertThat(api.getPaths().keySet())
                .contains(
                        "/tickets/{ticketId}/resolve",
                        "/tickets/{ticketId}/close",
                        "/tickets/{ticketId}/reopen",
                        "/tickets/{ticketId}/lifecycle",
                        "/tickets/technician-queue/{ticketId}/lifecycle")
                .containsAll(previousPaths)
                .hasSize(previousPaths.size() + 5);
        assertThat(operations(api).stream().map(Operation::getOperationId))
                .contains(
                        "resolveAssignedTicket",
                        "closeAssignedTicket",
                        "reopenAccessibleTicket",
                        "listOwnTicketLifecycle",
                        "listAssignedTechnicianTicketLifecycle")
                .doesNotHaveDuplicates();

        JsonNode root = Yaml31.mapper().readTree(OPENAPI_V43_ROOT.resolve("openapi.yaml").toFile());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(textValues(schemas.path("TicketResolveRequest").path("required")))
                .containsExactlyInAnyOrder("resolutionSummary", "expectedVersion");
        assertThat(schemas.at("/TicketResolveRequest/properties/resolutionSummary/maxLength").asInt())
                .isEqualTo(10_000);
        assertThat(schemas.at("/TicketReopenRequest/properties/reopenReason/maxLength").asInt())
                .isEqualTo(2_000);
        assertThat(textValues(schemas.at("/TicketCloseRequest/properties/closeReason/enum")))
                .containsExactly("RESOLVED", "CANCELLED", "DUPLICATE", "NOT_APPLICABLE");
        assertThat(schemas.path("TicketResolveRequest").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("TicketCloseRequest").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("TicketReopenRequest").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("TicketLifecycleEntry").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.path("TicketLifecyclePage").path("additionalProperties").asBoolean()).isFalse();
        assertThat(root.at("/paths/~1tickets~1{ticketId}~1resolve/post/responses").has("409")).isTrue();
        assertThat(root.at("/paths/~1tickets~1{ticketId}~1close/post/responses").has("409")).isTrue();
        assertThat(root.at("/paths/~1tickets~1{ticketId}~1reopen/post/responses").has("409")).isTrue();
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
        assertThat(sha256(Files.readAllBytes(OPENAPI_V42_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("ff4961375b266d9ca7dc217fb7993219efe166b77d2aed918f4d934607676c70");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V71_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("15aea35382ed0eeb618d507ed5701a908296526b04414de062ca31a78d3f2404");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V72_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("1b9fb32c3810de9131f4017aa0608c2a432db43d6370ef0e192328484952b036");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V80_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("c9072e6f0d317450bfc51c8939ca4755bf68808a005d86c8c4750c2a9c6de14b");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V81_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("227c9062ad876514c7f0f26ff5f241de4400b9712e711d02641eb6e4fa2e85fe");
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
        assertThat(verifyManifest(manifest, AGENT_ROOT, "agent")).isTrue();

        ObjectNode tampered = manifest.deepCopy();
        ((ObjectNode) tampered.get("files").get(0)).put("sha256", "0".repeat(64));
        assertThat(verifyManifest(tampered, AGENT_ROOT, "agent")).isFalse();
    }

    @Test
    void discoverySchemasFixturesAndManifestAreExecutableOffline() throws IOException {
        Map<String, String> schemas = new HashMap<>();
        for (String file : DISCOVERY_SCHEMAS) {
            schemas.put(DISCOVERY_SCHEMA_BASE + file, read(DISCOVERY_ROOT.resolve(file)));
        }
        Schema metaSchema = SchemaRegistry.withDialect(Dialects.getDraft202012())
                .getSchema(SchemaLocation.of(DRAFT_2020_12));
        for (String file : DISCOVERY_SCHEMAS) {
            assertThat(metaSchema.validate(schemas.get(DISCOVERY_SCHEMA_BASE + file), InputFormat.JSON))
                    .as("meta-schema validation for %s", file)
                    .isEmpty();
            assertThat(invalidReferences(
                            JSON.readTree(DISCOVERY_ROOT.resolve(file).toFile()), DISCOVERY_SCHEMA_BASE))
                    .as("safe references in %s", file)
                    .isEmpty();
            try (var packaged = getClass().getResourceAsStream("/contracts/discovery/1.0/" + file)) {
                assertThat(packaged).as("packaged Discovery schema %s", file).isNotNull();
                assertThat(packaged.readAllBytes())
                        .isEqualTo(Files.readAllBytes(DISCOVERY_ROOT.resolve(file)));
            }
        }

        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(schemas).schemaRegistryConfig(SchemaRegistryConfig.builder()
                        .formatAssertionsEnabled(true)
                        .failFast(false)
                        .build()));
        try (Stream<Path> files = Files.list(DISCOVERY_ROOT.resolve("examples/valid"))) {
            files.sorted().forEach(path -> assertThat(validateDiscovery(registry, schemaFor(path), path))
                    .as("valid Discovery fixture %s", path.getFileName())
                    .isEmpty());
        }
        try (Stream<Path> files = Files.list(DISCOVERY_ROOT.resolve("examples/invalid"))) {
            files.filter(path -> !List.of("job-invalid-expiry.json", "job-noncanonical-cidr.json")
                            .contains(path.getFileName().toString()))
                    .sorted()
                    .forEach(path -> assertThat(validateDiscovery(registry, schemaFor(path), path))
                    .as("invalid Discovery fixture %s", path.getFileName())
                    .isNotEmpty());
        }
        JsonNode manifest = JSON.readTree(DISCOVERY_ROOT.resolve("manifest.json").toFile());
        assertThat(verifyManifest(manifest, DISCOVERY_ROOT, "discovery")).isTrue();
        ObjectNode tampered = manifest.deepCopy();
        ((ObjectNode) tampered.get("files").get(0)).put("sha256", "0".repeat(64));
        assertThat(verifyManifest(tampered, DISCOVERY_ROOT, "discovery")).isFalse();
    }

    @Test
    void previousDiscoveryJobAndCurrentOpenApiRemainByteIdentical() throws IOException {
        assertThat(sha256(Files.readAllBytes(DISCOVERY_ROOT.resolve("common.schema.json"))))
                .isEqualTo("a08b85fce1b4a85f0378ec0a6eab45b8b6b2c02860355725e456df058a52c9aa");
        assertThat(sha256(Files.readAllBytes(DISCOVERY_ROOT.resolve("job.schema.json"))))
                .isEqualTo("6eb8e33b83dca4127330a756a98b91678e37dbc3ef4649450ca5497059639fae");
        assertThat(sha256(Files.readAllBytes(
                        DISCOVERY_ROOT.resolve("examples/valid/job-basic.json"))))
                .isEqualTo("66fb54d58e43bad3ca4d4354078d2ae148b8cc48140a6d86030514f2a8a80a8e");
        assertThat(sha256(Files.readAllBytes(
                        DISCOVERY_ROOT.resolve("examples/invalid/job-extra-property.json"))))
                .isEqualTo("03e7eab0f5ed95e4481c0a2c50c865e74247195953a855ae29e5ae30573d89c2");
        assertThat(sha256(Files.readAllBytes(
                        DISCOVERY_ROOT.resolve("examples/invalid/job-invalid-expiry.json"))))
                .isEqualTo("628724e56cf665b612b647d227b3027426833a912b643167fff556222f0b0ce5");
        assertThat(sha256(Files.readAllBytes(
                        DISCOVERY_ROOT.resolve("examples/invalid/job-invalid-uuid.json"))))
                .isEqualTo("e9f61220be216e52860b9f9cc7ea9d3dbedf52295435d3f56f39de539d8cb25b");
        assertThat(sha256(Files.readAllBytes(
                        DISCOVERY_ROOT.resolve("examples/invalid/job-non-utc-time.json"))))
                .isEqualTo("cc5f7e5dcc78af025a082190845dcdbc24a5af9af0c30c01bac684e451a3cdb0");
        assertThat(sha256(Files.readAllBytes(
                        DISCOVERY_ROOT.resolve("examples/invalid/job-noncanonical-cidr.json"))))
                .isEqualTo("c7555ed2c171c3b0aac5ef33fdcbfb76c0ba5aa23d3823a8306ec6b6c4261d86");
        assertThat(sha256(Files.readAllBytes(
                        DISCOVERY_ROOT.resolve("examples/invalid/job-unknown-version.json"))))
                .isEqualTo("d9a66a93ffc2918d0bdc45658c3bd7cf5cb76115a91f230877a7a438c76b21ea");
        assertThat(sha256(Files.readAllBytes(
                        DISCOVERY_ROOT.resolve("examples/invalid/job-wrong-message-type.json"))))
                .isEqualTo("54b4833e81338657f003f93fbdd95339cbe844d70eed4ae021ad828dfb7ae647");
        assertThat(sha256(Files.readAllBytes(OPENAPI_V51_ROOT.resolve("openapi.yaml"))))
                .isEqualTo("bbf30faef7d6da3e0e204ed874434c30fa0b71aa5fb69645fb185e0a14f18a94");
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

    private static List<com.networknt.schema.Error> validateDiscovery(
            SchemaRegistry registry, String schemaFile, Path fixture) {
        Schema schema = registry.getSchema(SchemaLocation.of(DISCOVERY_SCHEMA_BASE + schemaFile));
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
        if (name.startsWith("job-")) {
            return "job.schema.json";
        }
        throw new IllegalArgumentException("Unknown fixture family: " + name);
    }

    private static List<String> invalidReferences(JsonNode node) {
        return invalidReferences(node, SCHEMA_BASE);
    }

    private static List<String> invalidReferences(JsonNode node, String schemaBase) {
        List<String> invalid = new ArrayList<>();
        node.findValuesAsText("$ref").forEach(reference -> {
            if (reference.contains("..")
                    || (!reference.startsWith("#") && !reference.startsWith(schemaBase))) {
                invalid.add(reference);
            }
        });
        return invalid;
    }

    private static boolean verifyManifest(JsonNode manifest, Path root, String family)
            throws IOException {
        if (!"1.0".equals(manifest.path("manifestVersion").asText())
                || !family.equals(manifest.path("contractFamily").asText())
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
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
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
        try (Stream<Path> files = Files.walk(root)) {
            diskPaths = files.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(root.resolve("manifest.json")))
                    .map(root::relativize)
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
