package com.jaxon.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record AssetCreateRequest(
        @NotBlank @Size(max = 256) String name,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String assetType,
        @NotBlank @Pattern(regexp = "^(ACTIVE|INACTIVE)$") String status) {}
