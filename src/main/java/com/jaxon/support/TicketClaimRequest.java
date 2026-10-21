package com.jaxon.support;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

record TicketClaimRequest(@NotNull @PositiveOrZero Long expectedVersion) {}
