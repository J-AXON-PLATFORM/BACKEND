package com.jaxon.support;

import java.time.Instant;
import java.util.UUID;

record TicketCommentResponse(
        UUID commentId,
        UUID ticketId,
        String visibility,
        UUID authorUserId,
        String body,
        Instant createdAt) {}
