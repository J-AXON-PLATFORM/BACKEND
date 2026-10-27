package com.jaxon.discovery;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record DiscoveryResultMessage(
        String schemaVersion,
        UUID resultId,
        UUID jobId,
        UUID executorId,
        UUID scopeId,
        String profile,
        Instant startedAt,
        Instant completedAt,
        String status,
        String failureCode,
        Summary summary,
        List<Observation> observations) {

    record Summary(BigInteger targetsConsidered, int targetsScanned, int observationsFound) {}

    record Observation(
            String ipAddress,
            String macAddress,
            String hostname,
            List<String> evidence,
            List<Integer> openTcpPorts) {}
}
