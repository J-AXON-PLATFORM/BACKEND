ALTER TABLE ai.outbox_events
    DROP CONSTRAINT ai_outbox_event_type_check,
    DROP CONSTRAINT ai_outbox_payload_check;

ALTER TABLE ai.outbox_events
    ADD CONSTRAINT ai_outbox_event_type_check CHECK (event_type IN (
        'AI_SOURCE_REGISTERED',
        'AI_SOURCE_INDEXING_REQUESTED',
        'AI_SOURCE_INDEXED',
        'AI_SOURCE_INDEXING_FAILED',
        'AI_SOURCE_DISABLED'
    )),
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
    );
