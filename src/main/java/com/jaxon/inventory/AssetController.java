package com.jaxon.inventory;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
class AssetController {

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final AssetService assetService;

    AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetCreateRequest request, Authentication authentication) {
        UUID actorId = (UUID) authentication.getDetails();
        UUID correlationId = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
        AssetResponse created = assetService.create(request, actorId, correlationId);
        return ResponseEntity.created(URI.create("/api/v1/assets/" + created.assetId())).body(created);
    }

    @GetMapping("/{assetId}")
    ResponseEntity<?> find(@PathVariable String assetId, HttpServletRequest request) {
        if (!UUID_V4.matcher(assetId).matches()) {
            return notFound(request);
        }
        return assetService.find(UUID.fromString(assetId))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(request));
    }

    @ExceptionHandler(AssetValidationException.class)
    ResponseEntity<InventoryProblem> inactiveAssetType(
            AssetValidationException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validación fallida",
                "El tipo de activo no pertenece al catálogo activo.",
                "VALIDATION_FAILED");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<InventoryProblem> invalidAsset(
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

    private static ResponseEntity<InventoryProblem> notFound(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Activo no encontrado",
                "No existe un activo con el identificador indicado.",
                "ASSET_NOT_FOUND");
    }

    static ResponseEntity<InventoryProblem> problem(
            HttpServletRequest request, HttpStatus status, String title, String detail, String code) {
        return problem(request, status, title, detail, code, List.of());
    }

    static ResponseEntity<InventoryProblem> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<FieldError> errors) {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        UUID correlationId = value == null ? UUID.randomUUID() : UUID.fromString(value);
        InventoryProblem problem = new InventoryProblem(
                "https://problems.j-axon.invalid/" + code.toLowerCase().replace('_', '-'),
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                code,
                correlationId,
                errors);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }

    record InventoryProblem(
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
