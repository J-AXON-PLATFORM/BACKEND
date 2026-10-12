package com.jaxon.collector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AgentSshKeyRequest(@NotBlank @Size(max = 4096) String publicKey) {}
