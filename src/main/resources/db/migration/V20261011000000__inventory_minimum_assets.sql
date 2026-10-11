CREATE DOMAIN inventory.uuid_v4 AS uuid
    CHECK ((get_byte(uuid_send(VALUE), 6) >> 4) = 4 AND (get_byte(uuid_send(VALUE), 8) & 192) = 128);

CREATE TABLE inventory.asset_types (
    code varchar(64) PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED'))
);

INSERT INTO inventory.asset_types (code, status) VALUES
    ('DESKTOP', 'ACTIVE'),
    ('LAPTOP', 'ACTIVE'),
    ('SERVER', 'ACTIVE'),
    ('NETWORK_DEVICE', 'ACTIVE'),
    ('PRINTER', 'ACTIVE'),
    ('OTHER', 'ACTIVE');

CREATE TABLE inventory.assets (
    id inventory.uuid_v4 PRIMARY KEY,
    name varchar(256) NOT NULL CHECK (btrim(name) <> ''),
    asset_type varchar(64) NOT NULL REFERENCES inventory.asset_types(code),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (updated_at >= created_at)
);

CREATE TABLE inventory.outbox_events (
    event_id inventory.uuid_v4 PRIMARY KEY,
    event_type varchar(64) NOT NULL CHECK (event_type = 'ASSET_CREATED'),
    event_version varchar(16) NOT NULL CHECK (event_version = '1.0'),
    occurred_at timestamptz NOT NULL,
    producer_module varchar(32) NOT NULL CHECK (producer_module = 'INVENTORY'),
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'HUMAN_USER'),
    actor_id inventory.uuid_v4 NOT NULL,
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'ASSET'),
    resource_id inventory.uuid_v4 NOT NULL REFERENCES inventory.assets(id),
    resource_version bigint NOT NULL CHECK (resource_version >= 0),
    result varchar(16) NOT NULL CHECK (result = 'SUCCESS'),
    correlation_id inventory.uuid_v4 NOT NULL,
    payload jsonb NOT NULL CHECK (payload = '{}'::jsonb),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    CONSTRAINT inventory_outbox_delivery_rule CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND delivered_at IS NULL)
    )
);

CREATE INDEX inventory_outbox_events_pending_idx
    ON inventory.outbox_events (status, next_attempt_at);
