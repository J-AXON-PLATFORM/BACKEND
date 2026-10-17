package com.jaxon.collector;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record TechnicalStateResponse(
        UUID assetId,
        Instant lastSeenAt,
        Instant lastSnapshotAt,
        TechnicalObservation latestObservation) {

    record TechnicalObservation(
            UUID snapshotId,
            Instant capturedAt,
            String hostname,
            OperatingSystemObservation operatingSystem,
            String architecture,
            int logicalCpuCount,
            long memoryBytes,
            List<StorageVolumeObservation> storageVolumes) {}

    record OperatingSystemObservation(String family, String name, String version) {}

    record StorageVolumeObservation(String name, long totalBytes, long availableBytes) {}
}
