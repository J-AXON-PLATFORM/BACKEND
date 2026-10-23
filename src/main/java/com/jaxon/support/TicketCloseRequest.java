package com.jaxon.support;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

record TicketCloseRequest(
        @NotNull @Pattern(regexp = "RESOLVED|CANCELLED|DUPLICATE|NOT_APPLICABLE") String closeReason,
        @NotNull @PositiveOrZero Long expectedVersion) {}
