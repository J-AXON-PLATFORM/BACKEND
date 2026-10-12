package com.jaxon.inventory;

import java.util.UUID;

public interface AssetLookup {

    boolean exists(UUID assetId);
}
