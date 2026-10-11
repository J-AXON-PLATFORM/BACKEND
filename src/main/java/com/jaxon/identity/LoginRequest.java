package com.jaxon.identity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record LoginRequest(
        @NotNull @Size(min = 1, max = 128) String username,
        @NotNull @Size(min = 1, max = 256) String password) {}
