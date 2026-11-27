package com.jaxon.support;

import java.util.List;

public interface TicketReportQuery {
    TicketReportPage find(TicketReportCriteria criteria);

    List<TicketReportRow> export(TicketReportCriteria criteria, int limit);
}
