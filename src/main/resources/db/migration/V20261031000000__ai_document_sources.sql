CREATE DOMAIN ai.uuid_v4 AS uuid
    CHECK ((get_byte(uuid_send(VALUE), 6) >> 4) = 4 AND (get_byte(uuid_send(VALUE), 8) & 192) = 128);

CREATE TABLE ai.document_sources (
    id ai.uuid_v4 PRIMARY KEY,
    name varchar(256) NOT NULL CHECK (btrim(name) <> ''),
    media_type varchar(64) NOT NULL CHECK (media_type = 'text/plain'),
    size_bytes bigint NOT NULL CHECK (size_bytes BETWEEN 0 AND 1048576),
    sha256 char(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    storage_key varchar(64) NOT NULL UNIQUE
        CHECK (storage_key ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.txt$'),
    status varchar(16) NOT NULL CHECK (status = 'PENDING'),
    created_by_user_id ai.uuid_v4 NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version = 0),
    CHECK (updated_at = created_at)
);

CREATE INDEX ai_document_sources_order_idx
    ON ai.document_sources (created_at DESC, id DESC);

CREATE TABLE ai.outbox_events (
    event_id ai.uuid_v4 PRIMARY KEY,
    event_type varchar(64) NOT NULL CHECK (event_type = 'AI_SOURCE_REGISTERED'),
    event_version varchar(16) NOT NULL CHECK (event_version = '1.0'),
    occurred_at timestamptz NOT NULL,
    producer_module varchar(32) NOT NULL CHECK (producer_module = 'AI'),
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'HUMAN_USER'),
    actor_id ai.uuid_v4 NOT NULL,
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'AI_SOURCE'),
    resource_id ai.uuid_v4 NOT NULL REFERENCES ai.document_sources(id),
    resource_version bigint NOT NULL CHECK (resource_version = 0),
    result varchar(16) NOT NULL CHECK (result = 'SUCCESS'),
    correlation_id ai.uuid_v4 NOT NULL,
    payload jsonb NOT NULL CHECK (payload = '{"status":"PENDING"}'::jsonb),
    status varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    CONSTRAINT ai_outbox_delivery_rule CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND delivered_at IS NULL)
    )
);

CREATE INDEX ai_outbox_events_pending_idx
    ON ai.outbox_events (status, next_attempt_at);
