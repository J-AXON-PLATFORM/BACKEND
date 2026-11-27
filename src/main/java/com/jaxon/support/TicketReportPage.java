package com.jaxon.support;

import java.util.List;

public record TicketReportPage(
        List<TicketReportRow> items, int page, int size, long totalElements, long totalPages) {}
