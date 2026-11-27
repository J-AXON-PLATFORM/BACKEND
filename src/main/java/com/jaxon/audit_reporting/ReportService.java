package com.jaxon.audit_reporting;

import com.jaxon.collector.AgentReportCriteria;
import com.jaxon.collector.AgentReportPage;
import com.jaxon.collector.AgentReportQuery;
import com.jaxon.collector.AgentReportRow;
import com.jaxon.inventory.AssetReportCriteria;
import com.jaxon.inventory.AssetReportPage;
import com.jaxon.inventory.AssetReportQuery;
import com.jaxon.inventory.AssetReportRow;
import com.jaxon.support.TicketReportCriteria;
import com.jaxon.support.TicketReportPage;
import com.jaxon.support.TicketReportQuery;
import com.jaxon.support.TicketReportRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ReportService {

    static final int MAX_EXPORT_ROWS = 10_000;
    private static final int QUERY_LIMIT = MAX_EXPORT_ROWS + 1;

    private final AssetReportQuery assets;
    private final AgentReportQuery agents;
    private final TicketReportQuery tickets;
    private final ReportExportAuditService audit;

    ReportService(
            AssetReportQuery assets,
            AgentReportQuery agents,
            TicketReportQuery tickets,
            ReportExportAuditService audit) {
        this.assets = assets;
        this.agents = agents;
        this.tickets = tickets;
        this.audit = audit;
    }

    AssetReportPage assets(AssetReportCriteria criteria) {
        return assets.find(criteria);
    }

    AgentReportPage agents(AgentReportCriteria criteria) {
        return agents.find(criteria);
    }

    TicketReportPage tickets(TicketReportCriteria criteria) {
        return tickets.find(criteria);
    }

    ExportResult exportAssets(AssetReportCriteria criteria, UUID actorId, UUID correlationId) {
        List<AssetReportRow> rows = bounded(assets.export(criteria, QUERY_LIMIT));
        byte[] bytes = ReportCsv.encode(
                List.of("assetId", "name", "assetType", "status", "createdAt", "updatedAt"),
                rows.stream()
                        .map(row -> List.of(
                                cell(row.assetId()),
                                text(row.name()),
                                cell(row.assetType()),
                                cell(row.status()),
                                cell(row.createdAt()),
                                cell(row.updatedAt())))
                        .toList());
        audit.record(UUID.randomUUID(), "ASSETS", rows.size(), actorId, correlationId);
        return new ExportResult(bytes, "assets-report.csv");
    }

    ExportResult exportAgents(AgentReportCriteria criteria, UUID actorId, UUID correlationId) {
        List<AgentReportRow> rows = bounded(agents.export(criteria, QUERY_LIMIT));
        byte[] bytes = ReportCsv.encode(
                List.of(
                        "agentId", "assetId", "status", "operationalStatus", "lastSeenAt",
                        "lastSnapshotAt", "createdAt", "updatedAt"),
                rows.stream()
                        .map(row -> List.of(
                                cell(row.agentId()),
                                cell(row.assetId()),
                                cell(row.status()),
                                cell(row.operationalStatus()),
                                cell(row.lastSeenAt()),
                                cell(row.lastSnapshotAt()),
                                cell(row.createdAt()),
                                cell(row.updatedAt())))
                        .toList());
        audit.record(UUID.randomUUID(), "AGENTS", rows.size(), actorId, correlationId);
        return new ExportResult(bytes, "agents-report.csv");
    }

    ExportResult exportTickets(TicketReportCriteria criteria, UUID actorId, UUID correlationId) {
        List<TicketReportRow> rows = bounded(tickets.export(criteria, QUERY_LIMIT));
        byte[] bytes = ReportCsv.encode(
                List.of(
                        "ticketId", "type", "summary", "status", "priority", "source",
                        "requesterUserId", "createdByUserId", "assigneeUserId", "assetId",
                        "createdAt", "updatedAt"),
                rows.stream()
                        .map(row -> List.of(
                                cell(row.ticketId()),
                                cell(row.type()),
                                text(row.summary()),
                                cell(row.status()),
                                cell(row.priority()),
                                cell(row.source()),
                                cell(row.requesterUserId()),
                                cell(row.createdByUserId()),
                                cell(row.assigneeUserId()),
                                cell(row.assetId()),
                                cell(row.createdAt()),
                                cell(row.updatedAt())))
                        .toList());
        audit.record(UUID.randomUUID(), "TICKETS", rows.size(), actorId, correlationId);
        return new ExportResult(bytes, "tickets-report.csv");
    }

    private static <T> List<T> bounded(List<T> rows) {
        if (rows.size() > MAX_EXPORT_ROWS) throw new ReportExportLimitException();
        return rows;
    }

    private static ReportCsv.Cell cell(Object value) {
        return new ReportCsv.Cell(value == null ? null : format(value), false);
    }

    private static ReportCsv.Cell text(String value) {
        return new ReportCsv.Cell(value, true);
    }

    private static String format(Object value) {
        if (value instanceof Instant instant) return instant.toString();
        return value.toString();
    }

    record ExportResult(byte[] bytes, String filename) {}
}
