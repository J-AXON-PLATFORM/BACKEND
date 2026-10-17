package com.jaxon.collector;

import com.jaxon.inventory.AssetLookup;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
class TechnicalStateService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final AssetLookup assets;

    TechnicalStateService(JdbcTemplate jdbc, AssetLookup assets) {
        this.jdbc = jdbc;
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    Optional<TechnicalStateResponse> find(UUID assetId) {
        if (!assets.exists(assetId)) {
            return Optional.empty();
        }
        StateRow state = jdbc.query(
                        """
                        SELECT seen.last_seen_at, latest.snapshot_id, latest.captured_at,
                               latest.hostname, latest.os_family, latest.os_name, latest.os_version,
                               latest.architecture, latest.logical_cpu_count, latest.memory_bytes,
                               latest.storage_volumes
                        FROM (
                            SELECT max(s.received_at) AS last_seen_at
                            FROM collector.accepted_snapshots s
                            JOIN collector.agents a ON a.id = s.agent_id
                            WHERE a.asset_id = ?
                        ) seen
                        LEFT JOIN LATERAL (
                            SELECT s.snapshot_id, s.captured_at, s.hostname, s.os_family, s.os_name,
                                   s.os_version, s.architecture, s.logical_cpu_count, s.memory_bytes,
                                   s.storage_volumes
                            FROM collector.accepted_snapshots s
                            JOIN collector.agents a ON a.id = s.agent_id
                            WHERE a.asset_id = ?
                            ORDER BY s.captured_at DESC, s.received_at DESC, s.snapshot_id DESC
                            LIMIT 1
                        ) latest ON true
                        """,
                        (resultSet, row) -> {
                            java.sql.Timestamp lastSeen = resultSet.getTimestamp("last_seen_at");
                            return new StateRow(
                                    lastSeen == null ? null : lastSeen.toInstant(),
                                    resultSet.getObject("snapshot_id") == null ? null : observation(resultSet));
                        },
                        assetId,
                        assetId)
                .stream()
                .findFirst()
                .orElseThrow();
        return Optional.of(new TechnicalStateResponse(
                assetId,
                state.lastSeenAt(),
                state.latest() == null ? null : state.latest().capturedAt(),
                state.latest()));
    }

    private static TechnicalStateResponse.TechnicalObservation observation(ResultSet resultSet)
            throws SQLException {
        try {
            List<TechnicalStateResponse.StorageVolumeObservation> volumes = JSON.readValue(
                    resultSet.getString("storage_volumes"),
                    new TypeReference<>() {});
            return new TechnicalStateResponse.TechnicalObservation(
                    resultSet.getObject("snapshot_id", UUID.class),
                    resultSet.getTimestamp("captured_at").toInstant(),
                    resultSet.getString("hostname"),
                    new TechnicalStateResponse.OperatingSystemObservation(
                            resultSet.getString("os_family"),
                            resultSet.getString("os_name"),
                            resultSet.getString("os_version")),
                    resultSet.getString("architecture"),
                    resultSet.getInt("logical_cpu_count"),
                    resultSet.getLong("memory_bytes"),
                    volumes);
        } catch (JacksonException exception) {
            throw new SQLException("Stored technical observation is invalid", exception);
        }
    }

    private record StateRow(
            Instant lastSeenAt,
            TechnicalStateResponse.TechnicalObservation latest) {}
}
