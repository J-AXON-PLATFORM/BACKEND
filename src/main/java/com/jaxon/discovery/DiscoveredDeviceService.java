package com.jaxon.discovery;

import com.jaxon.inventory.AssetLookup;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DiscoveredDeviceService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AssetLookup assetLookup;

    DiscoveredDeviceService(JdbcTemplate jdbc, Clock clock, AssetLookup assetLookup) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.assetLookup = assetLookup;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Optional<DiscoveredDevicePage> findPage(UUID jobId, int page, int size) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM discovery.discovery_jobs WHERE job_id = ?)",
                Boolean.class, jobId);
        if (!Boolean.TRUE.equals(exists)) return Optional.empty();
        long total = jdbc.queryForObject(
                "SELECT count(*) FROM discovery.discovered_devices WHERE job_id = ?",
                Long.class, jobId);
        List<DiscoveredDeviceResponse> items = jdbc.query(
                """
                SELECT * FROM discovery.discovered_devices
                WHERE job_id = ?
                ORDER BY observation_index, discovered_device_id
                LIMIT ? OFFSET ?
                """,
                DiscoveredDeviceService::response, jobId, size, (long) page * size);
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return Optional.of(new DiscoveredDevicePage(items, page, size, total, totalPages));
    }

    @Transactional
    DiscoveredDeviceResponse ignore(
            UUID jobId,
            UUID discoveredDeviceId,
            long expectedVersion,
            UUID actorId,
            UUID correlationId) {
        DiscoveredDeviceResponse current = jdbc.query(
                        """
                        SELECT * FROM discovery.discovered_devices
                        WHERE job_id = ? AND discovered_device_id = ?
                        FOR UPDATE
                        """,
                        DiscoveredDeviceService::response,
                        jobId,
                        discoveredDeviceId)
                .stream()
                .findFirst()
                .orElseThrow(DiscoveredDeviceNotFoundException::new);
        if (current.version() != expectedVersion) {
            throw new DiscoveredDeviceMutationConflictException();
        }
        if (current.status().equals("IGNORED")) {
            return current;
        }
        if (!current.status().equals("UNREVIEWED")) {
            throw new DiscoveredDeviceMutationConflictException();
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        long newVersion = current.version() + 1;
        jdbc.update(
                """
                UPDATE discovery.discovered_devices
                SET status = 'IGNORED', version = ?, reviewed_at = ?, reviewed_by_user_id = ?
                WHERE job_id = ? AND discovered_device_id = ?
                """,
                newVersion,
                Timestamp.from(now),
                actorId,
                jobId,
                discoveredDeviceId);
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'DISCOVERED_DEVICE_IGNORED', '1.0', ?, 'DISCOVERY',
                        'HUMAN_USER', ?, 'DISCOVERED_DEVICE', ?, ?, 'SUCCESS', ?,
                        jsonb_build_object('previousStatus', 'UNREVIEWED', 'newStatus', 'IGNORED'), ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                discoveredDeviceId,
                newVersion,
                correlationId,
                Timestamp.from(now));
        return jdbc.query(
                        "SELECT * FROM discovery.discovered_devices WHERE discovered_device_id = ?",
                        DiscoveredDeviceService::response,
                        discoveredDeviceId)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    DiscoveredDeviceResponse incorporate(
            UUID jobId,
            UUID discoveredDeviceId,
            UUID assetId,
            long expectedVersion,
            UUID actorId,
            UUID correlationId) {
        DiscoveredDeviceResponse current = jdbc.query(
                        """
                        SELECT * FROM discovery.discovered_devices
                        WHERE job_id = ? AND discovered_device_id = ?
                        FOR UPDATE
                        """,
                        DiscoveredDeviceService::response,
                        jobId,
                        discoveredDeviceId)
                .stream()
                .findFirst()
                .orElseThrow(DiscoveredDeviceNotFoundException::new);
        if (current.version() != expectedVersion) {
            throw new DiscoveredDeviceMutationConflictException();
        }
        if (current.status().equals("INCORPORATED")) {
            if (assetId.equals(current.assetId())) return current;
            throw new DiscoveredDeviceMutationConflictException();
        }
        if (!current.status().equals("UNREVIEWED")) {
            throw new DiscoveredDeviceMutationConflictException();
        }
        AssetLookup.ReferenceStatus assetStatus = assetLookup.lockForReference(assetId);
        if (assetStatus == AssetLookup.ReferenceStatus.NOT_FOUND) {
            throw new IncorporationAssetNotFoundException();
        }
        if (assetStatus != AssetLookup.ReferenceStatus.ACTIVE) {
            throw new IncorporationAssetNotActiveException();
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        long newVersion = current.version() + 1;
        jdbc.update(
                """
                UPDATE discovery.discovered_devices
                SET status = 'INCORPORATED', version = ?, reviewed_at = ?,
                    reviewed_by_user_id = ?, inventory_asset_id = ?
                WHERE job_id = ? AND discovered_device_id = ?
                """,
                newVersion,
                Timestamp.from(now),
                actorId,
                assetId,
                jobId,
                discoveredDeviceId);
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'DISCOVERED_DEVICE_INCORPORATED', '1.0', ?, 'DISCOVERY',
                        'HUMAN_USER', ?, 'DISCOVERED_DEVICE', ?, ?, 'SUCCESS', ?,
                        jsonb_build_object('previousStatus', 'UNREVIEWED',
                                           'newStatus', 'INCORPORATED',
                                           'assetId', ?::text), ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                discoveredDeviceId,
                newVersion,
                correlationId,
                assetId,
                Timestamp.from(now));
        return jdbc.query(
                        "SELECT * FROM discovery.discovered_devices WHERE discovered_device_id = ?",
                        DiscoveredDeviceService::response,
                        discoveredDeviceId)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private static DiscoveredDeviceResponse response(ResultSet resultSet, int ignored) throws SQLException {
        return new DiscoveredDeviceResponse(
                resultSet.getObject("discovered_device_id", UUID.class),
                resultSet.getObject("job_id", UUID.class),
                resultSet.getString("status"),
                resultSet.getLong("version"),
                resultSet.getTimestamp("reviewed_at") == null
                        ? null : resultSet.getTimestamp("reviewed_at").toInstant(),
                resultSet.getObject("inventory_asset_id", UUID.class),
                resultSet.getString("ip_address"),
                resultSet.getString("mac_address"),
                resultSet.getString("hostname"),
                strings(resultSet.getArray("evidence")),
                integers(resultSet.getArray("open_tcp_ports")),
                resultSet.getTimestamp("observed_at").toInstant());
    }

    private static List<String> strings(Array array) throws SQLException {
        return Arrays.asList((String[]) array.getArray());
    }

    private static List<Integer> integers(Array array) throws SQLException {
        return Arrays.asList((Integer[]) array.getArray());
    }
}

final class DiscoveredDeviceNotFoundException extends RuntimeException {}

final class DiscoveredDeviceMutationConflictException extends RuntimeException {}

final class IncorporationAssetNotFoundException extends RuntimeException {}

final class IncorporationAssetNotActiveException extends RuntimeException {}
