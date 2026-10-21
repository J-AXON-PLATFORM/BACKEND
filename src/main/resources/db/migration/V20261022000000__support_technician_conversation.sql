ALTER TABLE support.ticket_comments
    DROP CONSTRAINT ticket_comments_visibility_check;

ALTER TABLE support.ticket_comments
    ADD CONSTRAINT ticket_comments_visibility_check
    CHECK (visibility IN ('PUBLIC', 'INTERNAL')) NOT VALID;

ALTER TABLE support.ticket_comments
    VALIDATE CONSTRAINT ticket_comments_visibility_check;

ALTER TABLE support.outbox_events
    DROP CONSTRAINT outbox_events_payload_check;

ALTER TABLE support.outbox_events
    ADD CONSTRAINT outbox_events_payload_check CHECK (
        jsonb_typeof(payload) = 'object'
        AND (
            (
                event_type = 'TICKET_CREATED'
                AND payload - 'type' = '{"status":"OPEN","priority":"NORMAL","source":"WEB"}'::jsonb
                AND payload ? 'type'
                AND jsonb_typeof(payload->'type') = 'string'
                AND payload->>'type' IN ('INCIDENT', 'SERVICE_REQUEST', 'QUESTION', 'OTHER')
            )
            OR (
                event_type = 'TICKET_COMMENT_ADDED'
                AND payload = jsonb_build_object(
                    'commentId', payload->'commentId',
                    'visibility', payload->'visibility'
                )
                AND jsonb_typeof(payload->'commentId') = 'string'
                AND (payload->>'commentId')::support.uuid_v4 IS NOT NULL
                AND jsonb_typeof(payload->'visibility') = 'string'
                AND payload->>'visibility' IN ('PUBLIC', 'INTERNAL')
            )
            OR (
                event_type = 'TICKET_ASSIGNED'
                AND payload = jsonb_build_object('assigneeUserId', payload->'assigneeUserId')
                AND jsonb_typeof(payload->'assigneeUserId') = 'string'
                AND (payload->>'assigneeUserId')::support.uuid_v4 IS NOT NULL
            )
        )
    ) NOT VALID;

ALTER TABLE support.outbox_events
    VALIDATE CONSTRAINT outbox_events_payload_check;
