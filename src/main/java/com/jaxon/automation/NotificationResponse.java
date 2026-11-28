package com.jaxon.automation;

import java.time.Instant;
import java.util.UUID;

record NotificationResponse(
        UUID notificationId,
        String type,
        String resourceType,
        UUID resourceId,
        String status,
        long version,
        Instant createdAt,
        Instant readAt) {}
