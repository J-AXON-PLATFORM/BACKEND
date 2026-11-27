package com.jaxon.inventory;

import java.util.List;

public record AssetReportPage(
        List<AssetReportRow> items, int page, int size, long totalElements, long totalPages) {}
