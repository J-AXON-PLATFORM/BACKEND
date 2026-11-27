package com.jaxon.inventory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcAssetReportQuery implements AssetReportQuery {

    private static final String SELECT = """
            SELECT id, name, asset_type, status, created_at, updated_at
            FROM inventory.assets
            """;
    private final JdbcTemplate jdbc;

    JdbcAssetReportQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AssetReportPage find(AssetReportCriteria criteria) {
        SqlFilter filter = filter(criteria);
        long total = count(filter);
        List<Object> arguments = new ArrayList<>(filter.arguments());
        arguments.add(criteria.size());
        arguments.add(Math.multiplyExact(criteria.page(), criteria.size()));
        List<AssetReportRow> items = jdbc.query(
                SELECT + filter.sql() + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (resultSet, row) -> new AssetReportRow(
                        resultSet.getObject("id", java.util.UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("asset_type"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                arguments.toArray());
        return new AssetReportPage(
                List.copyOf(items), criteria.page(), criteria.size(), total, totalPages(total, criteria.size()));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AssetReportRow> export(AssetReportCriteria criteria, int limit) {
        SqlFilter filter = filter(criteria);
        List<Object> arguments = new ArrayList<>(filter.arguments());
        arguments.add(limit);
        return List.copyOf(jdbc.query(
                SELECT + filter.sql() + " ORDER BY created_at DESC, id DESC LIMIT ?",
                (resultSet, row) -> new AssetReportRow(
                        resultSet.getObject("id", java.util.UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("asset_type"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                arguments.toArray()));
    }

    private long count(SqlFilter filter) {
        Long value = jdbc.queryForObject(
                "SELECT count(*) FROM inventory.assets" + filter.sql(),
                Long.class,
                filter.arguments().toArray());
        return value == null ? 0 : value;
    }

    private static SqlFilter filter(AssetReportCriteria criteria) {
        StringBuilder sql = new StringBuilder(" WHERE TRUE");
        List<Object> arguments = new ArrayList<>();
        add(sql, arguments, "status = ?", criteria.status());
        add(sql, arguments, "asset_type = ?", criteria.assetType());
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
