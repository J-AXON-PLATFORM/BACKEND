package com.jaxon.support;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

record TicketStatusUpdateRequest(
        @NotNull @Pattern(regexp = "OPEN|IN_PROGRESS|WAITING_FOR_REQUESTER") String status,
        @NotNull @PositiveOrZero Long expectedVersion) {}
