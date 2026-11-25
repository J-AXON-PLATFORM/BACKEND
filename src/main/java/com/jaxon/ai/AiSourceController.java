package com.jaxon.ai;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/ai/sources")
class AiSourceController {

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private static final ObjectMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final AiSourceService service;

    AiSourceController(AiSourceService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AiSourceResponse> create(
            @RequestPart("name") String name,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            HttpServletRequest request) {
        requireClosedMultipart(request);
        AiSourceResponse created = service.create(
                name, file, actorId(authentication), correlationId());
        return ResponseEntity.created(URI.create("/api/v1/ai/sources/" + created.sourceId()))
                .body(created);
    }

    @GetMapping
    ResponseEntity<?> findPage(
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            HttpServletRequest request) {
        PageRequest requested = page(page, size);
        if (requested == null) {
            return problem(
                    request,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Paginación no válida",
                    "page debe ser mayor o igual que cero y size debe estar entre 1 y 50.",
                    "VALIDATION_FAILED",
                    List.of());
        }
        return ResponseEntity.ok(service.findPage(requested.page(), requested.size()));
    }

    @GetMapping("/{sourceId}")
    ResponseEntity<?> find(@PathVariable String sourceId, HttpServletRequest request) {
        UUID parsed = uuidV4(sourceId);
        if (parsed == null) return notFound(request);
        return service.find(parsed)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(request));
    }

    @PostMapping(path = "/{sourceId}/index", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> index(
            @PathVariable String sourceId,
            @RequestBody IndexRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        UUID parsed = uuidV4(sourceId);
        if (parsed == null) return notFound(request);
        if (body == null || body.expectedVersion() == null || body.expectedVersion() < 0) {
            return problem(
                    request,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Versión no válida",
                    "expectedVersion debe ser un entero mayor o igual que cero.",
                    "VALIDATION_FAILED",
                    List.of(new FieldError(
                            "/expectedVersion", "INVALID_FIELD", "El valor no es válido.")));
        }
        return ResponseEntity.accepted().body(service.index(
                parsed, body.expectedVersion(), actorId(authentication), correlationId()));
    }

    @PostMapping(path = "/{sourceId}/disable", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> disable(
            @PathVariable String sourceId,
            @RequestBody byte[] body,
            Authentication authentication,
            HttpServletRequest request) {
        UUID parsed = uuidV4(sourceId);
        if (parsed == null) return notFound(request);
        JsonNode node;
        try {
            node = STRICT_JSON.readTree(body);
        } catch (JacksonException exception) {
            return problem(
                    request,
                    HttpStatus.BAD_REQUEST,
                    "Solicitud mal formada",
                    "El cuerpo JSON no es válido.",
                    "MALFORMED_REQUEST",
                    List.of());
        }
        Long expectedVersion = expectedVersion(node);
        if (expectedVersion == null) {
            return problem(
                    request,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Versión no válida",
                    "El cuerpo debe contener únicamente expectedVersion como entero mayor o igual que cero.",
                    "VALIDATION_FAILED",
                    List.of(new FieldError(
                            "/expectedVersion", "INVALID_FIELD", "El valor no es válido.")));
        }
        return ResponseEntity.ok(service.disable(
                parsed, expectedVersion, actorId(authentication), correlationId()));
    }

    static Long expectedVersion(JsonNode node) {
        if (node == null || !node.isObject() || node.size() != 1) return null;
        JsonNode value = node.get("expectedVersion");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) return null;
        long version = value.longValue();
        return version >= 0 ? version : null;
    }

    @ExceptionHandler(AiSourceDisableConflictException.class)
    ResponseEntity<Problem> disableConflict(
            AiSourceDisableConflictException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Fuente no deshabilitable",
                "La versión o el estado de la fuente ya no permite deshabilitarla.",
                "AI_SOURCE_DISABLE_CONFLICT",
                List.of());
    }

