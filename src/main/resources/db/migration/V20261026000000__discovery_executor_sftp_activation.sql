ALTER TABLE discovery.discovery_executors
    DROP CONSTRAINT discovery_executors_status_check,
    DROP CONSTRAINT discovery_executors_version_check,
    ADD COLUMN sftp_username varchar(64) NULL UNIQUE,
    ADD COLUMN activated_at timestamptz NULL,
    ADD CONSTRAINT discovery_executors_status_check CHECK (status IN ('PENDING', 'ACTIVE')),
    ADD CONSTRAINT discovery_executors_version_check CHECK (
        (status = 'PENDING' AND version = 0)
        OR (status = 'ACTIVE' AND version = 1)
    ),
    ADD CONSTRAINT discovery_executors_activation_check CHECK (
        (status = 'PENDING' AND sftp_username IS NULL AND activated_at IS NULL)
        OR (status = 'ACTIVE'
            AND sftp_username = 'discovery-executor-' || id::text
            AND activated_at IS NOT NULL
            AND activated_at >= created_at)
    );

ALTER TABLE discovery.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check,
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN (
            'DISCOVERY_SCOPE_CREATED',
            'DISCOVERY_SCOPE_STATUS_CHANGED',
            'DISCOVERY_EXECUTOR_PRE_REGISTERED',
            'DISCOVERY_EXECUTOR_ACTIVATED'
        )
    ),
    DROP CONSTRAINT outbox_events_payload_check,
    ADD CONSTRAINT outbox_events_payload_check CHECK (
        jsonb_typeof(payload) = 'object'
        AND (
            (event_type = 'DISCOVERY_SCOPE_CREATED'
                AND resource_type = 'AUTHORIZED_SCOPE'
                AND payload = '{"status":"ACTIVE"}'::jsonb)
            OR (event_type = 'DISCOVERY_SCOPE_STATUS_CHANGED'
                AND resource_type = 'AUTHORIZED_SCOPE'
                AND payload = jsonb_build_object(
                    'previousStatus', payload->'previousStatus',
                    'newStatus', payload->'newStatus'
                )
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
        )
    );
