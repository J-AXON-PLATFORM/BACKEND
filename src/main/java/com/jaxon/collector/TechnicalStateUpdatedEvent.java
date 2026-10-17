package com.jaxon.collector;

import java.time.Instant;
import java.util.UUID;

record TechnicalStateUpdatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID assetId,
        UUID correlationId) {

    static final String TYPE = "ASSET_TECHNICAL_STATE_UPDATED";
}