    @ExceptionHandler(InvalidAiSourceException.class)
    ResponseEntity<Problem> invalidSource(
            InvalidAiSourceException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Fuente documental no válida",
                "Revisa los campos indicados.",
                "VALIDATION_FAILED",
                List.of(new FieldError(
                        "/" + exception.field(), exception.errorCode(), "El valor no es válido.")));
    }

    @ExceptionHandler({DocumentTooLargeException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<Problem> tooLarge(RuntimeException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Fuente documental demasiado grande",
                "El archivo no puede superar 1 MiB.",
                "AI_SOURCE_TOO_LARGE",
                List.of(new FieldError("/file", "MAX_SIZE_EXCEEDED", "El archivo supera 1 MiB.")));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ResponseEntity<Problem> invalidMultipart(Exception exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.BAD_REQUEST,
                "Multipart no válido",
                "La petición debe incluir las partes name y file.",
                "INVALID_MULTIPART",
                List.of());
    }

    @ExceptionHandler(DocumentStorageUnavailableException.class)
    ResponseEntity<Problem> storageUnavailable(
            DocumentStorageUnavailableException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Almacenamiento documental no disponible",
                "No se pudo almacenar la fuente documental de forma segura.",
                "DOCUMENT_STORAGE_UNAVAILABLE",
                List.of());
    }

    @ExceptionHandler(EmbeddingProviderUnavailableException.class)
    ResponseEntity<Problem> embeddingUnavailable(
            EmbeddingProviderUnavailableException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Indexación no disponible",
                "El servicio local de indexación no está disponible.",
                "AI_INDEXING_UNAVAILABLE",
                List.of());
    }

    @ExceptionHandler(AiSourceNotFoundException.class)
    ResponseEntity<Problem> sourceNotFound(
            AiSourceNotFoundException exception, HttpServletRequest request) {
        return notFound(request);
    }

    @ExceptionHandler(AiSourceIndexConflictException.class)
    ResponseEntity<Problem> indexConflict(
            AiSourceIndexConflictException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Fuente no indexable",
                "La versión o el estado de la fuente ya no permite iniciar la indexación.",
                "AI_SOURCE_INDEX_CONFLICT",
                List.of());
    }

    private static ResponseEntity<Problem> notFound(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Fuente documental no encontrada",
                "No existe una fuente documental con el identificador indicado.",
                "AI_SOURCE_NOT_FOUND",
                List.of());
    }

    private static UUID actorId(Authentication authentication) {
        return (UUID) authentication.getDetails();
    }

    private static UUID correlationId() {
        return UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static void requireClosedMultipart(HttpServletRequest request) {
        try {
            Collection<Part> parts = request.getParts();
            long names = parts.stream().filter(part -> "name".equals(part.getName())).count();
            long files = parts.stream().filter(part -> "file".equals(part.getName())).count();
            if (parts.size() != 2 || names != 1 || files != 1) {
                throw new MultipartException("Unexpected multipart parts");
            }
        } catch (IOException | ServletException exception) {
            throw new MultipartException("Invalid multipart request", exception);
        }
    }

    private static UUID uuidV4(String value) {
        return UUID_V4.matcher(value).matches() ? UUID.fromString(value) : null;
    }

    private static PageRequest page(String page, String size) {
        try {
            int parsedPage = Integer.parseInt(page);
            int parsedSize = Integer.parseInt(size);
            return parsedPage >= 0 && parsedSize >= 1 && parsedSize <= 50
                    ? new PageRequest(parsedPage, parsedSize)
                    : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static ResponseEntity<Problem> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<FieldError> errors) {
        Problem problem = new Problem(
                "https://problems.j-axon.invalid/" + code.toLowerCase().replace('_', '-'),
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                code,
                correlationId(),
                errors);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    record Problem(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String code,
            UUID correlationId,
            List<FieldError> errors) {}

    record FieldError(String pointer, String code, String message) {}

    record IndexRequest(Long expectedVersion) {}

    private record PageRequest(int page, int size) {}
}
