ALTER TABLE support.outbox_events
    DROP CONSTRAINT outbox_events_event_version_check,
    ADD CONSTRAINT outbox_events_event_version_check
        CHECK (event_version IN ('1.0', '2.0')) NOT VALID;

ALTER TABLE support.outbox_events
    VALIDATE CONSTRAINT outbox_events_event_version_check;

ALTER TABLE support.outbox_events
    ADD CONSTRAINT outbox_events_audit_v2_check CHECK (
        event_version = '1.0'
        OR (
            actor_type = 'HUMAN_USER'
            AND resource_type = 'TICKET'
            AND result = 'SUCCESS'
            AND (
                (event_type = 'TICKET_CREATED'
                    AND resource_version = 0
                    AND payload = jsonb_build_object(
                        'type', payload->'type',
                        'status', 'OPEN',
                        'priority', 'NORMAL',
                        'source', 'WEB')
                    AND jsonb_typeof(payload->'type') = 'string'
                    AND payload->>'type' IN ('INCIDENT', 'SERVICE_REQUEST', 'QUESTION', 'OTHER'))
                OR (event_type = 'TICKET_COMMENT_ADDED'
                    AND resource_version >= 0
                    AND payload = jsonb_build_object(
                        'commentId', payload->'commentId',
                        'visibility', payload->'visibility')
                    AND jsonb_typeof(payload->'commentId') = 'string'
                    AND payload->>'commentId' ~
                        '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
                    AND jsonb_typeof(payload->'visibility') = 'string'
                    AND payload->>'visibility' IN ('PUBLIC', 'INTERNAL'))
                OR (event_type = 'TICKET_ASSIGNED'
                    AND resource_version >= 1
                    AND payload = jsonb_build_object('assigneeUserId', payload->'assigneeUserId')
                    AND jsonb_typeof(payload->'assigneeUserId') = 'string'
                    AND payload->>'assigneeUserId' =
                        lower(actor_id::text))
                OR (event_type = 'TICKET_PRIORITY_CHANGED'
                    AND resource_version >= 1
                    AND payload = jsonb_build_object(
                        'previousPriority', payload->'previousPriority',
                        'newPriority', payload->'newPriority')
                    AND jsonb_typeof(payload->'previousPriority') = 'string'
                    AND jsonb_typeof(payload->'newPriority') = 'string'
                    AND payload->>'previousPriority' IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
                    AND payload->>'newPriority' IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
                    AND payload->>'previousPriority' <> payload->>'newPriority')
                OR (event_type = 'TICKET_STATUS_CHANGED'
                    AND resource_version >= 1
                    AND payload = jsonb_build_object(
                        'previousStatus', payload->'previousStatus',
                        'newStatus', payload->'newStatus')
                    AND jsonb_typeof(payload->'previousStatus') = 'string'
                    AND jsonb_typeof(payload->'newStatus') = 'string'
                    AND (
                        (payload->>'previousStatus' = 'OPEN'
                            AND payload->>'newStatus' = 'IN_PROGRESS')
                        OR (payload->>'previousStatus' = 'IN_PROGRESS'
                            AND payload->>'newStatus' = 'WAITING_FOR_REQUESTER')
                        OR (payload->>'previousStatus' = 'WAITING_FOR_REQUESTER'
                            AND payload->>'newStatus' = 'IN_PROGRESS')))
                OR (event_type = 'TICKET_RESOLVED'
                    AND resource_version >= 1
                    AND payload = jsonb_build_object(
                        'lifecycleEventId', payload->'lifecycleEventId',
                        'fromStatus', payload->'fromStatus',
                        'toStatus', payload->'toStatus')
                    AND jsonb_typeof(payload->'lifecycleEventId') = 'string'
                    AND payload->>'lifecycleEventId' ~
                        '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
                    AND payload->>'fromStatus' IN ('IN_PROGRESS', 'WAITING_FOR_REQUESTER')
                    AND payload->>'toStatus' = 'RESOLVED')
                OR (event_type = 'TICKET_CLOSED'
                    AND resource_version >= 1
                    AND payload = jsonb_build_object(
                        'lifecycleEventId', payload->'lifecycleEventId',
                        'fromStatus', payload->'fromStatus',
                        'toStatus', payload->'toStatus',
                        'closeReason', payload->'closeReason')
                    AND jsonb_typeof(payload->'lifecycleEventId') = 'string'
                    AND payload->>'lifecycleEventId' ~
                        '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
                    AND payload->>'toStatus' = 'CLOSED'
                    AND (
                        (payload->>'fromStatus' = 'RESOLVED'
                            AND payload->>'closeReason' = 'RESOLVED')
                        OR (payload->>'fromStatus' IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER')
                            AND payload->>'closeReason' IN (
                                'CANCELLED', 'DUPLICATE', 'NOT_APPLICABLE'))))
                OR (event_type = 'TICKET_REOPENED'
                    AND resource_version >= 1
                    AND payload = jsonb_build_object(
                        'lifecycleEventId', payload->'lifecycleEventId',
                        'fromStatus', payload->'fromStatus',
                        'toStatus', payload->'toStatus')
                    AND jsonb_typeof(payload->'lifecycleEventId') = 'string'
                    AND payload->>'lifecycleEventId' ~
                        '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
                    AND payload->>'fromStatus' IN ('RESOLVED', 'CLOSED')
                    AND payload->>'toStatus' = 'OPEN')
            )
        )
    ) NOT VALID;

ALTER TABLE support.outbox_events
    VALIDATE CONSTRAINT outbox_events_audit_v2_check;

CREATE TABLE support.outbox_deliveries (
    event_id support.uuid_v4 NOT NULL
        REFERENCES support.outbox_events(event_id) ON DELETE CASCADE,
    consumer varchar(32) NOT NULL CHECK (consumer = 'AUDIT'),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL CHECK (attempts >= 0 AND attempts <= 10),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    last_error_code varchar(32) NULL
        CHECK (last_error_code IN ('AUDIT_REJECTED', 'AUDIT_CONFLICT', 'AUDIT_UNAVAILABLE')),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, consumer),
    CONSTRAINT support_outbox_deliveries_state_check CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status = 'PENDING' AND delivered_at IS NULL AND attempts < 10)
        OR (status = 'FAILED' AND delivered_at IS NULL AND attempts = 10
            AND last_error_code IS NOT NULL)
    )
);

CREATE INDEX support_outbox_deliveries_due_idx
    ON support.outbox_deliveries (consumer, status, next_attempt_at);

CREATE INDEX support_outbox_events_type_idx
    ON support.outbox_events (event_type, event_version, occurred_at, event_id);
