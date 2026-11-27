package com.jaxon.audit_reporting;

import com.jaxon.CorrelationIdFilter;
import com.jaxon.collector.AgentReportCriteria;
import com.jaxon.identity.SessionPrincipal;
import com.jaxon.inventory.AssetReportCriteria;
import com.jaxon.support.TicketReportCriteria;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
class ReportController {

    private static final Set<String> ASSET_FILTERS =
            Set.of("status", "assetType", "createdFrom", "createdTo");
    private static final Set<String> AGENT_FILTERS =
            Set.of("status", "operationalStatus", "assetId", "createdFrom", "createdTo");
    private static final Set<String> TICKET_FILTERS = Set.of(
            "type", "status", "priority", "requesterUserId", "assigneeUserId", "assetId",
            "createdFrom", "createdTo");
    private static final Set<String> PAGE = Set.of("page", "size");
    private static final Set<String> ASSET_STATUS = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> AGENT_STATUS = Set.of("PENDING", "ACTIVE");
    private static final Set<String> OPERATIONAL_STATUS = Set.of("UNKNOWN", "ONLINE", "OFFLINE");
    private static final Set<String> TICKET_TYPE = Set.of("INCIDENT", "SERVICE_REQUEST", "QUESTION", "OTHER");
    private static final Set<String> TICKET_STATUS =
            Set.of("OPEN", "IN_PROGRESS", "WAITING_FOR_REQUESTER", "RESOLVED", "CLOSED");
    private static final Set<String> PRIORITY = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/assets")
    ResponseEntity<?> assets(HttpServletRequest request) {
        Parsed<AssetReportCriteria> parsed = assets(request, true);
        return parsed.valid() ? ResponseEntity.ok(reports.assets(parsed.value())) : invalid(request, parsed.errors());
    }

    @GetMapping("/assets/export")
    ResponseEntity<?> exportAssets(
            HttpServletRequest request, @AuthenticationPrincipal SessionPrincipal principal) {
        Parsed<AssetReportCriteria> parsed = assets(request, false);
        if (!parsed.valid()) return invalid(request, parsed.errors());
        return csv(reports.exportAssets(parsed.value(), principal.userId(), correlationId()));
    }

    @GetMapping("/agents")
    ResponseEntity<?> agents(HttpServletRequest request) {
        Parsed<AgentReportCriteria> parsed = agents(request, true);
        return parsed.valid() ? ResponseEntity.ok(reports.agents(parsed.value())) : invalid(request, parsed.errors());
    }

    @GetMapping("/agents/export")
    ResponseEntity<?> exportAgents(
            HttpServletRequest request, @AuthenticationPrincipal SessionPrincipal principal) {
        Parsed<AgentReportCriteria> parsed = agents(request, false);
        if (!parsed.valid()) return invalid(request, parsed.errors());
        return csv(reports.exportAgents(parsed.value(), principal.userId(), correlationId()));
    }

    @GetMapping("/tickets")
    ResponseEntity<?> tickets(HttpServletRequest request) {
        Parsed<TicketReportCriteria> parsed = tickets(request, true);
        return parsed.valid() ? ResponseEntity.ok(reports.tickets(parsed.value())) : invalid(request, parsed.errors());
    }

    @GetMapping("/tickets/export")
    ResponseEntity<?> exportTickets(
            HttpServletRequest request, @AuthenticationPrincipal SessionPrincipal principal) {
        Parsed<TicketReportCriteria> parsed = tickets(request, false);
        if (!parsed.valid()) return invalid(request, parsed.errors());
        return csv(reports.exportTickets(parsed.value(), principal.userId(), correlationId()));
    }

    private static Parsed<AssetReportCriteria> assets(HttpServletRequest request, boolean paged) {
        ReportRequestParser parser = parser(request, ASSET_FILTERS, paged);
        String status = parser.member("status", ASSET_STATUS);
        String assetType = parser.code("assetType");
        Instant from = parser.instant("createdFrom");
        Instant to = parser.instant("createdTo");
        parser.range(from, to);
        int page = paged ? parser.integer("page", 0, 0, 1_000_000) : 0;
        int size = paged ? parser.integer("size", ReportRequestParser.DEFAULT_SIZE, 1, ReportRequestParser.MAX_SIZE)
                : ReportService.MAX_EXPORT_ROWS;
        return new Parsed<>(new AssetReportCriteria(status, assetType, from, to, page, size), parser.errors());
    }

