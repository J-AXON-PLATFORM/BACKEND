package com.jaxon.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionPrincipal(
        UUID userId,
        String username,
        List<String> roles,
        String csrfToken,
        Instant expiresAt) {}
