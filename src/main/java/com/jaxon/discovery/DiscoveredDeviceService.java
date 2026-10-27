package com.jaxon.discovery;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    DiscoveredDeviceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    private static DiscoveredDeviceResponse response(ResultSet resultSet, int ignored) throws SQLException {
        return new DiscoveredDeviceResponse(
                resultSet.getObject("discovered_device_id", UUID.class),
                resultSet.getObject("job_id", UUID.class),
                resultSet.getString("status"),
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
