ALTER TABLE identity.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check,
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN (
            'HUMAN_LOGIN_SUCCEEDED',
            'HUMAN_LOGIN_FAILED',
            'AGENT_PUBLIC_KEY_REGISTERED',
            'HUMAN_USER_CREATED',
            'HUMAN_LOGOUT_SUCCEEDED'
        )
    ) NOT VALID,
    DROP CONSTRAINT outbox_events_resource_type_check,
    ADD CONSTRAINT outbox_events_resource_type_check CHECK (
        resource_type IN (
            'AUTHENTICATION_ATTEMPT',
            'AGENT_TECHNICAL_IDENTITY',
            'HUMAN_USER',
            'HUMAN_SESSION'
        )
    ) NOT VALID,
    DROP CONSTRAINT identity_outbox_event_rule,
    ADD CONSTRAINT identity_outbox_event_rule CHECK (
        (event_type = 'HUMAN_LOGIN_FAILED' AND result = 'DENIED'
            AND resource_type = 'AUTHENTICATION_ATTEMPT')
        OR (event_type = 'HUMAN_LOGIN_SUCCEEDED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'AUTHENTICATION_ATTEMPT')
        OR (event_type = 'AGENT_PUBLIC_KEY_REGISTERED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'AGENT_TECHNICAL_IDENTITY')
        OR (event_type = 'HUMAN_USER_CREATED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'HUMAN_USER'
            AND payload = '{"status":"ACTIVE"}'::jsonb)
        OR (event_type = 'HUMAN_LOGOUT_SUCCEEDED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'HUMAN_SESSION'
            AND payload = '{}'::jsonb)
    ) NOT VALID;

ALTER TABLE identity.outbox_events VALIDATE CONSTRAINT outbox_events_event_type_check;
ALTER TABLE identity.outbox_events VALIDATE CONSTRAINT outbox_events_resource_type_check;
ALTER TABLE identity.outbox_events VALIDATE CONSTRAINT identity_outbox_event_rule;
