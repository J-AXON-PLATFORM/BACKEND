ALTER TABLE ai.document_sources
    DROP CONSTRAINT document_sources_status_check,
    DROP CONSTRAINT document_sources_version_check,
    DROP CONSTRAINT document_sources_check;

ALTER TABLE ai.document_sources
    ADD CONSTRAINT ai_document_sources_status_check
        CHECK (status IN ('PENDING', 'INDEXING', 'READY', 'FAILED', 'DISABLED')),
    ADD CONSTRAINT ai_document_sources_version_check CHECK (version >= 0),
    ADD CONSTRAINT ai_document_sources_updated_check CHECK (updated_at >= created_at);

CREATE TABLE ai.source_indexing_jobs (
    id ai.uuid_v4 PRIMARY KEY,
    source_id ai.uuid_v4 NOT NULL UNIQUE REFERENCES ai.document_sources(id),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'CLAIMED', 'SUCCEEDED', 'FAILED')),
    requested_by_user_id ai.uuid_v4 NOT NULL,
    correlation_id ai.uuid_v4 NOT NULL,
    created_at timestamptz NOT NULL,
    claimed_at timestamptz NULL,
    claim_expires_at timestamptz NULL,
    completed_at timestamptz NULL,
    failure_code varchar(64) NULL,
    CONSTRAINT ai_source_indexing_jobs_state_check CHECK (
        (status = 'PENDING' AND claimed_at IS NULL AND claim_expires_at IS NULL
            AND completed_at IS NULL AND failure_code IS NULL)
        OR (status = 'CLAIMED' AND claimed_at IS NOT NULL AND claim_expires_at IS NOT NULL
            AND completed_at IS NULL AND failure_code IS NULL)
        OR (status = 'SUCCEEDED' AND claimed_at IS NOT NULL AND claim_expires_at IS NULL
            AND completed_at IS NOT NULL AND failure_code IS NULL)
        OR (status = 'FAILED' AND completed_at IS NOT NULL AND failure_code IS NOT NULL)
    )
);

CREATE INDEX ai_source_indexing_jobs_claim_idx
    ON ai.source_indexing_jobs (status, created_at, id);
CREATE INDEX ai_source_indexing_jobs_recovery_idx
    ON ai.source_indexing_jobs (claim_expires_at)
    WHERE status = 'CLAIMED';

CREATE TABLE ai.source_chunks (
    id ai.uuid_v4 PRIMARY KEY,
    source_id ai.uuid_v4 NOT NULL REFERENCES ai.document_sources(id),
    ordinal integer NOT NULL CHECK (ordinal >= 0),
    content text NOT NULL CHECK (content <> ''),
    content_sha256 char(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    token_count integer NOT NULL CHECK (token_count BETWEEN 1 AND 480),
    start_token integer NOT NULL CHECK (start_token >= 0),
    end_token integer NOT NULL CHECK (end_token > start_token),
    embedding vector(384) NOT NULL,
    model_id varchar(128) NOT NULL,
    model_revision char(40) NOT NULL CHECK (model_revision ~ '^[0-9a-f]{40}$'),
    tokenizer_sha256 char(64) NOT NULL CHECK (tokenizer_sha256 ~ '^[0-9a-f]{64}$'),
    chunker_version varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (source_id, ordinal),
    CHECK (end_token - start_token = token_count)
);

CREATE INDEX ai_source_chunks_source_idx ON ai.source_chunks (source_id, ordinal);
CREATE INDEX ai_source_chunks_embedding_hnsw_idx
    ON ai.source_chunks USING hnsw (embedding vector_cosine_ops);

ALTER TABLE ai.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check,
    DROP CONSTRAINT outbox_events_resource_version_check,
    DROP CONSTRAINT outbox_events_payload_check;

ALTER TABLE ai.outbox_events
    ADD CONSTRAINT ai_outbox_event_type_check CHECK (event_type IN (
        'AI_SOURCE_REGISTERED',
        'AI_SOURCE_INDEXING_REQUESTED',
        'AI_SOURCE_INDEXED',
        'AI_SOURCE_INDEXING_FAILED'
    )),
    ADD CONSTRAINT ai_outbox_resource_version_check CHECK (resource_version >= 0),
    ADD CONSTRAINT ai_outbox_payload_check CHECK (
        jsonb_typeof(payload) = 'object'
        AND payload = jsonb_build_object('status', payload->>'status')
        AND payload->>'status' IN ('PENDING', 'INDEXING', 'READY', 'FAILED')
    );
