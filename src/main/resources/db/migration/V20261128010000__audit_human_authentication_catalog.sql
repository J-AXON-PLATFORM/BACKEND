ALTER TABLE audit_reporting.audit_events
    DROP CONSTRAINT audit_events_event_type_check,
    ADD CONSTRAINT audit_events_event_type_check CHECK (
        event_type IN (
            'AI_SOURCE_DISABLED',
            'HUMAN_USER_CREATED',
            'HUMAN_LOGIN_SUCCEEDED',
            'HUMAN_LOGOUT_SUCCEEDED')
    ),
    DROP CONSTRAINT audit_events_catalog_check,
    ADD CONSTRAINT audit_events_catalog_check CHECK (
        (
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
    );
