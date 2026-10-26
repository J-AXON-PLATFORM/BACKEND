package com.jaxon.discovery;

import jakarta.validation.constraints.NotBlank;

record DiscoveryJobCreateRequest(@NotBlank String executorId, @NotBlank String scopeId) {}
