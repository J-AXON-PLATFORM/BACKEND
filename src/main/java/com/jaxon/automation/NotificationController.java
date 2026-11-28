package com.jaxon.automation;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
@RequestMapping("/api/v1/notifications")
class NotificationController {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private static final Set<String> PARAMETERS = Set.of("status", "page", "size");
    private static final Set<String> STATUSES = Set.of("UNREAD", "READ");
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("^(0|[1-9]\\d{0,8})$");

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<?> find(Authentication authentication, HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        List<FieldError> errors = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            if (!PARAMETERS.contains(entry.getKey())) {
                errors.add(error("/query", "UNKNOWN_PARAMETER"));
                break;
            }
            if (entry.getValue().length != 1) {
                errors.add(error("/" + entry.getKey(), "DUPLICATE_PARAMETER"));
            }
        }
        if (!errors.isEmpty()) return invalid(request, errors);
        String status = member(parameters, "status", errors);
        Integer page = integer(parameters, "page", 0, 0, 1_000_000, errors);
        Integer size = integer(parameters, "size", DEFAULT_SIZE, 1, MAX_SIZE, errors);
        if (!errors.isEmpty()) return invalid(request, errors);
        return ResponseEntity.ok(service.findOwn(actorId(authentication), status, page, size));
    }

    @PostMapping("/{notificationId}/read")
    ResponseEntity<?> markRead(
            @PathVariable String notificationId,
            @Valid @RequestBody NotificationReadRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(notificationId).matches()) return notFound(request);
        return ResponseEntity.ok(service.markRead(
                UUID.fromString(notificationId),
                actorId(authentication),
                body.expectedVersion()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<Problem> notFound(
            NotificationNotFoundException exception, HttpServletRequest request) {
        return notFound(request);
    }

    @ExceptionHandler(NotificationVersionConflictException.class)
    ResponseEntity<Problem> conflict(
            NotificationVersionConflictException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Conflicto al marcar la notificación",
                "La notificación cambió de versión.",
                "NOTIFICATION_VERSION_CONFLICT",
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> invalidBody(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> error("/" + field.getField(), "INVALID_FIELD"))
                .toList();
        return invalid(request, errors);
    }

    private static String member(
            Map<String, String[]> parameters, String name, List<FieldError> errors) {
        String raw = value(parameters, name);
        if (raw == null) return null;
        if (STATUSES.contains(raw)) return raw;
        errors.add(error("/" + name, "INVALID_FIELD"));
        return null;
    }

    private static Integer integer(
            Map<String, String[]> parameters,
            String name,
            int fallback,
            int minimum,
            int maximum,
            List<FieldError> errors) {
        String raw = value(parameters, name);
        if (raw == null) return fallback;
        if (NON_NEGATIVE_INTEGER.matcher(raw).matches()) {
            int parsed = Integer.parseInt(raw);
            if (parsed >= minimum && parsed <= maximum) return parsed;
        }
        errors.add(error("/" + name, "INVALID_FIELD"));
        return null;
    }

    private static String value(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null ? null : values[0];
    }

    private static UUID actorId(Authentication authentication) {
        return (UUID) authentication.getDetails();
    }

    private static FieldError error(String pointer, String code) {
        return new FieldError(pointer, code, "El valor no es válido.");
    }

    private static ResponseEntity<Problem> invalid(
            HttpServletRequest request, List<FieldError> errors) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Filtros no válidos",
                "Los filtros o la paginación de notificaciones no son válidos.",
                "VALIDATION_FAILED",
                errors);
    }

    private static ResponseEntity<Problem> notFound(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Notificación no encontrada",
                "No existe una notificación accesible con el identificador indicado.",
                "NOTIFICATION_NOT_FOUND",
                List.of());
    }

    private static ResponseEntity<Problem> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<FieldError> errors) {
        String correlation = MDC.get(CorrelationIdFilter.MDC_KEY);
        Problem problem = new Problem(
                "https://problems.j-axon.invalid/" + code.toLowerCase().replace('_', '-'),
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                code,
                correlation == null ? UUID.randomUUID() : UUID.fromString(correlation),
                List.copyOf(errors));
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
}
