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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discovery/jobs")
class DiscoveryJobController {
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private final DiscoveryJobService service;

    DiscoveryJobController(DiscoveryJobService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<DiscoveryJobResponse> create(
            @Valid @RequestBody DiscoveryJobCreateRequest request, Authentication authentication) {
        DiscoveryJobResponse created = service.create(
                request, (UUID) authentication.getDetails(), correlationId());
        return ResponseEntity.created(URI.create("/api/v1/discovery/jobs/" + created.jobId()))
                .body(created);
    }

    @GetMapping("/{jobId}")
    ResponseEntity<?> find(@PathVariable String jobId, HttpServletRequest request) {
        if (!UUID_V4.matcher(jobId).matches()) return notFound(request);
        return service.find(UUID.fromString(jobId)).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(request));
    }

    @ExceptionHandler({DiscoveryJobNotFoundException.class, DiscoveryJobScopeNotFoundException.class})
    ResponseEntity<Problem> hiddenNotFound(RuntimeException exception, HttpServletRequest request) {
        return notFound(request);
    }

    @ExceptionHandler({DiscoveryJobConflictException.class,
        DiscoveryJobExecutorNotActiveException.class, DiscoveryJobScopeNotActiveException.class,
        DiscoveryJobPublicationConflictException.class, DiscoverySftpProvisioningConflictException.class})
    ResponseEntity<Problem> conflict(RuntimeException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "Discovery Job en conflicto",
                "El executor, el ámbito o la publicación no permiten crear el job.",
                "DISCOVERY_JOB_CONFLICT", List.of());
    }

    @ExceptionHandler({DiscoveryJobPublicationUnavailableException.class,
        DiscoverySftpProvisioningUnavailableException.class, DiscoverySftpHostKeyMismatchException.class})
    ResponseEntity<Problem> unavailable(RuntimeException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.SERVICE_UNAVAILABLE, "Publicación no disponible",
                "El job quedó pendiente y será reconciliado de forma segura.",
                "DISCOVERY_JOB_PUBLICATION_UNAVAILABLE", List.of());
    }

    @ExceptionHandler(DiscoveryJobPendingException.class)
    ResponseEntity<Problem> pending(DiscoveryJobPendingException exception, HttpServletRequest request) {
        URI location = URI.create("/api/v1/discovery/jobs/" + exception.jobId());
        Problem body = new Problem(
                "https://problems.j-axon.invalid/discovery-job-publication-unavailable",
                "Publicación no disponible", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "El job quedó pendiente y será reconciliado de forma segura.",
                location.toString(), "DISCOVERY_JOB_PUBLICATION_UNAVAILABLE",
                correlationId(), List.of());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .location(location)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(body);
    }

    @ExceptionHandler({InvalidDiscoveryJobRequestException.class,
        InvalidDiscoveryJobMessageException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Problem> invalid(RuntimeException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.UNPROCESSABLE_CONTENT, "Validación fallida",
                "Revisa los identificadores del executor y del ámbito.",
                "VALIDATION_FAILED", List.of());
    }

    private static ResponseEntity<Problem> notFound(HttpServletRequest request) {
        return problem(request, HttpStatus.NOT_FOUND, "Discovery Job no encontrado",
                "No existe un Discovery Job con el identificador indicado.",
                "DISCOVERY_JOB_NOT_FOUND", List.of());
    }

    private static UUID correlationId() {
        return UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static ResponseEntity<Problem> problem(HttpServletRequest request, HttpStatus status,
            String title, String detail, String code, List<FieldError> errors) {
        Problem body = new Problem(
                "https://problems.j-axon.invalid/" + code.toLowerCase().replace('_', '-'),
                title, status.value(), detail, request.getRequestURI(), code, correlationId(), errors);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json").body(body);
    }

    record Problem(String type, String title, int status, String detail, String instance,
            String code, UUID correlationId, List<FieldError> errors) {}
    record FieldError(String pointer, String code, String message) {}
}
