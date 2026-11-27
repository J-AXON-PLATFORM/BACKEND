ALTER TABLE ai.outbox_events
    DROP CONSTRAINT outbox_events_event_version_check,
    DROP CONSTRAINT outbox_events_actor_type_check,
    DROP CONSTRAINT ai_outbox_event_type_check,
    DROP CONSTRAINT ai_outbox_resource_version_check,
    DROP CONSTRAINT outbox_events_result_check,
    DROP CONSTRAINT ai_outbox_payload_check,
    ALTER COLUMN actor_id DROP NOT NULL;

ALTER TABLE ai.outbox_events
    ADD CONSTRAINT ai_outbox_event_version_check CHECK (event_version IN ('1.0', '2.0')),
    ADD CONSTRAINT ai_outbox_actor_type_check CHECK (actor_type IN ('HUMAN_USER', 'SYSTEM')),
    ADD CONSTRAINT ai_outbox_event_type_check CHECK (event_type IN (
        'AI_SOURCE_REGISTERED',
        'AI_SOURCE_INDEXING_REQUESTED',
        'AI_SOURCE_INDEXED',
        'AI_SOURCE_INDEXING_FAILED',
        'AI_SOURCE_DISABLED'
    )),
    ADD CONSTRAINT ai_outbox_resource_version_check CHECK (resource_version >= 0),
    ADD CONSTRAINT ai_outbox_result_check CHECK (result IN ('SUCCESS', 'FAILED')),
    ADD CONSTRAINT ai_outbox_payload_check CHECK (
        jsonb_typeof(payload) = 'object'
        AND (
            (
                event_type <> 'AI_SOURCE_DISABLED'
                AND payload = jsonb_build_object('status', payload->>'status')
                AND payload->>'status' IN ('PENDING', 'INDEXING', 'READY', 'FAILED')
            )
            OR (
                event_type = 'AI_SOURCE_DISABLED'
                AND payload = jsonb_build_object(
                    'previousStatus', payload->>'previousStatus',
                    'newStatus', payload->>'newStatus')
                AND payload->>'previousStatus' IN ('READY', 'FAILED')
                AND payload->>'newStatus' = 'DISABLED'
            )
        )
    ),
    ADD CONSTRAINT ai_outbox_catalog_check CHECK (
        (
            event_version = '1.0'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND result = 'SUCCESS'
            AND event_type IN (
                'AI_SOURCE_REGISTERED',
                'AI_SOURCE_INDEXING_REQUESTED',
                'AI_SOURCE_INDEXED',
                'AI_SOURCE_INDEXING_FAILED',
                'AI_SOURCE_DISABLED'
            )
        )
        OR (
            event_version = '2.0'
            AND event_type = 'AI_SOURCE_REGISTERED'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_version = 0
            AND result = 'SUCCESS'
            AND payload = '{"status":"PENDING"}'::jsonb
        )
        OR (
            event_version = '2.0'
            AND event_type = 'AI_SOURCE_INDEXING_REQUESTED'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_version = 1
            AND result = 'SUCCESS'
            AND payload = '{"status":"INDEXING"}'::jsonb
        )
        OR (
            event_version = '2.0'
            AND event_type = 'AI_SOURCE_INDEXED'
            AND actor_type = 'SYSTEM'
            AND actor_id IS NULL
            AND resource_version = 2
            AND result = 'SUCCESS'
            AND payload = '{"status":"READY"}'::jsonb
        )
        OR (
            event_version = '2.0'
            AND event_type = 'AI_SOURCE_INDEXING_FAILED'
            AND actor_type = 'SYSTEM'
            AND actor_id IS NULL
            AND resource_version = 2
            AND result = 'FAILED'
            AND payload = '{"status":"FAILED"}'::jsonb
        )
    );
