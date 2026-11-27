package com.jaxon.inventory;

import java.time.Instant;

public record AssetReportCriteria(
        String status, String assetType, Instant createdFrom, Instant createdTo, int page, int size) {}
