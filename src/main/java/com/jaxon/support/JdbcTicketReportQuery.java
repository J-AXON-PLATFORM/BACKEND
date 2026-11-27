package com.jaxon.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcTicketReportQuery implements TicketReportQuery {

    private static final String SELECT = """
            SELECT id, type, summary, status, priority, source, requester_user_id,
                   created_by_user_id, assignee_user_id, asset_id, created_at, updated_at
            FROM support.tickets
            """;
    private final JdbcTemplate jdbc;

    JdbcTicketReportQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TicketReportPage find(TicketReportCriteria criteria) {
        SqlFilter filter = filter(criteria);
        long total = count(filter);
        List<Object> arguments = new ArrayList<>(filter.arguments());
        arguments.add(criteria.size());
        arguments.add(Math.multiplyExact(criteria.page(), criteria.size()));
        List<TicketReportRow> items = jdbc.query(
                SELECT + filter.sql() + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (resultSet, row) -> map(resultSet),
                arguments.toArray());
        return new TicketReportPage(
                List.copyOf(items), criteria.page(), criteria.size(), total, totalPages(total, criteria.size()));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<TicketReportRow> export(TicketReportCriteria criteria, int limit) {
        SqlFilter filter = filter(criteria);
        List<Object> arguments = new ArrayList<>(filter.arguments());
        arguments.add(limit);
        return List.copyOf(jdbc.query(
                SELECT + filter.sql() + " ORDER BY created_at DESC, id DESC LIMIT ?",
                (resultSet, row) -> map(resultSet),
                arguments.toArray()));
    }

    private long count(SqlFilter filter) {
        Long value = jdbc.queryForObject(
                "SELECT count(*) FROM support.tickets" + filter.sql(),
                Long.class,
                filter.arguments().toArray());
        return value == null ? 0 : value;
    }

    private static TicketReportRow map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new TicketReportRow(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getString("type"),
                resultSet.getString("summary"),
                resultSet.getString("status"),
                resultSet.getString("priority"),
                resultSet.getString("source"),
                resultSet.getObject("requester_user_id", java.util.UUID.class),
                resultSet.getObject("created_by_user_id", java.util.UUID.class),
                resultSet.getObject("assignee_user_id", java.util.UUID.class),
                resultSet.getObject("asset_id", java.util.UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static SqlFilter filter(TicketReportCriteria criteria) {
        StringBuilder sql = new StringBuilder(" WHERE TRUE");
        List<Object> arguments = new ArrayList<>();
        add(sql, arguments, "type = ?", criteria.type());
        add(sql, arguments, "status = ?", criteria.status());
        add(sql, arguments, "priority = ?", criteria.priority());
        add(sql, arguments, "requester_user_id = ?", criteria.requesterUserId());
        add(sql, arguments, "assignee_user_id = ?", criteria.assigneeUserId());
        add(sql, arguments, "asset_id = ?", criteria.assetId());
        add(sql, arguments, "created_at >= ?", timestamp(criteria.createdFrom()));
        add(sql, arguments, "created_at < ?", timestamp(criteria.createdTo()));
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
