package com.jaxon.collector;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class AgentOperationalStateService {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration offlineThreshold;

    AgentOperationalStateService(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${jaxon.collector.agent-operational-status.offline-threshold:5m}")
                    Duration offlineThreshold) {
        if (offlineThreshold.isNegative() || offlineThreshold.isZero()) {
            throw new IllegalArgumentException("Agent offline threshold must be positive");
        }
        this.jdbc = jdbc;
        this.clock = clock;
        this.offlineThreshold = offlineThreshold;
    }

    AgentOperationalState find(UUID agentId) {
        return jdbc.query(
                        """
                        SELECT max(received_at) AS last_seen_at,
                               max(captured_at) AS last_snapshot_at
                        FROM collector.accepted_snapshots
                        WHERE agent_id = ?
                        """,
                        (resultSet, row) -> {
                            Timestamp lastSeenAt = resultSet.getTimestamp("last_seen_at");
                            Timestamp lastSnapshotAt = resultSet.getTimestamp("last_snapshot_at");
                            Instant seen = lastSeenAt == null ? null : lastSeenAt.toInstant();
                            return new AgentOperationalState(
                                    derive(seen, Instant.now(clock), offlineThreshold),
                                    seen,
                                    lastSnapshotAt == null ? null : lastSnapshotAt.toInstant());
                        },
                        agentId)
                .getFirst();
    }

    long countAdministrative(String status) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM collector.agents WHERE status = ?", Long.class, status);
        return count == null ? 0 : count;
    }

    long countOperational(AgentOperationalStatus status) {
        Long count = jdbc.queryForObject(
                """
                SELECT count(*)
                FROM collector.agents agent
                LEFT JOIN (
                    SELECT agent_id, max(received_at) AS last_seen_at
                    FROM collector.accepted_snapshots
                    GROUP BY agent_id
                ) contact ON contact.agent_id = agent.id
                WHERE CASE
                    WHEN contact.last_seen_at IS NULL THEN 'UNKNOWN'
                    WHEN contact.last_seen_at < ? THEN 'OFFLINE'
                    ELSE 'ONLINE'
                END = ?
                """,
                Long.class,
                Timestamp.from(Instant.now(clock).minus(offlineThreshold)),
                status.name());
        return count == null ? 0 : count;
    }

    static AgentOperationalStatus derive(Instant lastSeenAt, Instant now, Duration offlineThreshold) {
        if (lastSeenAt == null) {
            return AgentOperationalStatus.UNKNOWN;
        }
        return lastSeenAt.isBefore(now.minus(offlineThreshold))
                ? AgentOperationalStatus.OFFLINE
                : AgentOperationalStatus.ONLINE;
    }

    record AgentOperationalState(
            AgentOperationalStatus operationalStatus,
            Instant lastSeenAt,
            Instant lastSnapshotAt) {

        static AgentOperationalState unknown() {
            return new AgentOperationalState(AgentOperationalStatus.UNKNOWN, null, null);
        }
    }
}
