ALTER TABLE discovery.outbox_events
    DROP CONSTRAINT outbox_events_event_version_check,
    ADD CONSTRAINT outbox_events_event_version_check CHECK (event_version IN ('1.0', '2.0')) NOT VALID;

ALTER TABLE discovery.outbox_events VALIDATE CONSTRAINT outbox_events_event_version_check;

ALTER TABLE discovery.outbox_events
    DROP CONSTRAINT outbox_events_payload_check;

ALTER TABLE discovery.outbox_events
    ADD CONSTRAINT outbox_events_payload_check CHECK (
        jsonb_typeof(payload) = 'object'
        AND (
            (event_version = '2.0'
                AND event_type = 'DISCOVERY_SCOPE_CREATED'
                AND actor_type = 'HUMAN_USER'
                AND resource_type = 'AUTHORIZED_SCOPE'
                AND resource_version = 0
                AND payload = '{}'::jsonb)
            OR (event_version = '2.0'
                AND event_type = 'DISCOVERY_EXECUTOR_PRE_REGISTERED'
                AND actor_type = 'HUMAN_USER'
                AND resource_type = 'DISCOVERY_EXECUTOR'
                AND resource_version = 0
                AND payload = '{}'::jsonb)
            OR (event_version = '2.0'
                AND event_type = 'DISCOVERY_EXECUTOR_ACTIVATED'
                AND actor_type = 'HUMAN_USER'
                AND resource_type = 'DISCOVERY_EXECUTOR'
                AND resource_version = 1
                AND payload = '{}'::jsonb)
            OR (event_version = '2.0'
                AND event_type = 'DISCOVERY_SCOPE_STATUS_CHANGED'
                AND actor_type = 'HUMAN_USER'
                AND resource_type = 'AUTHORIZED_SCOPE'
                AND resource_version >= 1
                AND payload = jsonb_build_object(
                    'previousStatus', payload->'previousStatus',
                    'newStatus', payload->'newStatus')
                AND jsonb_typeof(payload->'previousStatus') = 'string'
                AND jsonb_typeof(payload->'newStatus') = 'string'
                AND payload->>'previousStatus' IN ('ACTIVE', 'DISABLED')
                AND payload->>'newStatus' IN ('ACTIVE', 'DISABLED')
                AND payload->>'previousStatus' <> payload->>'newStatus')
            OR (event_version = '1.0' AND (
                (event_type = 'DISCOVERY_SCOPE_CREATED'
                    AND resource_type = 'AUTHORIZED_SCOPE'
                    AND payload = '{"status":"ACTIVE"}'::jsonb)
                OR (event_type = 'DISCOVERY_SCOPE_STATUS_CHANGED'
                    AND resource_type = 'AUTHORIZED_SCOPE'
                    AND payload = jsonb_build_object(
                        'previousStatus', payload->'previousStatus',
                        'newStatus', payload->'newStatus')
                    AND jsonb_typeof(payload->'previousStatus') = 'string'
                    AND jsonb_typeof(payload->'newStatus') = 'string'
                    AND payload->>'previousStatus' IN ('ACTIVE', 'DISABLED')
                    AND payload->>'newStatus' IN ('ACTIVE', 'DISABLED')
                    AND payload->>'previousStatus' <> payload->>'newStatus')
                OR (event_type = 'DISCOVERY_EXECUTOR_PRE_REGISTERED'
                    AND resource_type = 'DISCOVERY_EXECUTOR'
                    AND payload = '{"status":"PENDING"}'::jsonb)
                OR (event_type = 'DISCOVERY_EXECUTOR_ACTIVATED'
                    AND resource_type = 'DISCOVERY_EXECUTOR'
                    AND resource_version = 1
                    AND payload = '{"status":"ACTIVE"}'::jsonb)
                OR (event_type = 'DISCOVERY_JOB_CREATED'
                    AND resource_type = 'DISCOVERY_JOB'
                    AND resource_version = 0
                    AND payload = '{"status":"PENDING","profile":"BASIC"}'::jsonb)
                OR (event_type = 'DISCOVERY_JOB_DISPATCHED'
                    AND resource_type = 'DISCOVERY_JOB'
                    AND resource_version = 1
                    AND payload = '{"status":"DISPATCHED"}'::jsonb)
                OR (event_type = 'DISCOVERY_JOB_COMPLETED'
                    AND actor_type = 'DISCOVERY_EXECUTOR'
                    AND resource_type = 'DISCOVERY_JOB'
                    AND resource_version = 2
                    AND payload = jsonb_build_object(
                        'status', payload->'status',
                        'observationsFound', payload->'observationsFound')
                    AND jsonb_typeof(payload->'status') = 'string'
                    AND payload->>'status' IN ('SUCCEEDED', 'FAILED')
                    AND jsonb_typeof(payload->'observationsFound') = 'number')
                OR (event_type = 'DISCOVERED_DEVICE_IGNORED'
                    AND actor_type = 'HUMAN_USER'
                    AND resource_type = 'DISCOVERED_DEVICE'
                    AND resource_version = 1
                    AND payload = '{"newStatus":"IGNORED","previousStatus":"UNREVIEWED"}'::jsonb)
                OR (event_type = 'DISCOVERED_DEVICE_INCORPORATED'
                    AND actor_type = 'HUMAN_USER'
                    AND resource_type = 'DISCOVERED_DEVICE'
                    AND resource_version = 1
                    AND payload = jsonb_build_object(
                        'previousStatus', 'UNREVIEWED',
                        'newStatus', 'INCORPORATED',
                        'assetId', payload->'assetId')
                    AND jsonb_typeof(payload->'assetId') = 'string'
                    AND (payload->>'assetId')::text ~
                        '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
            ))
        )
    ) NOT VALID;

ALTER TABLE discovery.outbox_events
    VALIDATE CONSTRAINT outbox_events_payload_check;

CREATE TABLE discovery.outbox_deliveries (
    event_id discovery.uuid_v4 NOT NULL REFERENCES discovery.outbox_events(event_id) ON DELETE CASCADE,
    consumer varchar(32) NOT NULL CHECK (consumer = 'AUDIT'),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL CHECK (attempts >= 0 AND attempts <= 10),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    last_error_code varchar(32) NULL
        CHECK (last_error_code IN ('AUDIT_REJECTED', 'AUDIT_CONFLICT', 'AUDIT_UNAVAILABLE')),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, consumer),
    CONSTRAINT discovery_outbox_deliveries_state_check CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status = 'PENDING' AND delivered_at IS NULL AND attempts < 10)
        OR (status = 'FAILED' AND delivered_at IS NULL AND attempts = 10
            AND last_error_code IS NOT NULL)
    )
);

CREATE INDEX discovery_outbox_deliveries_due_idx
    ON discovery.outbox_deliveries (consumer, status, next_attempt_at);

CREATE INDEX discovery_outbox_events_type_idx
    ON discovery.outbox_events (event_type, event_version, occurred_at, event_id);
