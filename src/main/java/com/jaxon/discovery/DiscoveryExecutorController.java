package com.jaxon.discovery;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discovery/executors")
class DiscoveryExecutorController {

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final DiscoveryExecutorService service;

    DiscoveryExecutorController(DiscoveryExecutorService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<DiscoveryExecutorResponse> create(
            @Valid @RequestBody DiscoveryExecutorCreateRequest request, Authentication authentication) {
        DiscoveryExecutorResponse created =
                service.create(request, actorId(authentication), correlationId());
        return ResponseEntity.created(
                        URI.create("/api/v1/discovery/executors/" + created.executorId()))
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

    @GetMapping("/{executorId}")
    ResponseEntity<?> find(@PathVariable String executorId, HttpServletRequest request) {
        UUID parsed = uuidV4(executorId);
        if (parsed == null) {
            return notFound(request);
        }
        return service.find(parsed)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(request));
    }

    @PutMapping("/{executorId}/ssh-key")
    ResponseEntity<?> registerPublicKey(
            @PathVariable String executorId,
            @Valid @RequestBody DiscoveryExecutorSshKeyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        UUID parsed = uuidV4(executorId);
        if (parsed == null) {
            return notFound(httpRequest);
        }
        return ResponseEntity.ok(
                service.registerPublicKey(parsed, request, actorId(authentication), correlationId()));
    }

    @ExceptionHandler(DiscoveryExecutorNotFoundException.class)
    ResponseEntity<Problem> notFound(
            DiscoveryExecutorNotFoundException exception, HttpServletRequest request) {
        return notFound(request);
    }

    @ExceptionHandler(DiscoveryExecutorConflictException.class)
    ResponseEntity<Problem> duplicate(
            DiscoveryExecutorConflictException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Discovery Executor duplicado",
                "Ya existe un Discovery Executor con el mismo nombre.",
                "DISCOVERY_EXECUTOR_CONFLICT",
                List.of());
    }

    @ExceptionHandler(DiscoveryExecutorScopeNotFoundException.class)
    ResponseEntity<Problem> scopeNotFound(
            DiscoveryExecutorScopeNotFoundException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Ámbito no encontrado",
                "Uno o más ámbitos indicados no existen.",
                "DISCOVERY_EXECUTOR_SCOPE_NOT_FOUND",
                List.of(new FieldError("/scopeIds", "UNKNOWN_SCOPE", "El ámbito no existe.")));
    }

    @ExceptionHandler(DiscoveryExecutorScopeNotActiveException.class)
    ResponseEntity<Problem> scopeNotActive(
            DiscoveryExecutorScopeNotActiveException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Ámbito no activo",
                "Todos los ámbitos deben estar activos durante el pre-registro.",
                "DISCOVERY_EXECUTOR_SCOPE_NOT_ACTIVE",
                List.of(new FieldError("/scopeIds", "INACTIVE_SCOPE", "El ámbito no está activo.")));
    }

    @ExceptionHandler(InvalidDiscoveryExecutorScopeIdsException.class)
    ResponseEntity<Problem> invalidScopeIds(
            InvalidDiscoveryExecutorScopeIdsException exception, HttpServletRequest request) {
        return validationProblem(request, "/scopeIds");
    }

    @ExceptionHandler(InvalidDiscoveryExecutorPublicKeyException.class)
    ResponseEntity<Problem> invalidPublicKey(
            InvalidDiscoveryExecutorPublicKeyException exception, HttpServletRequest request) {
        return validationProblem(request, "/publicKey");
    }

    @ExceptionHandler(DiscoveryExecutorPublicKeyConflictException.class)
    ResponseEntity<Problem> keyConflict(
            DiscoveryExecutorPublicKeyConflictException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Conflicto de clave pública",
                "La identidad ya tiene otra clave o la clave pertenece a otra identidad técnica.",
                "DISCOVERY_EXECUTOR_PUBLIC_KEY_CONFLICT",
                List.of());
    }

    @ExceptionHandler(InvalidAuthorizedScopeException.class)
    ResponseEntity<Problem> invalidName(
            InvalidAuthorizedScopeException exception, HttpServletRequest request) {
        return validationProblem(request, "/name");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> invalidRequest(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(
                        "/" + error.getField(), "INVALID_FIELD", "El valor no es válido."))
                .toList();
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validación fallida",
                "Revisa los campos indicados.",
                "VALIDATION_FAILED",
                errors);
    }

    private static ResponseEntity<Problem> validationProblem(
            HttpServletRequest request, String pointer) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validación fallida",
                "Revisa los campos indicados.",
                "VALIDATION_FAILED",
                List.of(new FieldError(pointer, "INVALID_FIELD", "El valor no es válido.")));
    }

    private static ResponseEntity<Problem> notFound(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Discovery Executor no encontrado",
                "No existe un Discovery Executor con el identificador indicado.",
                "DISCOVERY_EXECUTOR_NOT_FOUND",
                List.of());
    }

    private static UUID actorId(Authentication authentication) {
        return (UUID) authentication.getDetails();
    }

    private static UUID correlationId() {
        return UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
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

    private record PageRequest(int page, int size) {}
}
