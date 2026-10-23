package com.jaxon.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record TicketReopenRequest(
        @NotBlank @Size(max = 2_000) String reopenReason,
        @NotNull @PositiveOrZero Long expectedVersion) {}
