package com.jaxon.audit_reporting;

import java.util.List;

record AuditEventPage(List<AuditEventResponse> items, int page, int size, long totalElements, long totalPages) {}
