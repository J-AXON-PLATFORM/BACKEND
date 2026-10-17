package com.jaxon.collector;

import com.jaxon.CorrelationIdFilter;
import com.jaxon.identity.SessionPrincipal;
import com.jaxon.inventory.AssetLookup;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/assets")
class TechnicalStateController {

    private static final Pattern UUID_V4 = Pattern.compile(AgentController.UUID_V4_PATTERN);

    private final TechnicalStateService technicalStateService;
    private final AssetLookup assets;
    private final TechnicalStateEventBroadcaster events;

    TechnicalStateController(
            TechnicalStateService technicalStateService,
            AssetLookup assets,
            TechnicalStateEventBroadcaster events) {
        this.technicalStateService = technicalStateService;
        this.assets = assets;
        this.events = events;
    }

    @GetMapping(value = "/{assetId}/technical-state/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<?> events(
            @PathVariable String assetId,
            @AuthenticationPrincipal SessionPrincipal principal,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(assetId).matches()) {
            return notFound(request);
        }
        UUID parsedAssetId = UUID.fromString(assetId);
        if (!assets.exists(parsedAssetId)) {
            return notFound(request);
        }
        try {
            SseEmitter emitter = events.subscribe(parsedAssetId, principal.userId(), principal.expiresAt());
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(emitter);
        } catch (TechnicalStateEventBroadcaster.ConnectionLimitExceededException exception) {
            return tooManyRequests(request);
        }
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

    private static ResponseEntity<TechnicalStateProblem> tooManyRequests(HttpServletRequest request) {
        String correlation = MDC.get(CorrelationIdFilter.MDC_KEY);
        UUID correlationId = correlation == null ? UUID.randomUUID() : UUID.fromString(correlation);
        TechnicalStateProblem body = new TechnicalStateProblem(
                "https://problems.j-axon.invalid/sse-connection-limit-reached",
                "Límite de conexiones alcanzado",
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "No es posible abrir otra conexión de eventos en este momento.",
                request.getRequestURI(),
                "SSE_CONNECTION_LIMIT_REACHED",
                correlationId,
                List.of());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
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
