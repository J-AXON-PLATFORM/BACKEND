package com.jaxon.discovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record DiscoveryExecutorSshKeyRequest(@NotBlank @Size(max = 4096) String publicKey) {}
