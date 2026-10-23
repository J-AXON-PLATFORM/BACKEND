package com.jaxon.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record TicketResolveRequest(
        @NotBlank @Size(max = 10_000) String resolutionSummary,
        @NotNull @PositiveOrZero Long expectedVersion) {}
