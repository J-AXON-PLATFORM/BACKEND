package com.jaxon.discovery;

import java.time.Instant;
import java.util.UUID;

record AuthorizedScopeResponse(
        UUID scopeId,
        String name,
        String cidr,
        String status,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
