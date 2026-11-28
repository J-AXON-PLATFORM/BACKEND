CREATE DOMAIN automation.uuid_v4 AS uuid
    CHECK ((get_byte(uuid_send(VALUE), 6) >> 4) = 4
        AND (get_byte(uuid_send(VALUE), 8) & 192) = 128);

CREATE TABLE automation.rules (
    rule_id automation.uuid_v4 PRIMARY KEY,
    rule_code varchar(96) NOT NULL UNIQUE
        CHECK (rule_code = 'SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER'),
    status varchar(16) NOT NULL CHECK (status = 'ACTIVE'),
    trigger_module varchar(32) NOT NULL CHECK (trigger_module = 'SUPPORT'),
    trigger_event_type varchar(64) NOT NULL CHECK (trigger_event_type = 'TICKET_STATUS_CHANGED'),
    trigger_event_version varchar(16) NOT NULL CHECK (trigger_event_version = '2.0'),
    previous_status varchar(32) NOT NULL CHECK (previous_status = 'IN_PROGRESS'),
    new_status varchar(32) NOT NULL CHECK (new_status = 'WAITING_FOR_REQUESTER'),
    action_type varchar(32) NOT NULL CHECK (action_type = 'IN_APP_NOTIFICATION'),
    version bigint NOT NULL CHECK (version = 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

INSERT INTO automation.rules (
    rule_id, rule_code, status, trigger_module, trigger_event_type,
    trigger_event_version, previous_status, new_status, action_type,
    version, created_at, updated_at)
VALUES (
    'a0000000-0000-4000-8000-000000000001',
    'SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER',
    'ACTIVE', 'SUPPORT', 'TICKET_STATUS_CHANGED', '2.0',
    'IN_PROGRESS', 'WAITING_FOR_REQUESTER', 'IN_APP_NOTIFICATION',
    0, TIMESTAMPTZ '2026-12-02 00:00:00+00', TIMESTAMPTZ '2026-12-02 00:00:00+00');

CREATE TABLE automation.executions (
    execution_id automation.uuid_v4 PRIMARY KEY,
    rule_id automation.uuid_v4 NOT NULL REFERENCES automation.rules(rule_id),
    source_event_id automation.uuid_v4 NOT NULL,
    source_event_type varchar(64) NOT NULL CHECK (source_event_type = 'TICKET_STATUS_CHANGED'),
    source_event_version varchar(16) NOT NULL CHECK (source_event_version = '2.0'),
    source_occurred_at timestamptz NOT NULL,
    producer_module varchar(32) NOT NULL CHECK (producer_module = 'SUPPORT'),
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'TICKET'),
    resource_id automation.uuid_v4 NOT NULL,
    resource_version bigint NOT NULL CHECK (resource_version >= 1),
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'HUMAN_USER'),
    actor_id automation.uuid_v4 NOT NULL,
    correlation_id automation.uuid_v4 NOT NULL,
    recipient_user_id automation.uuid_v4 NOT NULL,
    previous_status varchar(32) NOT NULL CHECK (previous_status = 'IN_PROGRESS'),
    new_status varchar(32) NOT NULL CHECK (new_status = 'WAITING_FOR_REQUESTER'),
    status varchar(16) NOT NULL CHECK (
        status IN ('PENDING', 'DISPATCHED', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    attempts integer NOT NULL CHECK (attempts >= 0 AND attempts <= 10),
    next_attempt_at timestamptz NOT NULL,
    expires_at timestamptz NULL CHECK (expires_at IS NULL),
    completed_at timestamptz NULL,
    last_error_code varchar(32) NULL CHECK (
        last_error_code IS NULL OR last_error_code = 'NOTIFICATION_PERSISTENCE_FAILED'),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (rule_id, source_event_id),
    CONSTRAINT automation_execution_state_check CHECK (
        (status = 'PENDING' AND attempts < 10 AND completed_at IS NULL)
        OR (status = 'SUCCEEDED' AND completed_at IS NOT NULL AND last_error_code IS NULL)
        OR (status = 'FAILED' AND attempts = 10
            AND completed_at IS NOT NULL AND last_error_code IS NOT NULL)
        OR (status IN ('DISPATCHED', 'EXPIRED'))
    )
);

CREATE INDEX automation_executions_pending_idx
    ON automation.executions (status, next_attempt_at, created_at, execution_id);

CREATE TABLE automation.notifications (
    notification_id automation.uuid_v4 PRIMARY KEY,
    execution_id automation.uuid_v4 NOT NULL UNIQUE
        REFERENCES automation.executions(execution_id),
    recipient_user_id automation.uuid_v4 NOT NULL,
    notification_type varchar(64) NOT NULL
        CHECK (notification_type = 'TICKET_WAITING_FOR_REQUESTER'),
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'TICKET'),
    resource_id automation.uuid_v4 NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('UNREAD', 'READ')),
    version bigint NOT NULL CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    read_at timestamptz NULL,
    CONSTRAINT automation_notification_state_check CHECK (
        (status = 'UNREAD' AND version = 0 AND read_at IS NULL)
        OR (status = 'READ' AND version = 1 AND read_at IS NOT NULL)
    )
);

