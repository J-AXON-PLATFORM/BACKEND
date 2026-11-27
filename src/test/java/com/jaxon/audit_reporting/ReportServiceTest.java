package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaxon.collector.AgentReportQuery;
import com.jaxon.inventory.AssetReportCriteria;
import com.jaxon.inventory.AssetReportQuery;
import com.jaxon.inventory.AssetReportRow;
import com.jaxon.support.TicketReportQuery;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private static final AssetReportCriteria CRITERIA =
            new AssetReportCriteria(null, null, null, null, 0, ReportService.MAX_EXPORT_ROWS);
    private static final AssetReportRow ROW = new AssetReportRow(
            UUID.fromString("00000000-0000-4000-8000-000000000001"),
            "asset",
            "SERVER",
            "ACTIVE",
            Instant.EPOCH,
            Instant.EPOCH);

    private final AssetReportQuery assets = mock(AssetReportQuery.class);
    private final ReportExportAuditService audit = mock(ReportExportAuditService.class);
    private final ReportService service =
            new ReportService(assets, mock(AgentReportQuery.class), mock(TicketReportQuery.class), audit);

    @Test
    void acceptsExactlyTenThousandRowsAndAuditsTheCompletedCsv() {
        when(assets.export(CRITERIA, ReportService.MAX_EXPORT_ROWS + 1))
                .thenReturn(Collections.nCopies(ReportService.MAX_EXPORT_ROWS, ROW));
        UUID actorId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        ReportService.ExportResult result = service.exportAssets(CRITERIA, actorId, correlationId);

        assertThat(result.bytes()).isNotEmpty();
        verify(audit).record(any(UUID.class), eq("ASSETS"), eq(10_000), eq(actorId), eq(correlationId));
    }

    @Test
    void rejectsRowTenThousandAndOneWithoutAuditing() {
        List<AssetReportRow> rows = Collections.nCopies(ReportService.MAX_EXPORT_ROWS + 1, ROW);
        when(assets.export(CRITERIA, ReportService.MAX_EXPORT_ROWS + 1)).thenReturn(rows);

        assertThatThrownBy(() -> service.exportAssets(CRITERIA, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ReportExportLimitException.class);
        verify(audit, never()).record(any(), any(), any(Integer.class), any(), any());
    }
}
