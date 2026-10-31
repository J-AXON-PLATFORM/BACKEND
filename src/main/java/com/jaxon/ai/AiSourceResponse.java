package com.jaxon.ai;

import java.time.Instant;
import java.util.UUID;

record AiSourceResponse(
        UUID sourceId,
        String name,
        String mediaType,
        long sizeBytes,
        String sha256,
        String status,
        long version,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt) {}
