CREATE TABLE support.ticket_comments (
    id support.uuid_v4 PRIMARY KEY,
    ticket_id support.uuid_v4 NOT NULL REFERENCES support.tickets(id),
    visibility varchar(16) NOT NULL CHECK (visibility = 'PUBLIC'),
    author_user_id support.uuid_v4 NOT NULL,
    body varchar(10000) NOT NULL CHECK (btrim(body) <> ''),
    created_at timestamptz NOT NULL
);

CREATE INDEX support_ticket_comments_ticket_created_idx
    ON support.ticket_comments (ticket_id, created_at ASC, id ASC);

ALTER TABLE support.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check;

ALTER TABLE support.outbox_events
    ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN ('TICKET_CREATED', 'TICKET_COMMENT_ADDED')) NOT VALID;

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
                    'visibility', 'PUBLIC'
                )
                AND jsonb_typeof(payload->'commentId') = 'string'
                AND (payload->>'commentId')::support.uuid_v4 IS NOT NULL
            )
        )
    ) NOT VALID;

ALTER TABLE support.outbox_events
    VALIDATE CONSTRAINT outbox_events_payload_check;
