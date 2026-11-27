package com.jaxon.collector;

import java.util.List;

public record AgentReportPage(
        List<AgentReportRow> items, int page, int size, long totalElements, long totalPages) {}
