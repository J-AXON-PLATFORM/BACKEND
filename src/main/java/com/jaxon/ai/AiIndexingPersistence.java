package com.jaxon.ai;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AiIndexingPersistence {

    private final JdbcTemplate jdbc;

    AiIndexingPersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    AiSourceResponse request(
            UUID sourceId,
            long expectedVersion,
            UUID actorId,
            UUID correlationId,
            boolean providerAvailable,
            Instant now) {
        SourceState source = jdbc.query(
                        "SELECT status, version FROM ai.document_sources WHERE id = ? FOR UPDATE",
                        (resultSet, index) -> new SourceState(
                                resultSet.getString("status"), resultSet.getLong("version")),
                        sourceId)
                .stream()
                .findFirst()
                .orElseThrow(AiSourceNotFoundException::new);
        if (!source.status().equals("PENDING") || source.version() != expectedVersion) {
            throw new AiSourceIndexConflictException();
        }
        if (!providerAvailable) throw new EmbeddingProviderUnavailableException();
        UUID jobId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO ai.source_indexing_jobs
                    (id, source_id, status, requested_by_user_id, correlation_id, created_at)
                VALUES (?, ?, 'PENDING', ?, ?, ?)
                """,
                jobId,
                sourceId,
                actorId,
                correlationId,
                Timestamp.from(now));
        AiSourceResponse response = jdbc.query(
                """
                UPDATE ai.document_sources
                SET status = 'INDEXING', version = version + 1, updated_at = ?
                WHERE id = ?
                RETURNING id, name, media_type, size_bytes, sha256, status, version,
                          created_by_user_id, created_at, updated_at
                """,
                (resultSet, index) -> AiSourcePersistence.response(resultSet),
                Timestamp.from(now),
                sourceId).getFirst();
        addEvent(
                "AI_SOURCE_INDEXING_REQUESTED",
                sourceId,
                1,
                "HUMAN_USER",
                actorId,
                "SUCCESS",
                correlationId,
                "INDEXING",
                now);
        return response;
    }

    @Transactional
    Optional<ClaimedJob> claimNext(Instant now, Duration claimTimeout) {
        Optional<ClaimedJob> selected = jdbc.query(
                        """
                        SELECT j.id, j.source_id, j.requested_by_user_id, j.correlation_id,
                               s.storage_key, s.size_bytes, s.sha256
                        FROM ai.source_indexing_jobs j
                        JOIN ai.document_sources s ON s.id = j.source_id
                        WHERE j.status = 'PENDING' AND s.status = 'INDEXING'
                        ORDER BY j.created_at, j.id
                        FOR UPDATE OF j SKIP LOCKED
                        LIMIT 1
                        """,
                        (resultSet, index) -> claimedJob(resultSet))
                .stream()
                .findFirst();
        selected.ifPresent(job -> jdbc.update(
                """
                UPDATE ai.source_indexing_jobs
                SET status = 'CLAIMED', claimed_at = ?, claim_expires_at = ?
                WHERE id = ? AND status = 'PENDING'
                """,
                Timestamp.from(now),
                Timestamp.from(now.plus(claimTimeout)),
                job.jobId()));
        return selected;
    }

    @Transactional
    int recoverExpired(Instant now) {
        List<ClaimedJob> expired = jdbc.query(
                """
                SELECT j.id, j.source_id, j.requested_by_user_id, j.correlation_id,
                       s.storage_key, s.size_bytes, s.sha256
                FROM ai.source_indexing_jobs j
                JOIN ai.document_sources s ON s.id = j.source_id
                WHERE j.status = 'CLAIMED' AND j.claim_expires_at < ? AND s.status = 'INDEXING'
                ORDER BY j.claim_expires_at, j.id
                FOR UPDATE OF j SKIP LOCKED
                LIMIT 100
                """,
                (resultSet, index) -> claimedJob(resultSet),
                Timestamp.from(now));
        for (ClaimedJob job : expired) {
            failLocked(job, "INDEXING_INTERRUPTED", now);
        }
        return expired.size();
    }

    @Transactional
    boolean complete(ClaimedJob job, List<IndexedChunk> chunks, Instant now) {
        if (!lockActiveClaim(job.jobId(), job.sourceId(), now)) return false;
        for (IndexedChunk chunk : chunks) {
            jdbc.update(
                    """
                    INSERT INTO ai.source_chunks
                        (id, source_id, ordinal, content, content_sha256, token_count,
                         start_token, end_token, embedding, model_id, model_revision,
                         tokenizer_sha256, chunker_version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    job.sourceId(),
                    chunk.ordinal(),
                    chunk.content(),
                    chunk.sha256(),
                    chunk.tokenCount(),
                    chunk.startToken(),
                    chunk.endToken(),
                    vectorLiteral(chunk.embedding()),
                    EmbeddingProvider.MODEL_ID,
                    EmbeddingProvider.MODEL_REVISION,
                    EmbeddingProvider.VOCABULARY_DIGEST,
                    AiSourceChunker.VERSION,
                    Timestamp.from(now));
        }
        jdbc.update(
                """
                UPDATE ai.source_indexing_jobs
                SET status = 'SUCCEEDED', claim_expires_at = NULL, completed_at = ?
                WHERE id = ?
                """,
                Timestamp.from(now),
                job.jobId());
        jdbc.update(
                """
                UPDATE ai.document_sources
                SET status = 'READY', version = version + 1, updated_at = ?
                WHERE id = ? AND status = 'INDEXING'
                """,
                Timestamp.from(now),
                job.sourceId());
        addEvent(
                "AI_SOURCE_INDEXED",
                job.sourceId(),
                2,
                "SYSTEM",
                null,
                "SUCCESS",
                job.correlationId(),
                "READY",
                now);
        return true;
    }

    @Transactional
    boolean fail(ClaimedJob job, String failureCode, Instant now) {
        if (!lockActiveClaim(job.jobId(), job.sourceId(), now)) return false;
        failLocked(job, failureCode, now);
        return true;
    }

    private void failLocked(ClaimedJob job, String failureCode, Instant now) {
        jdbc.update(
                """
                UPDATE ai.source_indexing_jobs
                SET status = 'FAILED', claim_expires_at = NULL, completed_at = ?, failure_code = ?
                WHERE id = ?
                """,
                Timestamp.from(now),
                failureCode,
                job.jobId());
        jdbc.update(
                """
                UPDATE ai.document_sources
                SET status = 'FAILED', version = version + 1, updated_at = ?
                WHERE id = ? AND status = 'INDEXING'
                """,
                Timestamp.from(now),
                job.sourceId());
        addEvent(
                "AI_SOURCE_INDEXING_FAILED",
                job.sourceId(),
                2,
                "SYSTEM",
                null,
                "FAILED",
                job.correlationId(),
                "FAILED",
                now);
    }

    private boolean lockActiveClaim(UUID jobId, UUID sourceId, Instant now) {
        return jdbc.query(
                        """
                        SELECT 1
                        FROM ai.source_indexing_jobs j
                        JOIN ai.document_sources s ON s.id = j.source_id
                        WHERE j.id = ? AND j.source_id = ?
                          AND j.status = 'CLAIMED' AND j.claim_expires_at >= ?
                          AND s.status = 'INDEXING'
                        FOR UPDATE OF j, s
                        """,
                        (resultSet, index) -> 1,
                        jobId,
                        sourceId,
                        Timestamp.from(now))
                .stream()
                .findFirst()
                .isPresent();
    }

    private void addEvent(
            String eventType,
            UUID sourceId,
            long resourceVersion,
            String actorType,
            UUID actorId,
            String result,
            UUID correlationId,
            String status,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO ai.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '2.0', ?, 'AI', ?, ?, 'AI_SOURCE', ?, ?,
                        ?, ?, jsonb_build_object('status', ?), ?)
                """,
                UUID.randomUUID(),
                eventType,
                Timestamp.from(now),
                actorType,
                actorId,
                sourceId,
                resourceVersion,
                result,
                correlationId,
                status,
                Timestamp.from(now));
    }

    private static ClaimedJob claimedJob(ResultSet resultSet) throws SQLException {
        return new ClaimedJob(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("source_id", UUID.class),
                resultSet.getObject("requested_by_user_id", UUID.class),
                resultSet.getObject("correlation_id", UUID.class),
                resultSet.getString("storage_key"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("sha256").trim());
    }

    static String vectorLiteral(float[] embedding) {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) value.append(',');
            value.append(Float.toString(embedding[index]));
        }
        return value.append(']').toString();
    }

    record ClaimedJob(
            UUID jobId,
            UUID sourceId,
            UUID actorId,
            UUID correlationId,
            String storageKey,
            long sizeBytes,
            String sha256) {}

    record IndexedChunk(
            int ordinal,
            String content,
            String sha256,
            int tokenCount,
            int startToken,
            int endToken,
            float[] embedding) {}

    private record SourceState(String status, long version) {}
}

final class AiSourceNotFoundException extends RuntimeException {}

final class AiSourceIndexConflictException extends RuntimeException {}
