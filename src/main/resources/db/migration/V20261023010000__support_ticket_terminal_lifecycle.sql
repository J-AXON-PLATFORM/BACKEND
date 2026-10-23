CREATE TABLE support.ticket_lifecycle_events (
    id support.uuid_v4 PRIMARY KEY,
    ticket_id support.uuid_v4 NOT NULL REFERENCES support.tickets(id),
    event_type varchar(16) NOT NULL CHECK (event_type IN ('RESOLVED', 'CLOSED', 'REOPENED')),
    from_status varchar(32) NOT NULL CHECK (from_status IN (
        'OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER', 'RESOLVED', 'CLOSED'
    )),
    to_status varchar(32) NOT NULL CHECK (to_status IN ('OPEN', 'RESOLVED', 'CLOSED')),
    actor_user_id support.uuid_v4 NOT NULL,
    resolution_summary varchar(10000) NULL,
    close_reason varchar(32) NULL CHECK (close_reason IN (
        'RESOLVED', 'CANCELLED', 'DUPLICATE', 'NOT_APPLICABLE'
    )),
    reopen_reason varchar(2000) NULL,
    occurred_at timestamptz NOT NULL,
    resource_version bigint NOT NULL CHECK (resource_version > 0),
    UNIQUE (ticket_id, resource_version),
    CHECK (
        (
            event_type = 'RESOLVED'
            AND from_status IN ('IN_PROGRESS', 'WAITING_FOR_REQUESTER')
            AND to_status = 'RESOLVED'
            AND resolution_summary IS NOT NULL
            AND btrim(resolution_summary) <> ''
            AND close_reason IS NULL
            AND reopen_reason IS NULL
        ) OR (
            event_type = 'CLOSED'
            AND to_status = 'CLOSED'
            AND resolution_summary IS NULL
            AND reopen_reason IS NULL
            AND (
                (from_status = 'RESOLVED' AND close_reason = 'RESOLVED')
                OR (
                    from_status IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER')
                    AND close_reason IN ('CANCELLED', 'DUPLICATE', 'NOT_APPLICABLE')
                )
            )
        ) OR (
            event_type = 'REOPENED'
            AND from_status IN ('RESOLVED', 'CLOSED')
            AND to_status = 'OPEN'
            AND reopen_reason IS NOT NULL
            AND btrim(reopen_reason) <> ''
            AND resolution_summary IS NULL
            AND close_reason IS NULL
        )
    )
);

CREATE INDEX ticket_lifecycle_events_ticket_order_idx
    ON support.ticket_lifecycle_events (ticket_id, occurred_at ASC, id ASC);

CREATE FUNCTION support.reject_ticket_lifecycle_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        MESSAGE = 'support.ticket_lifecycle_events is append-only';
END;
$$;

CREATE TRIGGER ticket_lifecycle_events_append_only
    BEFORE UPDATE OR DELETE ON support.ticket_lifecycle_events
    FOR EACH ROW EXECUTE FUNCTION support.reject_ticket_lifecycle_mutation();

ALTER TABLE support.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check;

ALTER TABLE support.outbox_events
    ADD CONSTRAINT outbox_events_event_type_check
    CHECK (event_type IN (
        'TICKET_CREATED',
        'TICKET_COMMENT_ADDED',
        'TICKET_ASSIGNED',
        'TICKET_PRIORITY_CHANGED',
        'TICKET_STATUS_CHANGED',
        'TICKET_RESOLVED',
        'TICKET_CLOSED',
        'TICKET_REOPENED'
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
            OR (
                event_type IN ('TICKET_RESOLVED', 'TICKET_REOPENED')
                AND payload = jsonb_build_object(
                    'lifecycleEventId', payload->'lifecycleEventId',
                    'fromStatus', payload->'fromStatus',
                    'toStatus', payload->'toStatus'
                )
                AND jsonb_typeof(payload->'lifecycleEventId') = 'string'
                AND (payload->>'lifecycleEventId')::support.uuid_v4 IS NOT NULL
                AND jsonb_typeof(payload->'fromStatus') = 'string'
                AND jsonb_typeof(payload->'toStatus') = 'string'
                AND (
                    (event_type = 'TICKET_RESOLVED'
                        AND payload->>'fromStatus' IN ('IN_PROGRESS', 'WAITING_FOR_REQUESTER')
                        AND payload->>'toStatus' = 'RESOLVED')
                    OR (event_type = 'TICKET_REOPENED'
                        AND payload->>'fromStatus' IN ('RESOLVED', 'CLOSED')
                        AND payload->>'toStatus' = 'OPEN')
                )
            )
            OR (
                event_type = 'TICKET_CLOSED'
                AND payload = jsonb_build_object(
                    'lifecycleEventId', payload->'lifecycleEventId',
                    'fromStatus', payload->'fromStatus',
                    'toStatus', payload->'toStatus',
                    'closeReason', payload->'closeReason'
                )
                AND jsonb_typeof(payload->'lifecycleEventId') = 'string'
                AND (payload->>'lifecycleEventId')::support.uuid_v4 IS NOT NULL
                AND jsonb_typeof(payload->'fromStatus') = 'string'
                AND jsonb_typeof(payload->'toStatus') = 'string'
                AND jsonb_typeof(payload->'closeReason') = 'string'
                AND payload->>'toStatus' = 'CLOSED'
                AND (
                    (payload->>'fromStatus' = 'RESOLVED' AND payload->>'closeReason' = 'RESOLVED')
                    OR (
                        payload->>'fromStatus' IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER')
                        AND payload->>'closeReason' IN ('CANCELLED', 'DUPLICATE', 'NOT_APPLICABLE')
                    )
                )
            )
        )
    ) NOT VALID;

ALTER TABLE support.outbox_events
    VALIDATE CONSTRAINT outbox_events_payload_check;
