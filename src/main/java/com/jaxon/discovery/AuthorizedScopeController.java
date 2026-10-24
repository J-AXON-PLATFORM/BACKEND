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
@RequestMapping("/api/v1/discovery/scopes")
class AuthorizedScopeController {

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final AuthorizedScopeService service;

    AuthorizedScopeController(AuthorizedScopeService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<AuthorizedScopeResponse> create(
            @Valid @RequestBody AuthorizedScopeCreateRequest request, Authentication authentication) {
        AuthorizedScopeResponse created = service.create(request, actorId(authentication), correlationId());
        return ResponseEntity.created(URI.create("/api/v1/discovery/scopes/" + created.scopeId())).body(created);
    }

    @GetMapping
    ResponseEntity<?> findPage(
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            Authentication authentication,
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
        return ResponseEntity.ok(service.findPage(isAdministrator(authentication), requested.page(), requested.size()));
    }

    @GetMapping("/{scopeId}")
    ResponseEntity<?> find(
            @PathVariable String scopeId, Authentication authentication, HttpServletRequest request) {
        if (!UUID_V4.matcher(scopeId).matches()) {
            return notFound(request);
        }
        return service.find(UUID.fromString(scopeId), isAdministrator(authentication))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(request));
    }

    @PutMapping("/{scopeId}/status")
    ResponseEntity<?> updateStatus(
            @PathVariable String scopeId,
            @Valid @RequestBody AuthorizedScopeStatusRequest update,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(scopeId).matches()) {
            return notFound(request);
        }
        return ResponseEntity.ok(service.updateStatus(
                UUID.fromString(scopeId),
                update.status(),
                update.expectedVersion(),
                actorId(authentication),
                correlationId()));
    }

    @ExceptionHandler(AuthorizedScopeNotFoundException.class)
    ResponseEntity<DiscoveryProblem> notFound(
            AuthorizedScopeNotFoundException exception, HttpServletRequest request) {
        return notFound(request);
    }

    @ExceptionHandler(AuthorizedScopeConflictException.class)
    ResponseEntity<DiscoveryProblem> duplicate(
            AuthorizedScopeConflictException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Ámbito de descubrimiento duplicado",
                "Ya existe un ámbito con el mismo nombre o CIDR.",
                "DISCOVERY_SCOPE_CONFLICT",
                List.of());
    }

    @ExceptionHandler(AuthorizedScopeMutationConflictException.class)
    ResponseEntity<DiscoveryProblem> mutationConflict(
            AuthorizedScopeMutationConflictException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Conflicto al modificar el ámbito",
                "El ámbito cambió de versión.",
                "DISCOVERY_SCOPE_MUTATION_CONFLICT",
                List.of());
    }

    @ExceptionHandler(InvalidAuthorizedScopeException.class)
    ResponseEntity<DiscoveryProblem> invalidScope(
            InvalidAuthorizedScopeException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validación fallida",
                "Revisa los campos indicados.",
                "VALIDATION_FAILED",
                List.of(new FieldError("/" + exception.field(), "INVALID_FIELD", "El valor no es válido.")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<DiscoveryProblem> invalidRequest(
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

    private static UUID actorId(Authentication authentication) {
        return (UUID) authentication.getDetails();
    }

    private static UUID correlationId() {
        return UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static boolean isAdministrator(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SYSTEM_ADMIN"));
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

    private static ResponseEntity<DiscoveryProblem> notFound(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Ámbito de descubrimiento no encontrado",
                "No existe un ámbito accesible con el identificador indicado.",
                "DISCOVERY_SCOPE_NOT_FOUND",
                List.of());
    }

    private static ResponseEntity<DiscoveryProblem> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<FieldError> errors) {
        DiscoveryProblem problem = new DiscoveryProblem(
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

    record DiscoveryProblem(
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
