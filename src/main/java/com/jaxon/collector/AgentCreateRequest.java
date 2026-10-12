package com.jaxon.collector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record AgentCreateRequest(
        @NotBlank
        @Pattern(regexp = AgentController.UUID_V4_PATTERN)
        String assetId) {}
