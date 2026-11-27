package com.jaxon.audit_reporting;

import com.jaxon.AuditFact;
import com.jaxon.AuditFactRecorder;
import com.jaxon.AuditRecordOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReportExportAuditService {

    private final AuditFactRecorder recorder;
    private final Clock clock;

    ReportExportAuditService(AuditFactRecorder recorder, Clock clock) {
        this.recorder = recorder;
        this.clock = clock;
    }

    @Transactional
    void record(UUID exportId, String reportType, int rowCount, UUID actorId, UUID correlationId) {
        Instant occurredAt = Instant.now(clock);
        AuditRecordOutcome outcome = recorder.record(new AuditFact(
                exportId,
                "REPORT_EXPORTED",
                "1.0",
                "AUDIT_REPORTING",
                occurredAt,
                "HUMAN_USER",
                actorId,
                "REPORT_EXPORT",
                exportId,
                null,
                "SUCCESS",
                correlationId,
                Map.of("format", "CSV", "reportType", reportType, "rowCount", Integer.toString(rowCount))));
        if (outcome != AuditRecordOutcome.RECORDED) {
            throw new IllegalStateException("A new report export must create one audit event");
        }
    }
}
