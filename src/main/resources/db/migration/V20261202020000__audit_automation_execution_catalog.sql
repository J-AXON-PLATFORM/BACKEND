ALTER TABLE audit_reporting.audit_events
    DROP CONSTRAINT audit_events_event_type_check,
    ADD CONSTRAINT audit_events_event_type_check CHECK (
        event_type IN (
            'AI_SOURCE_REGISTERED',
            'AI_SOURCE_INDEXING_REQUESTED',
            'AI_SOURCE_INDEXED',
            'AI_SOURCE_INDEXING_FAILED',
            'AI_SOURCE_DISABLED',
            'HUMAN_USER_CREATED',
            'HUMAN_LOGIN_FAILED',
            'HUMAN_LOGIN_SUCCEEDED',
            'HUMAN_LOGOUT_SUCCEEDED',
            'AGENT_PUBLIC_KEY_REGISTERED',
            'DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED',
            'ASSET_CREATED',
            'AGENT_PRE_REGISTERED',
            'AGENT_ACTIVATED',
            'DISCOVERY_SCOPE_CREATED',
            'DISCOVERY_SCOPE_STATUS_CHANGED',
            'DISCOVERY_EXECUTOR_PRE_REGISTERED',
            'DISCOVERY_EXECUTOR_ACTIVATED',
            'DISCOVERY_JOB_CREATED',
            'DISCOVERY_JOB_DISPATCHED',
            'DISCOVERY_JOB_COMPLETED',
            'DISCOVERED_DEVICE_IGNORED',
            'DISCOVERED_DEVICE_INCORPORATED',
            'TICKET_CREATED',
            'TICKET_COMMENT_ADDED',
            'TICKET_ASSIGNED',
            'TICKET_PRIORITY_CHANGED',
            'TICKET_STATUS_CHANGED',
            'TICKET_RESOLVED',
            'TICKET_CLOSED',
            'TICKET_REOPENED',
            'REPORT_EXPORTED',
            'AUTOMATION_EXECUTION_SUCCEEDED',
            'AUTOMATION_EXECUTION_FAILED')
    ),
    DROP CONSTRAINT audit_events_catalog_check;

