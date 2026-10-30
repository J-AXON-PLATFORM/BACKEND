package com.jaxon.discovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record DiscoveredDeviceNewAssetRequest(
        @NotBlank @Size(max = 256) String name,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String assetType,
        @NotNull @PositiveOrZero Long expectedVersion) {}