CREATE INDEX automation_notifications_recipient_idx
    ON automation.notifications (
        recipient_user_id, status, created_at DESC, notification_id DESC);

CREATE TABLE automation.outbox_events (
    event_id automation.uuid_v4 PRIMARY KEY,
    event_type varchar(64) NOT NULL CHECK (
        event_type IN ('AUTOMATION_EXECUTION_SUCCEEDED', 'AUTOMATION_EXECUTION_FAILED')),
    event_version varchar(16) NOT NULL CHECK (event_version = '1.0'),
    occurred_at timestamptz NOT NULL,
    producer_module varchar(32) NOT NULL CHECK (producer_module = 'AUTOMATION'),
    actor_type varchar(32) NOT NULL CHECK (actor_type = 'AUTOMATION'),
    actor_id automation.uuid_v4 NOT NULL,
    resource_type varchar(64) NOT NULL CHECK (resource_type = 'AUTOMATION_EXECUTION'),
    resource_id automation.uuid_v4 NOT NULL,
    resource_version bigint NOT NULL CHECK (resource_version = 1),
    result varchar(16) NOT NULL CHECK (result IN ('SUCCESS', 'FAILED')),
    correlation_id automation.uuid_v4 NOT NULL,
    payload jsonb NOT NULL CHECK (
        jsonb_typeof(payload) = 'object'
        AND octet_length(payload::text) <= 4096),
    next_attempt_at timestamptz NOT NULL,
    CONSTRAINT automation_outbox_catalog_check CHECK (
        (event_type = 'AUTOMATION_EXECUTION_SUCCEEDED'
            AND result = 'SUCCESS'
            AND payload = '{"actionType":"IN_APP_NOTIFICATION","ruleCode":"SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER"}'::jsonb)
        OR (event_type = 'AUTOMATION_EXECUTION_FAILED'
            AND result = 'FAILED'
            AND payload = '{"actionType":"IN_APP_NOTIFICATION","failureCode":"NOTIFICATION_PERSISTENCE_FAILED","ruleCode":"SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER"}'::jsonb)
    )
);

CREATE INDEX automation_outbox_events_type_idx
    ON automation.outbox_events (event_type, event_version, occurred_at, event_id);

CREATE TABLE automation.outbox_deliveries (
    event_id automation.uuid_v4 NOT NULL
        REFERENCES automation.outbox_events(event_id) ON DELETE CASCADE,
    consumer varchar(32) NOT NULL CHECK (consumer = 'AUDIT'),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL CHECK (attempts >= 0 AND attempts <= 10),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    last_error_code varchar(32) NULL CHECK (
        last_error_code IS NULL OR last_error_code IN (
            'AUDIT_REJECTED', 'AUDIT_CONFLICT', 'AUDIT_UNAVAILABLE')),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, consumer),
    CONSTRAINT automation_outbox_delivery_state_check CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status = 'PENDING' AND delivered_at IS NULL AND attempts < 10)
        OR (status = 'FAILED' AND delivered_at IS NULL AND attempts = 10
            AND last_error_code IS NOT NULL)
    )
);

CREATE INDEX automation_outbox_deliveries_due_idx
    ON automation.outbox_deliveries (consumer, status, next_attempt_at);
