package com.jaxon.discovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AuthorizedScopeCreateRequest(
        @NotBlank String name,
        @NotBlank @Size(max = 64) String cidr) {}
