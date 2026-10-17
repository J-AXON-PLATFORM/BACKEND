package com.jaxon.collector;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record SnapshotMessage(
        String contractVersion,
        UUID messageId,
        UUID agentId,
        Instant createdAt,
        UUID snapshotId,
        Instant capturedAt,
        String hostname,
        OperatingSystem operatingSystem,
        String architecture,
        int logicalCpuCount,
        long memoryBytes,
        List<StorageVolume> storageVolumes) {

    record OperatingSystem(String family, String name, String version) {}

    record StorageVolume(String name, long totalBytes, long availableBytes) {}
}
