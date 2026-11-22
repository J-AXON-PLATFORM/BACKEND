package com.jaxon.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exact, read-only vector retrieval over READY sources.
 *
 * <p>The eligibility filter (source READY and chunk produced by the current model, revision and
 * tokenizer) is part of the same statement that ranks the chunks. The materialized CTE forces an
 * exact cosine-distance scan, so the deterministic order {@code similarity DESC, source_id, ordinal} is
 * total and never depends on approximate index traversal or on post-filtering a truncated
 * candidate list.
 */
@Component
class AiRetrievalPersistence {

    private final JdbcTemplate jdbc;

    AiRetrievalPersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    List<AiRetrievalResult> search(float[] queryEmbedding, int limit) {
        String vector = AiIndexingPersistence.vectorLiteral(queryEmbedding);
        return jdbc.query(
                """
                WITH scored AS MATERIALIZED (
                    SELECT c.id, c.source_id, c.ordinal, c.embedding <=> ?::vector AS distance
                    FROM ai.source_chunks c
                    JOIN ai.document_sources s ON s.id = c.source_id
                    WHERE s.status = 'READY'
                      AND c.model_id = ?
                      AND c.model_revision = ?
                      AND c.tokenizer_sha256 = ?
                ), ranked AS (
                    SELECT id, source_id, ordinal,
                           GREATEST(-1.0::float8, LEAST(1.0::float8, 1.0::float8 - distance)) AS similarity
                    FROM scored
                    WHERE distance IS NOT NULL
                      AND distance <> 'NaN'::float8
                      AND distance > '-Infinity'::float8
                      AND distance < 'Infinity'::float8
                    ORDER BY similarity DESC, source_id ASC, ordinal ASC
                    LIMIT ?
                )
                SELECT r.source_id, s.name, s.sha256, r.id AS chunk_id, r.ordinal,
                       c.start_token, c.end_token, c.content_sha256, c.content, r.similarity
                FROM ranked r
                JOIN ai.source_chunks c ON c.id = r.id
                JOIN ai.document_sources s ON s.id = r.source_id AND s.status = 'READY'
                ORDER BY r.similarity DESC, r.source_id ASC, r.ordinal ASC
                """,
                (resultSet, index) -> new AiRetrievalResult(
                        resultSet.getObject("source_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("sha256").trim(),
                        resultSet.getObject("chunk_id", UUID.class),
                        resultSet.getInt("ordinal"),
                        resultSet.getInt("start_token"),
                        resultSet.getInt("end_token"),
                        resultSet.getString("content_sha256").trim(),
                        resultSet.getString("content"),
                        finite(resultSet.getDouble("similarity"))),
                vector,
                EmbeddingProvider.MODEL_ID,
                EmbeddingProvider.MODEL_REVISION,
                EmbeddingProvider.VOCABULARY_DIGEST,
                limit);
    }

    static double finite(double similarity) {
        if (!Double.isFinite(similarity) || similarity < -1.0 || similarity > 1.0) {
            throw new IllegalStateException("Invalid similarity");
        }
        return similarity;
    }
}
