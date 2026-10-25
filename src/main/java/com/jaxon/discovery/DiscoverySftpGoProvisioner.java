package com.jaxon.discovery;

import java.util.UUID;

interface DiscoverySftpGoProvisioner {

    DiscoveryExecutorActivationResponse ensureProvisioned(UUID executorId, String publicKey);
}
