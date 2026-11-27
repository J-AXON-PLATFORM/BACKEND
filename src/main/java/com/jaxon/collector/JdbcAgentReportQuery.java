package com.jaxon.collector;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcAgentReportQuery implements AgentReportQuery {

    private static final String FROM = """
             FROM collector.agents agent
             LEFT JOIN (
                 SELECT agent_id, max(received_at) AS last_seen_at, max(captured_at) AS last_snapshot_at
                 FROM collector.accepted_snapshots
                 GROUP BY agent_id
             ) observation ON observation.agent_id = agent.id
            """;
    private static final String OPERATIONAL = """
            CASE
                WHEN observation.last_seen_at IS NULL THEN 'UNKNOWN'
                WHEN observation.last_seen_at < ? THEN 'OFFLINE'
                ELSE 'ONLINE'
            END
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration offlineThreshold;

    JdbcAgentReportQuery(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${jaxon.collector.agent-operational-status.offline-threshold:5m}")
                    Duration offlineThreshold) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.offlineThreshold = offlineThreshold;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AgentReportPage find(AgentReportCriteria criteria) {
        Timestamp cutoff = Timestamp.from(Instant.now(clock).minus(offlineThreshold));
        SqlFilter filter = filter(criteria, cutoff);
        long total = count(filter);
        List<Object> arguments = new ArrayList<>();
        arguments.add(cutoff);
        arguments.addAll(filter.arguments());
        arguments.add(criteria.size());
        arguments.add(Math.multiplyExact(criteria.page(), criteria.size()));
        List<AgentReportRow> items = jdbc.query(
                select() + filter.sql() + " ORDER BY agent.created_at DESC, agent.id DESC LIMIT ? OFFSET ?",
                (resultSet, row) -> map(resultSet),
                arguments.toArray());
        return new AgentReportPage(
                List.copyOf(items), criteria.page(), criteria.size(), total, totalPages(total, criteria.size()));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AgentReportRow> export(AgentReportCriteria criteria, int limit) {
        Timestamp cutoff = Timestamp.from(Instant.now(clock).minus(offlineThreshold));
        SqlFilter filter = filter(criteria, cutoff);
        List<Object> arguments = new ArrayList<>();
        arguments.add(cutoff);
        arguments.addAll(filter.arguments());
        arguments.add(limit);
        return List.copyOf(jdbc.query(
                select() + filter.sql() + " ORDER BY agent.created_at DESC, agent.id DESC LIMIT ?",
                (resultSet, row) -> map(resultSet),
                arguments.toArray()));
    }

    private long count(SqlFilter filter) {
        Long value = jdbc.queryForObject(
                "SELECT count(*)" + FROM + filter.sql(), Long.class, filter.arguments().toArray());
        return value == null ? 0 : value;
    }

    private static String select() {
        return "SELECT agent.id, agent.asset_id, agent.status, " + OPERATIONAL
                + " AS operational_status, observation.last_seen_at, observation.last_snapshot_at,"
                + " agent.created_at, agent.updated_at" + FROM;
    }

    private static AgentReportRow map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Timestamp seen = resultSet.getTimestamp("last_seen_at");
        Timestamp snapshot = resultSet.getTimestamp("last_snapshot_at");
        return new AgentReportRow(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getObject("asset_id", java.util.UUID.class),
                resultSet.getString("status"),
                resultSet.getString("operational_status"),
                seen == null ? null : seen.toInstant(),
                snapshot == null ? null : snapshot.toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static SqlFilter filter(AgentReportCriteria criteria, Timestamp cutoff) {
        StringBuilder sql = new StringBuilder(" WHERE TRUE");
        List<Object> arguments = new ArrayList<>();
        add(sql, arguments, "agent.status = ?", criteria.status());
        if (criteria.operationalStatus() != null) {
            sql.append(" AND ").append(OPERATIONAL).append(" = ?");
            arguments.add(cutoff);
            arguments.add(criteria.operationalStatus());
        }
        add(sql, arguments, "agent.asset_id = ?", criteria.assetId());
        add(sql, arguments, "agent.created_at >= ?", timestamp(criteria.createdFrom()));
        add(sql, arguments, "agent.created_at < ?", timestamp(criteria.createdTo()));
        return new SqlFilter(sql.toString(), List.copyOf(arguments));
    }

    private static void add(StringBuilder sql, List<Object> arguments, String clause, Object value) {
        if (value != null) {
            sql.append(" AND ").append(clause);
            arguments.add(value);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static long totalPages(long total, int size) {
        return (total + size - 1) / size;
    }

    private record SqlFilter(String sql, List<Object> arguments) {}
}
