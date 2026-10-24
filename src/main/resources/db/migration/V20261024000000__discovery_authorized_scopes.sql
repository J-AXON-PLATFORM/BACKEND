CREATE DOMAIN discovery.uuid_v4 AS uuid
    CHECK ((get_byte(uuid_send(VALUE), 6) >> 4) = 4 AND (get_byte(uuid_send(VALUE), 8) & 192) = 128);

CREATE TABLE discovery.authorized_scopes (
    id discovery.uuid_v4 PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    normalized_name varchar(200) NOT NULL CHECK (btrim(normalized_name) <> ''),
    cidr inet NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by_user_id discovery.uuid_v4 NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (normalized_name),
    UNIQUE (cidr),
    CHECK (cidr = network(cidr)),
    CHECK (updated_at >= created_at)
);

CREATE INDEX discovery_authorized_scopes_order_idx
    ON discovery.authorized_scopes (normalized_name ASC, id ASC);

CREATE TABLE discovery.outbox_events (
    event_id discovery.uuid_v4 PRIMARY KEY,
    event_type varchar(64) NOT NULL CHECK (event_type IN (
        'DISCOVERY_SCOPE_CREATED', 'DISCOVERY_SCOPE_STATUS_CHANGED'
    )),
    event_version varchar(16) NOT NULL CHECK (event_version = '1.0'),
    occurred_at timestamptz NOT NULL,
    producer_module varchar(32) NOT NULL CHECK (producer_module = 'DISCOVERY'),
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'HUMAN_USER'),
    actor_id discovery.uuid_v4 NOT NULL,
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'AUTHORIZED_SCOPE'),
    resource_id discovery.uuid_v4 NOT NULL REFERENCES discovery.authorized_scopes(id),
    resource_version bigint NOT NULL CHECK (resource_version >= 0),
    result varchar(16) NOT NULL CHECK (result = 'SUCCESS'),
    correlation_id discovery.uuid_v4 NOT NULL,
    payload jsonb NOT NULL CHECK (
        jsonb_typeof(payload) = 'object'
        AND (
            (event_type = 'DISCOVERY_SCOPE_CREATED' AND payload = '{"status":"ACTIVE"}'::jsonb)
            OR (
                event_type = 'DISCOVERY_SCOPE_STATUS_CHANGED'
                AND payload = jsonb_build_object(
                    'previousStatus', payload->'previousStatus',
                    'newStatus', payload->'newStatus'
                )
                AND jsonb_typeof(payload->'previousStatus') = 'string'
                AND jsonb_typeof(payload->'newStatus') = 'string'
                AND payload->>'previousStatus' IN ('ACTIVE', 'DISABLED')
                AND payload->>'newStatus' IN ('ACTIVE', 'DISABLED')
                AND payload->>'previousStatus' <> payload->>'newStatus'
            )
        )
    ),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    CONSTRAINT discovery_outbox_delivery_rule CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND delivered_at IS NULL)
    )
);

CREATE INDEX discovery_outbox_events_pending_idx
    ON discovery.outbox_events (status, next_attempt_at);
