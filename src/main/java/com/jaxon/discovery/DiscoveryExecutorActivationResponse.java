package com.jaxon.discovery;

record DiscoveryExecutorActivationResponse(
        String host,
        int port,
        String username,
        String hostKey,
        String hostKeyFingerprint) {}
