package com.jaxon.audit_reporting;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit/events")
class AuditEventController {

    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 200;
    private static final Set<String> PARAMETERS = Set.of(
            "from", "to", "module", "eventType", "actorId", "resourceType", "resourceId", "result",
            "correlationId", "page", "size");
    private static final Set<String> MODULES = Set.of(
            "INVENTORY", "DISCOVERY", "COLLECTOR", "SUPPORT", "AI", "IDENTITY", "AUTOMATION",
            "AUDIT_REPORTING");
    private static final Set<String> RESULTS = Set.of("SUCCESS", "DENIED", "FAILED");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private static final Pattern UTC_DATE_TIME = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z$");
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("^(0|[1-9]\\d{0,8})$");

    private final AuditEventPersistence persistence;

    AuditEventController(AuditEventPersistence persistence) {
        this.persistence = persistence;
    }

    @GetMapping
    ResponseEntity<?> find(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        List<FieldError> errors = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            if (!PARAMETERS.contains(entry.getKey())) {
                errors.add(error("/query", "UNKNOWN_PARAMETER"));
                break;
            } else if (entry.getValue().length != 1) {
                errors.add(error("/" + entry.getKey(), "DUPLICATE_PARAMETER"));
            }
        }
        if (!errors.isEmpty()) return invalid(request, errors);
        Instant from = instant(parameters, "from", errors);
        Instant to = instant(parameters, "to", errors);
        if (from != null && to != null && !from.isBefore(to)) errors.add(error("/to", "INVALID_RANGE"));
        String module = member(parameters, "module", MODULES, errors);
        String eventType = code(parameters, "eventType", errors);
        UUID actorId = uuid(parameters, "actorId", errors);
        String resourceType = code(parameters, "resourceType", errors);
        UUID resourceId = uuid(parameters, "resourceId", errors);
        String result = member(parameters, "result", RESULTS, errors);
        UUID correlationId = uuid(parameters, "correlationId", errors);
        Integer page = integer(parameters, "page", 0, 0, 1_000_000, errors);
        Integer size = integer(parameters, "size", DEFAULT_SIZE, 1, MAX_SIZE, errors);
        if (!errors.isEmpty()) return invalid(request, errors);
        return ResponseEntity.ok(persistence.find(new AuditEventQuery(
                from, to, module, eventType, actorId, resourceType, resourceId, result, correlationId,
                page, size)));
    }

    private static String value(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null ? null : values[0];
    }

    private static Instant instant(Map<String, String[]> parameters, String name, List<FieldError> errors) {
        String raw = value(parameters, name);
        if (raw == null) return null;
        try {
            if (UTC_DATE_TIME.matcher(raw).matches()) return Instant.parse(raw);
        } catch (DateTimeParseException exception) {
            // reported below
        }
        errors.add(error("/" + name, "INVALID_FIELD"));
        return null;
    }

    private static String member(
            Map<String, String[]> parameters, String name, Set<String> allowed, List<FieldError> errors) {
        String raw = value(parameters, name);
        if (raw == null) return null;
        if (allowed.contains(raw)) return raw;
        errors.add(error("/" + name, "INVALID_FIELD"));
        return null;
    }

    private static String code(Map<String, String[]> parameters, String name, List<FieldError> errors) {
        String raw = value(parameters, name);
        if (raw == null) return null;
        if (CODE.matcher(raw).matches()) return raw;
        errors.add(error("/" + name, "INVALID_FIELD"));
        return null;
    }

    private static UUID uuid(Map<String, String[]> parameters, String name, List<FieldError> errors) {
        String raw = value(parameters, name);
        if (raw == null) return null;
        if (UUID_V4.matcher(raw).matches()) return UUID.fromString(raw);
        errors.add(error("/" + name, "INVALID_FIELD"));
        return null;
    }

    private static Integer integer(
            Map<String, String[]> parameters, String name, int fallback, int minimum, int maximum,
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

    private static FieldError error(String pointer, String code) {
        return new FieldError(pointer, code, "El parámetro no es válido.");
    }

    private static ResponseEntity<Problem> invalid(HttpServletRequest request, List<FieldError> errors) {
        Problem problem = new Problem(
                "https://problems.j-axon.invalid/validation-failed",
                "Filtros no válidos",
                HttpStatus.UNPROCESSABLE_CONTENT.value(),
                "Los filtros o la paginación de auditoría no son válidos.",
                request.getRequestURI(),
                "VALIDATION_FAILED",
                UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)),
                List.copyOf(errors));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
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
