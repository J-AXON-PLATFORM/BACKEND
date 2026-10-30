package com.jaxon.inventory;

import java.util.UUID;

public interface AssetCreator {

    UUID createActive(String name, String assetType, UUID actorId, UUID correlationId);
}
