package com.jaxon.inventory;

import java.util.UUID;

public interface AssetLookup {

    enum ReferenceStatus {
        NOT_FOUND,
        ACTIVE,
        INACTIVE
    }

    boolean exists(UUID assetId);

    boolean isActive(UUID assetId);

    ReferenceStatus lockForReference(UUID assetId);
}
