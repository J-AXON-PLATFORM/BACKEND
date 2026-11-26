package com.jaxon.audit_reporting;

/**
 * Public in-process contract owned by Audit/Reporting. It must be invoked inside the caller's
 * transaction so that the producer can mark its delivery atomically with the materialization.
 */
public interface AuditFactRecorder {

    AuditRecordOutcome record(AuditFact fact);
}
