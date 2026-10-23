package com.jaxon.support;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

record TicketPriorityUpdateRequest(
        @NotNull @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
        @NotNull @PositiveOrZero Long expectedVersion) {}
