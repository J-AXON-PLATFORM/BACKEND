CREATE DOMAIN support.uuid_v4 AS uuid
    CHECK ((get_byte(uuid_send(VALUE), 6) >> 4) = 4 AND (get_byte(uuid_send(VALUE), 8) & 192) = 128);

CREATE TABLE support.tickets (
    id support.uuid_v4 PRIMARY KEY,
    type varchar(32) NOT NULL CHECK (type IN ('INCIDENT', 'SERVICE_REQUEST', 'QUESTION', 'OTHER')),
    summary varchar(256) NOT NULL CHECK (btrim(summary) <> ''),
    description varchar(10000) NOT NULL CHECK (btrim(description) <> ''),
    status varchar(32) NOT NULL DEFAULT 'OPEN' CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER', 'RESOLVED', 'CLOSED'
    )),
    priority varchar(16) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    source varchar(16) NOT NULL DEFAULT 'WEB' CHECK (source = 'WEB'),
    requester_user_id support.uuid_v4 NOT NULL,
    created_by_user_id support.uuid_v4 NOT NULL,
    assignee_user_id support.uuid_v4 NULL,
    asset_id support.uuid_v4 NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (updated_at >= created_at)
);

CREATE INDEX support_tickets_requester_created_idx
    ON support.tickets (requester_user_id, created_at DESC, id DESC);

CREATE TABLE support.outbox_events (
    event_id support.uuid_v4 PRIMARY KEY,
    event_type varchar(64) NOT NULL CHECK (event_type = 'TICKET_CREATED'),
    event_version varchar(16) NOT NULL CHECK (event_version = '1.0'),
    occurred_at timestamptz NOT NULL,
    producer_module varchar(32) NOT NULL CHECK (producer_module = 'SUPPORT'),
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'HUMAN_USER'),
    actor_id support.uuid_v4 NOT NULL,
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'TICKET'),
    resource_id support.uuid_v4 NOT NULL REFERENCES support.tickets(id),
    resource_version bigint NOT NULL CHECK (resource_version >= 0),
    result varchar(16) NOT NULL CHECK (result = 'SUCCESS'),
    correlation_id support.uuid_v4 NOT NULL,
    payload jsonb NOT NULL CHECK (
        jsonb_typeof(payload) = 'object'
        AND payload - 'type' = '{"status":"OPEN","priority":"NORMAL","source":"WEB"}'::jsonb
        AND payload ? 'type'
        AND jsonb_typeof(payload->'type') = 'string'
        AND payload->>'type' IN ('INCIDENT', 'SERVICE_REQUEST', 'QUESTION', 'OTHER')
    ),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    CONSTRAINT support_outbox_delivery_rule CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND delivered_at IS NULL)
    )
);

CREATE INDEX support_outbox_events_pending_idx
    ON support.outbox_events (status, next_attempt_at);
