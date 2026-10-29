ALTER TABLE discovery.discovered_devices
    ADD COLUMN inventory_asset_id discovery.uuid_v4 NULL;

ALTER TABLE discovery.discovered_devices
    DROP CONSTRAINT discovered_devices_status_check;

ALTER TABLE discovery.discovered_devices
    ADD CONSTRAINT discovered_devices_status_check
    CHECK (status IN ('UNREVIEWED', 'IGNORED', 'INCORPORATED')) NOT VALID;

ALTER TABLE discovery.discovered_devices
    VALIDATE CONSTRAINT discovered_devices_status_check;

ALTER TABLE discovery.discovered_devices
    DROP CONSTRAINT discovered_devices_review_check;

ALTER TABLE discovery.discovered_devices
    ADD CONSTRAINT discovered_devices_review_check CHECK (
        (status = 'UNREVIEWED'
            AND version = 0
            AND reviewed_at IS NULL
            AND reviewed_by_user_id IS NULL
            AND inventory_asset_id IS NULL)
        OR (status = 'IGNORED'
            AND version = 1
            AND reviewed_at IS NOT NULL
            AND reviewed_by_user_id IS NOT NULL
            AND inventory_asset_id IS NULL
            AND reviewed_at >= created_at)
        OR (status = 'INCORPORATED'
            AND version = 1
            AND reviewed_at IS NOT NULL
            AND reviewed_by_user_id IS NOT NULL
            AND inventory_asset_id IS NOT NULL
            AND reviewed_at >= created_at)
    ) NOT VALID;

ALTER TABLE discovery.discovered_devices
    VALIDATE CONSTRAINT discovered_devices_review_check;

ALTER TABLE discovery.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check;

ALTER TABLE discovery.outbox_events
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN (
            'DISCOVERY_SCOPE_CREATED',
            'DISCOVERY_SCOPE_STATUS_CHANGED',
            'DISCOVERY_EXECUTOR_PRE_REGISTERED',
            'DISCOVERY_EXECUTOR_ACTIVATED',
            'DISCOVERY_JOB_CREATED',
            'DISCOVERY_JOB_DISPATCHED',
            'DISCOVERY_JOB_COMPLETED',
            'DISCOVERED_DEVICE_IGNORED',
            'DISCOVERED_DEVICE_INCORPORATED'
        )
    ) NOT VALID;

ALTER TABLE discovery.outbox_events
    VALIDATE CONSTRAINT outbox_events_event_type_check;

ALTER TABLE discovery.outbox_events
    DROP CONSTRAINT outbox_events_payload_check;

ALTER TABLE discovery.outbox_events
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
        )
    ) NOT VALID;

ALTER TABLE discovery.outbox_events
    VALIDATE CONSTRAINT outbox_events_payload_check;
