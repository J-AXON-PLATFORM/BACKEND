package com.jaxon.discovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

record DiscoveryExecutorCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Size(min = 1, max = 50) List<@NotBlank String> scopeIds) {}
