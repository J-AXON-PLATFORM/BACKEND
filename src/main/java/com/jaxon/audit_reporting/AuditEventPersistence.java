package com.jaxon.audit_reporting;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class AuditEventPersistence {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    AuditEventPersistence(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    AuditEventPage find(AuditEventQuery query) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        List<Object> arguments = new ArrayList<>();
        condition(where, arguments, " AND occurred_at >= ?", query.from() == null ? null : Timestamp.from(query.from()));
        condition(where, arguments, " AND occurred_at < ?", query.to() == null ? null : Timestamp.from(query.to()));
        condition(where, arguments, " AND producer_module = ?", query.module());
        condition(where, arguments, " AND event_type = ?", query.eventType());
        condition(where, arguments, " AND actor_id = ?", query.actorId());
        condition(where, arguments, " AND resource_type = ?", query.resourceType());
        condition(where, arguments, " AND resource_id = ?", query.resourceId());
        condition(where, arguments, " AND result = ?", query.result());
        condition(where, arguments, " AND correlation_id = ?", query.correlationId());
        long total = jdbc.queryForObject(
                "SELECT count(*) FROM audit_reporting.audit_events" + where, Long.class, arguments.toArray());
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(query.size());
        pageArguments.add((long) query.page() * query.size());
        List<AuditEventResponse> items = jdbc.query(
                """
                SELECT audit_event_id, source_event_id, event_type, event_version, producer_module,
                       occurred_at, recorded_at, actor_type, actor_id, resource_type, resource_id,
                       resource_version, result, correlation_id, metadata::text AS metadata
                FROM audit_reporting.audit_events
                """ + where + " ORDER BY occurred_at DESC, audit_event_id DESC LIMIT ? OFFSET ?",
                (resultSet, index) -> response(resultSet),
                pageArguments.toArray());
        long pages = total == 0 ? 0 : (total + query.size() - 1) / query.size();
        return new AuditEventPage(items, query.page(), query.size(), total, pages);
    }

    private static void condition(StringBuilder where, List<Object> arguments, String clause, Object value) {
        if (value == null) return;
        where.append(clause);
        arguments.add(value);
    }

    private AuditEventResponse response(ResultSet resultSet) throws SQLException {
        Map<String, String> metadata = objectMapper.readValue(
                resultSet.getString("metadata"), new TypeReference<Map<String, String>>() {});
        return new AuditEventResponse(
                resultSet.getObject("audit_event_id", UUID.class),
                resultSet.getObject("source_event_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getString("event_version"),
                resultSet.getString("producer_module"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getTimestamp("recorded_at").toInstant(),
                resultSet.getString("actor_type"),
                resultSet.getObject("actor_id", UUID.class),
                resultSet.getString("resource_type"),
                resultSet.getObject("resource_id", UUID.class),
                resultSet.getObject("resource_version", Long.class),
                resultSet.getString("result"),
                resultSet.getObject("correlation_id", UUID.class),
                metadata);
    }
}
