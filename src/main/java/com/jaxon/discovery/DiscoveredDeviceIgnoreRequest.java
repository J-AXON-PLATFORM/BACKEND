package com.jaxon.discovery;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

record DiscoveredDeviceIgnoreRequest(
        @NotNull @PositiveOrZero Long expectedVersion) {}
