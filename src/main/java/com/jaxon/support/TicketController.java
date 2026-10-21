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
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    ResponseEntity<?> findPage(
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            Authentication authentication,
            HttpServletRequest request) {
        PageRequest requestedPage = page(page, size, 50);
        if (requestedPage == null) {
            return invalidPage(request, 50);
        }
        return ResponseEntity.ok(ticketService.findOwnPage(
                actorId(authentication), requestedPage.page(), requestedPage.size()));
    }

    @GetMapping("/technician-queue")
    ResponseEntity<?> findTechnicianQueue(
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            Authentication authentication,
            HttpServletRequest request) {
        PageRequest requestedPage = page(page, size, 50);
        if (requestedPage == null) {
            return invalidPage(request, 50);
        }
        return ResponseEntity.ok(ticketService.findTechnicianQueue(
                actorId(authentication), requestedPage.page(), requestedPage.size()));
    }

    @GetMapping("/technician-queue/{ticketId}")
    ResponseEntity<?> findForTechnician(
            @PathVariable String ticketId,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(ticketId).matches()) {
            return ticketNotFound(request);
        }
        return ticketService.findForTechnician(UUID.fromString(ticketId), actorId(authentication))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ticketNotFound(request));
    }

    @GetMapping("/technician-queue/{ticketId}/comments")
    ResponseEntity<?> findCommentsForTechnician(
            @PathVariable String ticketId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "50") String size,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(ticketId).matches()) {
            return ticketNotFound(request);
        }
        PageRequest requestedPage = page(page, size, 100);
        if (requestedPage == null) {
            return invalidPage(request, 100);
        }
        return ticketService.findCommentsForTechnician(
                        UUID.fromString(ticketId),
                        actorId(authentication),
                        requestedPage.page(),
                        requestedPage.size())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ticketNotFound(request));
    }

    @GetMapping("/technician-queue/{ticketId}/conversation")
    ResponseEntity<?> findConversationForTechnician(
            @PathVariable String ticketId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "50") String size,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(ticketId).matches()) {
            return ticketNotFound(request);
        }
        PageRequest requestedPage = page(page, size, 100);
        if (requestedPage == null) {
            return invalidPage(request, 100);
        }
        return ticketService.findConversationForTechnician(
                        UUID.fromString(ticketId),
                        actorId(authentication),
                        requestedPage.page(),
                        requestedPage.size())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ticketNotFound(request));
    }

    @PostMapping("/technician-queue/{ticketId}/conversation")
    ResponseEntity<?> addTechnicianComment(
            @PathVariable String ticketId,
            @Valid @RequestBody TechnicianTicketCommentCreateRequest comment,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(ticketId).matches()) {
            return ticketNotFound(request);
        }
        UUID correlationId = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
        return ticketService.addTechnicianComment(
                        UUID.fromString(ticketId),
                        actorId(authentication),
                        comment.visibility(),
                        comment.body(),
                        correlationId)
                .<ResponseEntity<?>>map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .orElseGet(() -> ticketNotFound(request));
    }

    @PostMapping("/{ticketId}/claim")
    ResponseEntity<?> claim(
            @PathVariable String ticketId,
            @Valid @RequestBody TicketClaimRequest claim,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(ticketId).matches()) {
            return ticketNotFound(request);
        }
        UUID correlationId = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.ok(ticketService.claim(
                UUID.fromString(ticketId),
                actorId(authentication),
                claim.expectedVersion(),
                correlationId));
    }

    @PostMapping("/{ticketId}/comments")
    ResponseEntity<?> addComment(
            @PathVariable String ticketId,
            @Valid @RequestBody TicketCommentCreateRequest comment,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(ticketId).matches()) {
            return ticketNotFound(request);
        }
        UUID parsedTicketId = UUID.fromString(ticketId);
        UUID correlationId = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
        return ticketService.addOwnComment(
                        parsedTicketId, actorId(authentication), comment.body(), correlationId)
                .<ResponseEntity<?>>map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .orElseGet(() -> ticketNotFound(request));
    }

    @GetMapping("/{ticketId}/comments")
    ResponseEntity<?> findComments(
            @PathVariable String ticketId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "50") String size,
            Authentication authentication,
            HttpServletRequest request) {
        if (!UUID_V4.matcher(ticketId).matches()) {
            return ticketNotFound(request);
        }
        PageRequest requestedPage = page(page, size, 100);
        if (requestedPage == null) {
            return invalidPage(request, 100);
        }
        return ticketService.findOwnComments(
                        UUID.fromString(ticketId),
                        actorId(authentication),
                        requestedPage.page(),
                        requestedPage.size())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ticketNotFound(request));
    }

    @ExceptionHandler(TicketNotFoundException.class)
    ResponseEntity<SupportProblem> ticketNotFound(
            TicketNotFoundException exception, HttpServletRequest request) {
        return ticketNotFound(request);
    }

    @ExceptionHandler(TicketClaimConflictException.class)
    ResponseEntity<SupportProblem> ticketClaimConflict(
            TicketClaimConflictException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Conflicto al asignar ticket",
                "El ticket cambió de versión o ya no está disponible para asignación.",
                "TICKET_CLAIM_CONFLICT",
                List.of());
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

    @ExceptionHandler(InvalidCommentException.class)
    ResponseEntity<SupportProblem> invalidComment(
            InvalidCommentException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validación fallida",
                "Revisa los campos indicados.",
                "VALIDATION_FAILED",
                List.of(new FieldError("/body", "INVALID_FIELD", "El valor no es válido.")));
    }

    private static UUID actorId(Authentication authentication) {
        return (UUID) authentication.getDetails();
    }

    private static PageRequest page(String page, String size, int maximumSize) {
        try {
            int parsedPage = Integer.parseInt(page);
            int parsedSize = Integer.parseInt(size);
            if (parsedPage < 0 || parsedSize < 1 || parsedSize > maximumSize) {
                return null;
            }
            return new PageRequest(parsedPage, parsedSize);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static ResponseEntity<SupportProblem> invalidPage(HttpServletRequest request, int maximumSize) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Paginación no válida",
                "page debe ser mayor o igual que cero y size debe estar entre 1 y " + maximumSize + ".",
                "VALIDATION_FAILED",
                List.of());
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

    private record PageRequest(int page, int size) {}
}