    private static Parsed<AgentReportCriteria> agents(HttpServletRequest request, boolean paged) {
        ReportRequestParser parser = parser(request, AGENT_FILTERS, paged);
        String status = parser.member("status", AGENT_STATUS);
        String operationalStatus = parser.member("operationalStatus", OPERATIONAL_STATUS);
        UUID assetId = parser.uuid("assetId");
        Instant from = parser.instant("createdFrom");
        Instant to = parser.instant("createdTo");
        parser.range(from, to);
        int page = paged ? parser.integer("page", 0, 0, 1_000_000) : 0;
        int size = paged ? parser.integer("size", ReportRequestParser.DEFAULT_SIZE, 1, ReportRequestParser.MAX_SIZE)
                : ReportService.MAX_EXPORT_ROWS;
        return new Parsed<>(
                new AgentReportCriteria(status, operationalStatus, assetId, from, to, page, size),
                parser.errors());
    }

    private static Parsed<TicketReportCriteria> tickets(HttpServletRequest request, boolean paged) {
        ReportRequestParser parser = parser(request, TICKET_FILTERS, paged);
        String type = parser.member("type", TICKET_TYPE);
        String status = parser.member("status", TICKET_STATUS);
        String priority = parser.member("priority", PRIORITY);
        UUID requester = parser.uuid("requesterUserId");
        UUID assignee = parser.uuid("assigneeUserId");
        UUID asset = parser.uuid("assetId");
        Instant from = parser.instant("createdFrom");
        Instant to = parser.instant("createdTo");
        parser.range(from, to);
        int page = paged ? parser.integer("page", 0, 0, 1_000_000) : 0;
        int size = paged ? parser.integer("size", ReportRequestParser.DEFAULT_SIZE, 1, ReportRequestParser.MAX_SIZE)
                : ReportService.MAX_EXPORT_ROWS;
        return new Parsed<>(
                new TicketReportCriteria(type, status, priority, requester, assignee, asset, from, to, page, size),
                parser.errors());
    }

    private static ReportRequestParser parser(HttpServletRequest request, Set<String> filters, boolean paged) {
        Set<String> allowed = new java.util.HashSet<>(filters);
        if (paged) allowed.addAll(PAGE);
        return new ReportRequestParser(request, Set.copyOf(allowed));
    }

    private static ResponseEntity<byte[]> csv(ReportService.ExportResult result) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .body(result.bytes());
    }

    private static ResponseEntity<Problem> invalid(
            HttpServletRequest request, List<ReportRequestParser.Error> errors) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Filtros no válidos",
                "Los filtros o la paginación del reporte no son válidos.",
                "VALIDATION_FAILED",
                errors.stream()
                        .map(error -> new FieldError(
                                error.pointer(), error.code(), "El parámetro no es válido."))
                        .toList());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ReportExportLimitException.class)
    ResponseEntity<Problem> exportLimit(ReportExportLimitException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Exportación demasiado grande",
                "Restringe los filtros para exportar como máximo 10000 filas y 10 MiB.",
                "REPORT_EXPORT_LIMIT_EXCEEDED",
                List.of());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Problem> unavailable(RuntimeException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Reporte no disponible",
                "No fue posible consultar o confirmar el reporte.",
                "REPORT_UNAVAILABLE",
                List.of());
    }

    private static ResponseEntity<Problem> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<FieldError> errors) {
        Problem body = new Problem(
                "https://problems.j-axon.invalid/" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                code,
                correlationId(),
                errors);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static UUID correlationId() {
        return UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private record Parsed<T>(T value, List<ReportRequestParser.Error> errors) {
        boolean valid() {
            return errors.isEmpty();
        }
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
