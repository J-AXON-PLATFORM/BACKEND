package com.jaxon.ai;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
class AiSourcePersistence {

    private final JdbcTemplate jdbc;

    AiSourcePersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    AiSourceResponse create(
            UUID sourceId,
            String name,
            DocumentStorage.StoredDocument document,
            UUID actorId,
            UUID correlationId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO ai.document_sources
                    (id, name, media_type, size_bytes, sha256, storage_key, status,
                     created_by_user_id, created_at, updated_at, version)
                VALUES (?, ?, 'text/plain', ?, ?, ?, 'PENDING', ?, ?, ?, 0)
                """,
                sourceId,
                name,
                document.sizeBytes(),
                document.sha256(),
                document.storageKey(),
                actorId,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO ai.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'AI_SOURCE_REGISTERED', '1.0', ?, 'AI',
                        'HUMAN_USER', ?, 'AI_SOURCE', ?, 0,
                        'SUCCESS', ?, '{"status":"PENDING"}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                sourceId,
                correlationId,
                Timestamp.from(now));
        return find(sourceId).orElseThrow();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    AiSourcePage findPage(int page, int size) {
        long total = jdbc.queryForObject("SELECT count(*) FROM ai.document_sources", Long.class);
        List<AiSourceResponse> items = jdbc.query(
                """
                SELECT id, name, media_type, size_bytes, sha256, status, version,
                       created_by_user_id, created_at, updated_at
                FROM ai.document_sources
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """,
                (resultSet, index) -> response(resultSet),
                size,
                (long) page * size);
        return new AiSourcePage(
                items, page, size, total, Math.toIntExact((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    Optional<AiSourceResponse> find(UUID sourceId) {
        return jdbc.query(
                        """
                        SELECT id, name, media_type, size_bytes, sha256, status, version,
                               created_by_user_id, created_at, updated_at
                        FROM ai.document_sources WHERE id = ?
                        """,
                        (resultSet, index) -> response(resultSet),
                        sourceId)
                .stream()
                .findFirst();
    }

    static AiSourceResponse response(ResultSet resultSet) throws SQLException {
        return new AiSourceResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("media_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("sha256").trim(),
                resultSet.getString("status"),
                resultSet.getLong("version"),
                resultSet.getObject("created_by_user_id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
