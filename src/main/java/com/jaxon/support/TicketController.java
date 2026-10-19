package com.jaxon.support;

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
@RequestMapping("/api/v1/tickets")
class TicketController {

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final TicketService ticketService;

    TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    ResponseEntity<TicketResponse> create(
            @Valid @RequestBody TicketCreateRequest request, Authentication authentication) {
        UUID actorId = actorId(authentication);
        UUID correlationId = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
        TicketResponse created = ticketService.create(request, actorId, correlationId);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.ticketId())).body(created);
    }

    @GetMapping("/{ticketId}")
    ResponseEntity<?> find(
            @PathVariable String ticketId,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(ticketId).matches()) {
            return ticketNotFound(request);
        }
        return ticketService.findOwn(UUID.fromString(ticketId), actorId(authentication))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ticketNotFound(request));
    }

    @ExceptionHandler(AssociatedAssetNotFoundException.class)
    ResponseEntity<SupportProblem> associatedAssetNotFound(
            AssociatedAssetNotFoundException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Activo no encontrado",
                "No existe un activo con el identificador indicado.",
                "ASSET_NOT_FOUND",
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<SupportProblem> invalidTicket(
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

    private static ResponseEntity<SupportProblem> ticketNotFound(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Ticket no encontrado",
                "No existe un ticket accesible con el identificador indicado.",
                "TICKET_NOT_FOUND",
                List.of());
    }

    private static ResponseEntity<SupportProblem> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<FieldError> errors) {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        UUID correlationId = value == null ? UUID.randomUUID() : UUID.fromString(value);
        SupportProblem problem = new SupportProblem(
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

    record SupportProblem(
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
