CREATE DOMAIN identity.uuid_v4 AS uuid
    CHECK ((get_byte(uuid_send(VALUE), 6) >> 4) = 4 AND (get_byte(uuid_send(VALUE), 8) & 192) = 128);

CREATE TABLE identity.human_users (
    id identity.uuid_v4 PRIMARY KEY,
    username varchar(128) NOT NULL,
    normalized_username varchar(128) NOT NULL UNIQUE,
    password_hash varchar(512) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at timestamptz NOT NULL
);

CREATE TABLE identity.roles (
    code varchar(32) PRIMARY KEY CHECK (code IN (
        'SYSTEM_ADMIN', 'IT_OPERATOR', 'SUPPORT_TECHNICIAN', 'END_USER', 'AUDITOR'
    ))
);

INSERT INTO identity.roles (code) VALUES
    ('SYSTEM_ADMIN'),
    ('IT_OPERATOR'),
    ('SUPPORT_TECHNICIAN'),
    ('END_USER'),
    ('AUDITOR');

CREATE TABLE identity.human_user_roles (
    user_id identity.uuid_v4 NOT NULL REFERENCES identity.human_users(id),
    role_code varchar(32) NOT NULL REFERENCES identity.roles(code),
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE identity.human_sessions (
    id identity.uuid_v4 PRIMARY KEY,
    user_id identity.uuid_v4 NOT NULL REFERENCES identity.human_users(id),
    token_hash char(64) NOT NULL UNIQUE,
    csrf_token varchar(512) NOT NULL,
    created_at timestamptz NOT NULL,
    last_activity_at timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    CHECK (last_activity_at >= created_at AND last_activity_at < absolute_expires_at)
);

CREATE INDEX human_sessions_user_id_idx ON identity.human_sessions (user_id);
CREATE INDEX human_sessions_expiry_idx ON identity.human_sessions (absolute_expires_at, last_activity_at);

CREATE TABLE identity.login_attempts (
    id identity.uuid_v4 PRIMARY KEY,
    attempt_key_hash char(64) NOT NULL,
    attempted_at timestamptz NOT NULL
);

CREATE INDEX login_attempts_window_idx
    ON identity.login_attempts (attempt_key_hash, attempted_at);

CREATE TABLE identity.outbox_events (
    event_id identity.uuid_v4 PRIMARY KEY,
    event_type varchar(64) NOT NULL CHECK (event_type IN ('HUMAN_LOGIN_SUCCEEDED', 'HUMAN_LOGIN_FAILED')),
    event_version varchar(16) NOT NULL,
    occurred_at timestamptz NOT NULL,
    producer_module varchar(32) NOT NULL CHECK (producer_module = 'IDENTITY'),
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'HUMAN_USER'),
    actor_id identity.uuid_v4 NULL REFERENCES identity.human_users(id),
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'AUTHENTICATION_ATTEMPT'),
    resource_id identity.uuid_v4 NOT NULL,
    result varchar(16) NOT NULL CHECK (result IN ('SUCCESS', 'DENIED')),
    correlation_id identity.uuid_v4 NOT NULL,
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    CONSTRAINT failed_login_actor_rule CHECK (
        (event_type = 'HUMAN_LOGIN_FAILED' AND result = 'DENIED')
        OR (event_type = 'HUMAN_LOGIN_SUCCEEDED' AND result = 'SUCCESS' AND actor_id IS NOT NULL)
    ),
    CONSTRAINT outbox_delivery_rule CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND delivered_at IS NULL)
    )
);

CREATE INDEX outbox_events_pending_idx
    ON identity.outbox_events (status, next_attempt_at);
