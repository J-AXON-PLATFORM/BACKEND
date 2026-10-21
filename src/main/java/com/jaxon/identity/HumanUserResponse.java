package com.jaxon.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record HumanUserResponse(
        UUID userId, String username, String status, List<HumanRole> roles, Instant createdAt) {}
