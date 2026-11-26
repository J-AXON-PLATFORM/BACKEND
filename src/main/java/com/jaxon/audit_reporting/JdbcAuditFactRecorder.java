package com.jaxon.audit_reporting;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class JdbcAuditFactRecorder implements AuditFactRecorder {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcAuditFactRecorder(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditRecordOutcome record(AuditFact fact) {
        if (!AuditCatalog.accepts(fact)) {
            throw new AuditFactRejectedException(AuditFactRejectedException.Reason.INVALID);
        }
        String metadata = objectMapper.writeValueAsString(new TreeMap<>(fact.metadata()));
        List<UUID> inserted = jdbc.queryForList(
                """
                INSERT INTO audit_reporting.audit_events
                    (audit_event_id, source_event_id, event_type, event_version, producer_module,
                     occurred_at, actor_type, actor_id, resource_type, resource_id,
                     resource_version, result, correlation_id, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (source_event_id) DO NOTHING
                RETURNING audit_event_id
                """,
                UUID.class,
                UUID.randomUUID(),
                fact.sourceEventId(),
                fact.eventType(),
                fact.eventVersion(),
                fact.producerModule(),
                Timestamp.from(fact.occurredAt()),
                fact.actorType(),
                fact.actorId(),
                fact.resourceType(),
                fact.resourceId(),
                fact.resourceVersion(),
                fact.result(),
                fact.correlationId(),
                metadata);
        if (!inserted.isEmpty()) return AuditRecordOutcome.RECORDED;
        if (!sameAsRecorded(fact)) {
            throw new AuditFactRejectedException(AuditFactRejectedException.Reason.CONFLICT);
        }
        return AuditRecordOutcome.DUPLICATE;
    }

    private boolean sameAsRecorded(AuditFact fact) {
        return jdbc.query(
                        """
                        SELECT event_type, event_version, producer_module, occurred_at, actor_type,
                               actor_id, resource_type, resource_id, resource_version, result,
                               correlation_id, metadata::text AS metadata
                        FROM audit_reporting.audit_events
                        WHERE source_event_id = ?
                        """,
                        (resultSet, index) -> {
                            Map<String, String> metadata = objectMapper.readValue(
                                    resultSet.getString("metadata"), new TypeReference<Map<String, String>>() {});
                            Long resourceVersion = resultSet.getObject("resource_version", Long.class);
                            return fact.eventType().equals(resultSet.getString("event_type"))
                                    && fact.eventVersion().equals(resultSet.getString("event_version"))
                                    && fact.producerModule().equals(resultSet.getString("producer_module"))
                                    && fact.occurredAt().equals(resultSet.getTimestamp("occurred_at").toInstant())
                                    && fact.actorType().equals(resultSet.getString("actor_type"))
                                    && Objects.equals(fact.actorId(), resultSet.getObject("actor_id", UUID.class))
                                    && fact.resourceType().equals(resultSet.getString("resource_type"))
                                    && fact.resourceId().equals(resultSet.getObject("resource_id", UUID.class))
                                    && Objects.equals(fact.resourceVersion(), resourceVersion)
                                    && fact.result().equals(resultSet.getString("result"))
                                    && fact.correlationId().equals(resultSet.getObject("correlation_id", UUID.class))
                                    && fact.metadata().equals(metadata);
                        },
                        fact.sourceEventId())
                .stream()
                .findFirst()
                .orElse(false);
    }
}
