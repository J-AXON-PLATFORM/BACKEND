package com.jaxon.discovery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record DiscoveredDeviceResponse(
        UUID discoveredDeviceId,
        UUID jobId,
        String status,
        long version,
        Instant reviewedAt,
        UUID assetId,
        String ipAddress,
        String macAddress,
        String hostname,
        List<String> evidence,
        List<Integer> openTcpPorts,
        Instant observedAt) {}
