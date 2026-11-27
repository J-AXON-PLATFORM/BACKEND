package com.jaxon.inventory;

import java.util.List;

public interface AssetReportQuery {
    AssetReportPage find(AssetReportCriteria criteria);

    List<AssetReportRow> export(AssetReportCriteria criteria, int limit);
}
