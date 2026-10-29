package com.jaxon.discovery;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

record DiscoveredDeviceIncorporateRequest(
        @NotNull String assetId,
        @NotNull @PositiveOrZero Long expectedVersion) {}
