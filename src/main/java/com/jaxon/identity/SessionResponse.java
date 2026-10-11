package com.jaxon.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record SessionResponse(
        UUID userId,
        String username,
        List<String> roles,
        String csrfToken,
        Instant expiresAt) {

    static SessionResponse from(SessionPrincipal principal) {
        return new SessionResponse(
                principal.userId(),
                principal.username(),
                principal.roles(),
                principal.csrfToken(),
                principal.expiresAt());
    }
}
