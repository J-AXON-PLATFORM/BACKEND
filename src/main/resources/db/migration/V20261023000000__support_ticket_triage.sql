ALTER TABLE support.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check;

ALTER TABLE support.outbox_events
    ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN (
        'TICKET_CREATED',
        'TICKET_COMMENT_ADDED',
        'TICKET_ASSIGNED',
        'TICKET_PRIORITY_CHANGED',
        'TICKET_STATUS_CHANGED'
    )) NOT VALID;

ALTER TABLE support.outbox_events
    VALIDATE CONSTRAINT outbox_events_event_type_check;

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
            OR (
                event_type = 'TICKET_PRIORITY_CHANGED'
                AND payload = jsonb_build_object(
                    'previousPriority', payload->'previousPriority',
                    'newPriority', payload->'newPriority'
                )
                AND jsonb_typeof(payload->'previousPriority') = 'string'
                AND jsonb_typeof(payload->'newPriority') = 'string'
                AND payload->>'previousPriority' IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
                AND payload->>'newPriority' IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
            )
            OR (
                event_type = 'TICKET_STATUS_CHANGED'
                AND payload = jsonb_build_object(
                    'previousStatus', payload->'previousStatus',
                    'newStatus', payload->'newStatus'
                )
                AND jsonb_typeof(payload->'previousStatus') = 'string'
                AND jsonb_typeof(payload->'newStatus') = 'string'
                AND payload->>'previousStatus' IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER')
                AND payload->>'newStatus' IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER')
            )
        )
    ) NOT VALID;

ALTER TABLE support.outbox_events
    VALIDATE CONSTRAINT outbox_events_payload_check;