ALTER TABLE audit_reporting.audit_events
    ADD CONSTRAINT audit_events_catalog_check CHECK (
        (
            event_type = 'AI_SOURCE_REGISTERED'
            AND event_version = '2.0'
            AND producer_module = 'AI'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AI_SOURCE'
            AND resource_version = 0
            AND result = 'SUCCESS'
            AND metadata = '{"status":"PENDING"}'::jsonb
        )
        OR (
            event_type = 'AI_SOURCE_INDEXING_REQUESTED'
            AND event_version = '2.0'
            AND producer_module = 'AI'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AI_SOURCE'
            AND resource_version = 1
            AND result = 'SUCCESS'
            AND metadata = '{"status":"INDEXING"}'::jsonb
        )
        OR (
            event_type = 'AI_SOURCE_INDEXED'
            AND event_version = '2.0'
            AND producer_module = 'AI'
            AND actor_type = 'SYSTEM'
            AND actor_id IS NULL
            AND resource_type = 'AI_SOURCE'
            AND resource_version = 2
            AND result = 'SUCCESS'
            AND metadata = '{"status":"READY"}'::jsonb
        )
        OR (
            event_type = 'AI_SOURCE_INDEXING_FAILED'
            AND event_version = '2.0'
            AND producer_module = 'AI'
            AND actor_type = 'SYSTEM'
            AND actor_id IS NULL
            AND resource_type = 'AI_SOURCE'
            AND resource_version = 2
            AND result = 'FAILED'
            AND metadata = '{"status":"FAILED"}'::jsonb
        )
        OR (
            event_type = 'AI_SOURCE_DISABLED'
            AND event_version = '1.0'
            AND producer_module = 'AI'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AI_SOURCE'
            AND resource_version IS NOT NULL AND resource_version >= 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'previousStatus', metadata->>'previousStatus',
                'newStatus', metadata->>'newStatus')
            AND metadata->>'previousStatus' IN ('READY', 'FAILED')
            AND metadata->>'newStatus' = 'DISABLED'
        )
        OR (
            event_type = 'HUMAN_USER_CREATED'
            AND event_version = '2.0'
            AND producer_module = 'IDENTITY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'HUMAN_USER'
            AND resource_version IS NULL
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'status', metadata->>'status',
                'roles', metadata->>'roles')
            AND metadata->>'status' = 'ACTIVE'
            AND metadata->>'roles' ~
                '^(AUDITOR(\|END_USER)?(\|IT_OPERATOR)?(\|SUPPORT_TECHNICIAN)?(\|SYSTEM_ADMIN)?|END_USER(\|IT_OPERATOR)?(\|SUPPORT_TECHNICIAN)?(\|SYSTEM_ADMIN)?|IT_OPERATOR(\|SUPPORT_TECHNICIAN)?(\|SYSTEM_ADMIN)?|SUPPORT_TECHNICIAN(\|SYSTEM_ADMIN)?|SYSTEM_ADMIN)$'
        )
        OR (
            event_type = 'HUMAN_LOGIN_FAILED'
            AND event_version = '2.0'
            AND producer_module = 'IDENTITY'
            AND actor_type = 'HUMAN_USER'
            AND resource_type = 'AUTHENTICATION_ATTEMPT'
            AND resource_version IS NULL
            AND result = 'DENIED'
            AND metadata = '{"reason":"INVALID_CREDENTIALS"}'::jsonb
        )
        OR (
            event_type = 'HUMAN_LOGIN_SUCCEEDED'
            AND event_version = '2.0'
            AND producer_module = 'IDENTITY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AUTHENTICATION_ATTEMPT'
            AND resource_version IS NULL
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'HUMAN_LOGOUT_SUCCEEDED'
            AND event_version = '2.0'
            AND producer_module = 'IDENTITY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'HUMAN_SESSION'
            AND resource_version IS NULL
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'AGENT_PUBLIC_KEY_REGISTERED'
            AND event_version = '2.0'
            AND producer_module = 'IDENTITY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AGENT_TECHNICAL_IDENTITY'
            AND resource_version IS NULL
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED'
            AND event_version = '2.0'
            AND producer_module = 'IDENTITY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY'
            AND resource_version IS NULL
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'ASSET_CREATED'
            AND event_version = '2.0'
            AND producer_module = 'INVENTORY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'ASSET'
            AND resource_version = 0
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'AGENT_PRE_REGISTERED'
            AND event_version = '2.0'
            AND producer_module = 'COLLECTOR'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AGENT'
            AND resource_version = 0
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'AGENT_ACTIVATED'
            AND event_version = '2.0'
            AND producer_module = 'COLLECTOR'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AGENT'
            AND resource_version = 1
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'DISCOVERY_SCOPE_CREATED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AUTHORIZED_SCOPE'
            AND resource_version = 0
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'DISCOVERY_SCOPE_STATUS_CHANGED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'AUTHORIZED_SCOPE'
            AND resource_version IS NOT NULL AND resource_version >= 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'previousStatus', metadata->>'previousStatus',
                'newStatus', metadata->>'newStatus')
            AND metadata->>'previousStatus' IN ('ACTIVE', 'DISABLED')
            AND metadata->>'newStatus' IN ('ACTIVE', 'DISABLED')
            AND metadata->>'previousStatus' <> metadata->>'newStatus'
        )
        OR (
            event_type = 'DISCOVERY_EXECUTOR_PRE_REGISTERED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'DISCOVERY_EXECUTOR'
            AND resource_version = 0
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'DISCOVERY_EXECUTOR_ACTIVATED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'DISCOVERY_EXECUTOR'
            AND resource_version = 1
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'DISCOVERY_JOB_CREATED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'DISCOVERY_JOB'
            AND resource_version = 0
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'DISCOVERY_JOB_DISPATCHED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'DISCOVERY_JOB'
            AND resource_version = 1
            AND result = 'SUCCESS'
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'DISCOVERY_JOB_COMPLETED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'DISCOVERY_EXECUTOR'
            AND actor_id IS NOT NULL
            AND resource_type = 'DISCOVERY_JOB'
            AND resource_version = 2
            AND result IN ('SUCCESS', 'FAILED')
            AND metadata = '{}'::jsonb
        )
        OR (
            event_type = 'DISCOVERED_DEVICE_IGNORED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'DISCOVERED_DEVICE'
            AND resource_version = 1
            AND result = 'SUCCESS'
            AND metadata = '{"newStatus":"IGNORED","previousStatus":"UNREVIEWED"}'::jsonb
        )
        OR (
            event_type = 'DISCOVERED_DEVICE_INCORPORATED'
            AND event_version = '2.0'
            AND producer_module = 'DISCOVERY'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'DISCOVERED_DEVICE'
            AND resource_version = 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'previousStatus', 'UNREVIEWED',
                'newStatus', 'INCORPORATED',
                'assetId', metadata->'assetId')
            AND jsonb_typeof(metadata->'assetId') = 'string'
            AND (metadata->>'assetId')::text ~
                '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        )
        OR (
            event_type = 'TICKET_CREATED'
            AND event_version = '2.0'
            AND producer_module = 'SUPPORT'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'TICKET'
            AND resource_version = 0
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'type', metadata->>'type',
                'status', 'OPEN',
                'priority', 'NORMAL',
                'source', 'WEB')
            AND metadata->>'type' IN ('INCIDENT', 'SERVICE_REQUEST', 'QUESTION', 'OTHER')
        )
        OR (
            event_type = 'TICKET_COMMENT_ADDED'
            AND event_version = '2.0'
            AND producer_module = 'SUPPORT'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'TICKET'
            AND resource_version IS NOT NULL AND resource_version >= 0
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'commentId', metadata->>'commentId',
                'visibility', metadata->>'visibility')
            AND metadata->>'commentId' ~
                '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            AND metadata->>'visibility' IN ('PUBLIC', 'INTERNAL')
        )
        OR (
            event_type = 'TICKET_ASSIGNED'
            AND event_version = '2.0'
            AND producer_module = 'SUPPORT'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'TICKET'
            AND resource_version IS NOT NULL AND resource_version >= 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'assigneeUserId', lower(actor_id::text))
        )
        OR (
            event_type = 'TICKET_PRIORITY_CHANGED'
            AND event_version = '2.0'
            AND producer_module = 'SUPPORT'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'TICKET'
            AND resource_version IS NOT NULL AND resource_version >= 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'previousPriority', metadata->>'previousPriority',
                'newPriority', metadata->>'newPriority')
            AND metadata->>'previousPriority' IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
            AND metadata->>'newPriority' IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
            AND metadata->>'previousPriority' <> metadata->>'newPriority'
        )
        OR (
            event_type = 'TICKET_STATUS_CHANGED'
            AND event_version = '2.0'
            AND producer_module = 'SUPPORT'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'TICKET'
            AND resource_version IS NOT NULL AND resource_version >= 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'previousStatus', metadata->>'previousStatus',
                'newStatus', metadata->>'newStatus')
            AND (
                (metadata->>'previousStatus' = 'OPEN'
                    AND metadata->>'newStatus' = 'IN_PROGRESS')
                OR (metadata->>'previousStatus' = 'IN_PROGRESS'
                    AND metadata->>'newStatus' = 'WAITING_FOR_REQUESTER')
                OR (metadata->>'previousStatus' = 'WAITING_FOR_REQUESTER'
                    AND metadata->>'newStatus' = 'IN_PROGRESS'))
        )
        OR (
            event_type = 'TICKET_RESOLVED'
            AND event_version = '2.0'
            AND producer_module = 'SUPPORT'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'TICKET'
            AND resource_version IS NOT NULL AND resource_version >= 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'lifecycleEventId', metadata->>'lifecycleEventId',
                'fromStatus', metadata->>'fromStatus',
                'toStatus', metadata->>'toStatus')
            AND metadata->>'lifecycleEventId' ~
                '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            AND metadata->>'fromStatus' IN ('IN_PROGRESS', 'WAITING_FOR_REQUESTER')
            AND metadata->>'toStatus' = 'RESOLVED'
        )
        OR (
            event_type = 'TICKET_CLOSED'
            AND event_version = '2.0'
            AND producer_module = 'SUPPORT'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'TICKET'
            AND resource_version IS NOT NULL AND resource_version >= 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'lifecycleEventId', metadata->>'lifecycleEventId',
                'fromStatus', metadata->>'fromStatus',
                'toStatus', metadata->>'toStatus',
                'closeReason', metadata->>'closeReason')
            AND metadata->>'lifecycleEventId' ~
                '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            AND metadata->>'toStatus' = 'CLOSED'
            AND (
                (metadata->>'fromStatus' = 'RESOLVED'
                    AND metadata->>'closeReason' = 'RESOLVED')
                OR (metadata->>'fromStatus' IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER')
                    AND metadata->>'closeReason' IN (
                        'CANCELLED', 'DUPLICATE', 'NOT_APPLICABLE')))
        )
        OR (
            event_type = 'REPORT_EXPORTED'
            AND event_version = '1.0'
            AND producer_module = 'AUDIT_REPORTING'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'REPORT_EXPORT'
            AND resource_id = source_event_id
            AND resource_version IS NULL
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'format', 'CSV',
                'reportType', metadata->>'reportType',
                'rowCount', metadata->>'rowCount')
            AND metadata->>'reportType' IN ('ASSETS', 'AGENTS', 'TICKETS')
            AND metadata->>'rowCount' ~ '^(0|[1-9][0-9]{0,4})$'
            AND (metadata->>'rowCount')::integer <= 10000
        )
        OR (
            event_type = 'TICKET_REOPENED'
            AND event_version = '2.0'
            AND producer_module = 'SUPPORT'
            AND actor_type = 'HUMAN_USER'
            AND actor_id IS NOT NULL
            AND resource_type = 'TICKET'
            AND resource_version IS NOT NULL AND resource_version >= 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'lifecycleEventId', metadata->>'lifecycleEventId',
                'fromStatus', metadata->>'fromStatus',
                'toStatus', metadata->>'toStatus')
            AND metadata->>'lifecycleEventId' ~
                '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            AND metadata->>'fromStatus' IN ('RESOLVED', 'CLOSED')
            AND metadata->>'toStatus' = 'OPEN'
        )
        OR (
            event_type = 'AUTOMATION_EXECUTION_SUCCEEDED'
            AND event_version = '1.0'
            AND producer_module = 'AUTOMATION'
            AND actor_type = 'AUTOMATION'
            AND actor_id IS NOT NULL
            AND resource_type = 'AUTOMATION_EXECUTION'
            AND resource_id = actor_id
            AND resource_version = 1
            AND result = 'SUCCESS'
            AND metadata = jsonb_build_object(
                'ruleCode', 'SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER',
                'actionType', 'IN_APP_NOTIFICATION')
        )
        OR (
            event_type = 'AUTOMATION_EXECUTION_FAILED'
            AND event_version = '1.0'
            AND producer_module = 'AUTOMATION'
            AND actor_type = 'AUTOMATION'
            AND actor_id IS NOT NULL
            AND resource_type = 'AUTOMATION_EXECUTION'
            AND resource_id = actor_id
            AND resource_version = 1
            AND result = 'FAILED'
            AND metadata = jsonb_build_object(
                'ruleCode', 'SUPPORT_WAITING_FOR_REQUESTER_NOTIFY_REQUESTER',
                'actionType', 'IN_APP_NOTIFICATION',
                'failureCode', 'NOTIFICATION_PERSISTENCE_FAILED')
        )
    );
