package com.jaxon.inventory;

import java.time.Instant;
import java.util.UUID;

record AssetResponse(
        UUID assetId,
        String name,
        String assetType,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
