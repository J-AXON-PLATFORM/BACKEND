package com.jaxon.discovery;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discovery-jobs")
class DiscoveredDeviceController {
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private final DiscoveredDeviceService service;

    DiscoveredDeviceController(DiscoveredDeviceService service) {
        this.service = service;
    }

    @GetMapping("/{jobId}/discovered-devices")
    ResponseEntity<?> findPage(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(jobId).matches()) return notFound(request);
        PageRequest pagination = page(page, size);
        if (pagination == null) {
            return problem(request, HttpStatus.UNPROCESSABLE_CONTENT, "Paginación no válida",
                    "page debe ser mayor o igual que cero y size debe estar entre 1 y 50.",
                    "VALIDATION_FAILED");
        }
        return service.findPage(UUID.fromString(jobId), pagination.page(), pagination.size())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(request));
    }

    @PostMapping("/{jobId}/discovered-devices/{discoveredDeviceId}/ignore")
    ResponseEntity<?> ignore(
            @PathVariable String jobId,
            @PathVariable String discoveredDeviceId,
            @Valid @RequestBody DiscoveredDeviceIgnoreRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(jobId).matches() || !UUID_V4.matcher(discoveredDeviceId).matches()) {
            return deviceNotFound(request);
        }
        return ResponseEntity.ok(service.ignore(
                UUID.fromString(jobId),
                UUID.fromString(discoveredDeviceId),
                body.expectedVersion(),
                (UUID) authentication.getDetails(),
                UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY))));
    }

    @ExceptionHandler(DiscoveredDeviceNotFoundException.class)
    ResponseEntity<Problem> deviceNotFound(
            DiscoveredDeviceNotFoundException exception, HttpServletRequest request) {
        return deviceNotFound(request);
    }

    @ExceptionHandler(DiscoveredDeviceMutationConflictException.class)
    ResponseEntity<Problem> mutationConflict(
            DiscoveredDeviceMutationConflictException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "Conflicto al ignorar el dispositivo",
                "El dispositivo descubierto cambió de versión.",
                "DISCOVERED_DEVICE_MUTATION_CONFLICT");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> invalidRequest(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(
                        "/" + error.getField(), "INVALID_FIELD", "El valor no es válido."))
                .toList();
        Problem body = new Problem(
                "https://problems.j-axon.invalid/validation-failed",
                "Validación fallida",
                HttpStatus.UNPROCESSABLE_CONTENT.value(),
                "Revisa los campos indicados.",
                request.getRequestURI(),
                "VALIDATION_FAILED",
                UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)),
                errors);
        return ResponseEntity.unprocessableEntity()
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(body);
    }

    private static PageRequest page(String page, String size) {
        try {
            int parsedPage = Integer.parseInt(page);
            int parsedSize = Integer.parseInt(size);
            return parsedPage >= 0 && parsedSize >= 1 && parsedSize <= 50
                    ? new PageRequest(parsedPage, parsedSize) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static ResponseEntity<Problem> notFound(HttpServletRequest request) {
        return problem(request, HttpStatus.NOT_FOUND, "Discovery Job no encontrado",
                "No existe un Discovery Job con el identificador indicado.",
                "DISCOVERY_JOB_NOT_FOUND");
    }

    private static ResponseEntity<Problem> deviceNotFound(HttpServletRequest request) {
        return problem(request, HttpStatus.NOT_FOUND, "Dispositivo descubierto no encontrado",
                "No existe un dispositivo descubierto con los identificadores indicados.",
                "DISCOVERED_DEVICE_NOT_FOUND");
    }

    private static ResponseEntity<Problem> problem(
            HttpServletRequest request, HttpStatus status, String title, String detail, String code) {
        Problem body = new Problem(
                "https://problems.j-axon.invalid/" + code.toLowerCase().replace('_', '-'),
                title, status.value(), detail, request.getRequestURI(), code,
                UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)), List.of());
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json").body(body);
    }

    private record PageRequest(int page, int size) {}
    record Problem(String type, String title, int status, String detail, String instance,
            String code, UUID correlationId, List<FieldError> errors) {}
    record FieldError(String pointer, String code, String message) {}
}
