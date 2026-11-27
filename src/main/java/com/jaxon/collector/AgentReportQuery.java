package com.jaxon.collector;

import java.util.List;

public interface AgentReportQuery {
    AgentReportPage find(AgentReportCriteria criteria);

    List<AgentReportRow> export(AgentReportCriteria criteria, int limit);
}
