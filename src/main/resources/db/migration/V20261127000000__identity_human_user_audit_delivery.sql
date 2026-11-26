CREATE TABLE identity.outbox_deliveries (
    event_id identity.uuid_v4 NOT NULL REFERENCES identity.outbox_events(event_id) ON DELETE CASCADE,
    consumer varchar(32) NOT NULL CHECK (consumer = 'AUDIT'),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts integer NOT NULL CHECK (attempts >= 0 AND attempts <= 10),
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz NULL,
    last_error_code varchar(32) NULL
        CHECK (last_error_code IN ('AUDIT_REJECTED', 'AUDIT_CONFLICT', 'AUDIT_UNAVAILABLE')),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, consumer),
    CONSTRAINT identity_outbox_deliveries_state_check CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status = 'PENDING' AND delivered_at IS NULL AND attempts < 10)
        OR (status = 'FAILED' AND delivered_at IS NULL AND attempts = 10
            AND last_error_code IS NOT NULL)
    )
);

CREATE INDEX identity_outbox_deliveries_due_idx
    ON identity.outbox_deliveries (consumer, status, next_attempt_at);

CREATE INDEX identity_outbox_events_type_idx
    ON identity.outbox_events (event_type, event_version, occurred_at, event_id);

ALTER TABLE identity.outbox_events
    DROP CONSTRAINT identity_outbox_event_rule,
    ADD CONSTRAINT identity_outbox_event_rule CHECK (
        (event_type = 'HUMAN_LOGIN_FAILED' AND result = 'DENIED'
            AND resource_type = 'AUTHENTICATION_ATTEMPT')
        OR (event_type = 'HUMAN_LOGIN_SUCCEEDED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'AUTHENTICATION_ATTEMPT')
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
            AND payload = '{}'::jsonb)
    ) NOT VALID;

ALTER TABLE identity.outbox_events VALIDATE CONSTRAINT identity_outbox_event_rule;
