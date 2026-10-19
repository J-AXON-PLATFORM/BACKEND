package com.jaxon.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record TicketCreateRequest(
        @NotBlank @Pattern(regexp = "^(INCIDENT|SERVICE_REQUEST|QUESTION|OTHER)$") String type,
        @NotBlank @Size(max = 256) String summary,
        @NotBlank @Size(max = 10_000) String description,
        UUID assetId) {}
