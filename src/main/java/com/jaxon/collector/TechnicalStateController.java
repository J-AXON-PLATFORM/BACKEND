package com.jaxon.collector;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
class TechnicalStateController {

    private static final Pattern UUID_V4 = Pattern.compile(AgentController.UUID_V4_PATTERN);

    private final TechnicalStateService technicalStateService;

    TechnicalStateController(TechnicalStateService technicalStateService) {
        this.technicalStateService = technicalStateService;
    }

    @GetMapping("/{assetId}/technical-state")
    ResponseEntity<?> find(@PathVariable String assetId, HttpServletRequest request) {
        if (!UUID_V4.matcher(assetId).matches()) {
            return notFound(request);
        }
        return technicalStateService.find(UUID.fromString(assetId))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(request));
    }

    private static ResponseEntity<TechnicalStateProblem> notFound(HttpServletRequest request) {
        String correlation = MDC.get(CorrelationIdFilter.MDC_KEY);
        UUID correlationId = correlation == null ? UUID.randomUUID() : UUID.fromString(correlation);
        TechnicalStateProblem body = new TechnicalStateProblem(
                "https://problems.j-axon.invalid/asset-not-found",
                "Activo no encontrado",
                HttpStatus.NOT_FOUND.value(),
                "No existe un activo con el identificador indicado.",
                request.getRequestURI(),
                "ASSET_NOT_FOUND",
                correlationId,
                List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(body);
    }

    record TechnicalStateProblem(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String code,
            UUID correlationId,
            List<Object> errors) {}
}
