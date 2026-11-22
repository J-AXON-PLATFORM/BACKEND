package com.jaxon.ai;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/ai/retrieval")
class AiRetrievalController {

    static final int MAX_QUERY_CODE_POINTS = 2000;
    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 10;
    private static final Set<String> FIELDS = Set.of("query", "limit");
    private static final ObjectMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final AiRetrievalService service;

    AiRetrievalController(AiRetrievalService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AiRetrievalResponse> retrieve(@RequestBody byte[] body) {
        JsonNode node;
        try {
            node = STRICT_JSON.readTree(body);
        } catch (JacksonException exception) {
            throw new MalformedAiRetrievalException();
        }
        RetrievalRequest request = parse(node);
        return ResponseEntity.ok(service.retrieve(request.query(), request.limit()));
    }

    static RetrievalRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidAiRetrievalException("", "INVALID_FIELD");
        }
        for (String name : body.propertyNames()) {
            if (!FIELDS.contains(name)) throw new InvalidAiRetrievalException("", "UNKNOWN_FIELD");
        }
        JsonNode queryNode = body.get("query");
        if (queryNode == null || !queryNode.isString()) {
            throw new InvalidAiRetrievalException("query", "INVALID_FIELD");
        }
        String rawQuery = queryNode.stringValue();
        if (rawQuery.codePointCount(0, rawQuery.length()) > MAX_QUERY_CODE_POINTS) {
            throw new InvalidAiRetrievalException("query", "TOO_LONG");
        }
        String query = trimBlank(rawQuery);
        if (query.isEmpty()) throw new InvalidAiRetrievalException("query", "BLANK");
        int limit = DEFAULT_LIMIT;
        JsonNode limitNode = body.get("limit");
        if (limitNode != null) {
            if (!limitNode.isIntegralNumber()
                    || !limitNode.canConvertToInt()
                    || limitNode.intValue() < 1
                    || limitNode.intValue() > MAX_LIMIT) {
                throw new InvalidAiRetrievalException("limit", "INVALID_FIELD");
            }
            limit = limitNode.intValue();
        }
        return new RetrievalRequest(query, limit);
    }

    static String trimBlank(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isBlank(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isBlank(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isBlank(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF
                || Character.getType(codePoint) == Character.CONTROL
                || Character.getType(codePoint) == Character.FORMAT;
    }

    @ExceptionHandler(MalformedAiRetrievalException.class)
    ResponseEntity<AiSourceController.Problem> malformed(
            MalformedAiRetrievalException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.BAD_REQUEST,
                "Solicitud mal formada",
                "El cuerpo JSON no es válido.",
                "MALFORMED_REQUEST",
                List.of());
    }

    @ExceptionHandler(InvalidAiRetrievalException.class)
    ResponseEntity<AiSourceController.Problem> invalid(
            InvalidAiRetrievalException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Consulta no válida",
                "Revisa la consulta y el límite indicados.",
                "VALIDATION_FAILED",
                List.of(new AiSourceController.FieldError(
                        exception.field().isEmpty() ? "" : "/" + exception.field(),
                        exception.errorCode(),
                        "El valor no es válido.")));
    }

    @ExceptionHandler(EmbeddingProviderUnavailableException.class)
    ResponseEntity<AiSourceController.Problem> unavailable(
            EmbeddingProviderUnavailableException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Recuperación no disponible",
                "El servicio local de embeddings no está disponible.",
                "AI_RETRIEVAL_UNAVAILABLE",
                List.of());
    }

    private static ResponseEntity<AiSourceController.Problem> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<AiSourceController.FieldError> errors) {
        AiSourceController.Problem problem = new AiSourceController.Problem(
                "https://problems.j-axon.invalid/" + code.toLowerCase().replace('_', '-'),
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                code,
                UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)),
                errors);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    record RetrievalRequest(String query, int limit) {}
}

final class MalformedAiRetrievalException extends RuntimeException {}
