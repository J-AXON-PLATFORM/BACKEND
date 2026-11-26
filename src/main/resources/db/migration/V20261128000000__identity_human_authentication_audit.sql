ALTER TABLE identity.outbox_events
    DROP CONSTRAINT identity_outbox_event_rule,
    ADD CONSTRAINT identity_outbox_event_rule CHECK (
        (event_type = 'HUMAN_LOGIN_FAILED' AND result = 'DENIED'
            AND resource_type = 'AUTHENTICATION_ATTEMPT')
        OR (event_type = 'HUMAN_LOGIN_SUCCEEDED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'AUTHENTICATION_ATTEMPT'
            AND event_version IN ('1.0', '2.0')
            AND payload = '{}'::jsonb)
        OR (event_type = 'AGENT_PUBLIC_KEY_REGISTERED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'AGENT_TECHNICAL_IDENTITY')
        OR (event_type = 'DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY'
            AND payload = '{}'::jsonb)
        OR (event_type = 'HUMAN_USER_CREATED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'HUMAN_USER'
            AND (
                (event_version = '1.0' AND payload = '{"status":"ACTIVE"}'::jsonb)
                OR (
                    event_version = '2.0'
                    AND payload = jsonb_build_object(
                        'status', payload->>'status',
                        'roles', payload->>'roles')
                    AND payload->>'status' = 'ACTIVE'
                    AND payload->>'roles' ~
                        '^(AUDITOR(\|END_USER)?(\|IT_OPERATOR)?(\|SUPPORT_TECHNICIAN)?(\|SYSTEM_ADMIN)?|END_USER(\|IT_OPERATOR)?(\|SUPPORT_TECHNICIAN)?(\|SYSTEM_ADMIN)?|IT_OPERATOR(\|SUPPORT_TECHNICIAN)?(\|SYSTEM_ADMIN)?|SUPPORT_TECHNICIAN(\|SYSTEM_ADMIN)?|SYSTEM_ADMIN)$'
                )
            ))
        OR (event_type = 'HUMAN_LOGOUT_SUCCEEDED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'HUMAN_SESSION'
            AND event_version IN ('1.0', '2.0')
            AND payload = '{}'::jsonb)
    ) NOT VALID;

ALTER TABLE identity.outbox_events VALIDATE CONSTRAINT identity_outbox_event_rule;
