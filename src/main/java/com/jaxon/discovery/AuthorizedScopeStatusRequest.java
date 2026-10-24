package com.jaxon.discovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

record AuthorizedScopeStatusRequest(
        @NotBlank @Pattern(regexp = "^(ACTIVE|DISABLED)$") String status,
        @NotNull @PositiveOrZero Long expectedVersion) {}
