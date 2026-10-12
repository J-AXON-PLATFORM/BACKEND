CREATE DOMAIN collector.uuid_v4 AS uuid
    CHECK ((get_byte(uuid_send(VALUE), 6) >> 4) = 4 AND (get_byte(uuid_send(VALUE), 8) & 192) = 128);

CREATE TABLE collector.agents (
    id collector.uuid_v4 PRIMARY KEY,
    asset_id collector.uuid_v4 NOT NULL,
    status varchar(16) NOT NULL CHECK (status = 'PENDING'),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (updated_at >= created_at)
);

CREATE INDEX collector_agents_asset_id_idx ON collector.agents (asset_id);

CREATE TABLE collector.outbox_events (
    event_id collector.uuid_v4 PRIMARY KEY,
    event_type varchar(64) NOT NULL CHECK (event_type = 'AGENT_PRE_REGISTERED'),
    event_version varchar(16) NOT NULL CHECK (event_version = '1.0'),
    occurred_at timestamptz NOT NULL,
    producer_module varchar(32) NOT NULL CHECK (producer_module = 'COLLECTOR'),
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'HUMAN_USER'),
    actor_id collector.uuid_v4 NOT NULL,
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'AGENT'),
    resource_id collector.uuid_v4 NOT NULL REFERENCES collector.agents(id),
    resource_version bigint NOT NULL CHECK (resource_version >= 0),
    result varchar(16) NOT NULL CHECK (result = 'SUCCESS'),
    correlation_id collector.uuid_v4 NOT NULL,
    payload jsonb NOT NULL CHECK (payload = '{}'::jsonb),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    CONSTRAINT collector_outbox_delivery_rule CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND delivered_at IS NULL)
    )
);

CREATE INDEX collector_outbox_events_pending_idx
    ON collector.outbox_events (status, next_attempt_at);

CREATE TABLE identity.technical_identities (
    id identity.uuid_v4 PRIMARY KEY,
    agent_id identity.uuid_v4 NOT NULL UNIQUE,
    identity_type varchar(32) NOT NULL CHECK (identity_type = 'AGENT'),
    public_key varchar(1024) NULL,
    public_key_fingerprint varchar(128) NULL UNIQUE,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK ((public_key IS NULL) = (public_key_fingerprint IS NULL)),
    CHECK (updated_at >= created_at)
);

ALTER TABLE identity.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check,
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN ('HUMAN_LOGIN_SUCCEEDED', 'HUMAN_LOGIN_FAILED', 'AGENT_PUBLIC_KEY_REGISTERED')
    ),
    DROP CONSTRAINT outbox_events_resource_type_check,
    ADD CONSTRAINT outbox_events_resource_type_check CHECK (
        resource_type IN ('AUTHENTICATION_ATTEMPT', 'AGENT_TECHNICAL_IDENTITY')
    ),
    DROP CONSTRAINT failed_login_actor_rule,
    ADD CONSTRAINT identity_outbox_event_rule CHECK (
        (event_type = 'HUMAN_LOGIN_FAILED' AND result = 'DENIED'
            AND resource_type = 'AUTHENTICATION_ATTEMPT')
        OR (event_type = 'HUMAN_LOGIN_SUCCEEDED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'AUTHENTICATION_ATTEMPT')
        OR (event_type = 'AGENT_PUBLIC_KEY_REGISTERED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'AGENT_TECHNICAL_IDENTITY')
    );
