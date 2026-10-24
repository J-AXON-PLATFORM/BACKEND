ALTER TABLE identity.technical_identities
    ADD COLUMN discovery_executor_id identity.uuid_v4 NULL UNIQUE,
    ALTER COLUMN agent_id DROP NOT NULL,
    DROP CONSTRAINT technical_identities_identity_type_check,
    ADD CONSTRAINT technical_identities_identity_type_check CHECK (
        identity_type IN ('AGENT', 'DISCOVERY_EXECUTOR')
    ),
    ADD CONSTRAINT technical_identity_owner_rule CHECK (
        (identity_type = 'AGENT' AND agent_id IS NOT NULL AND discovery_executor_id IS NULL)
        OR (identity_type = 'DISCOVERY_EXECUTOR' AND agent_id IS NULL AND discovery_executor_id IS NOT NULL)
    );

ALTER TABLE identity.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check,
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN (
            'HUMAN_LOGIN_SUCCEEDED',
            'HUMAN_LOGIN_FAILED',
            'AGENT_PUBLIC_KEY_REGISTERED',
            'DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED',
            'HUMAN_USER_CREATED',
            'HUMAN_LOGOUT_SUCCEEDED'
        )
    ) NOT VALID,
    DROP CONSTRAINT outbox_events_resource_type_check,
    ADD CONSTRAINT outbox_events_resource_type_check CHECK (
        resource_type IN (
            'AUTHENTICATION_ATTEMPT',
            'AGENT_TECHNICAL_IDENTITY',
            'DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY',
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
        OR (event_type = 'DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED' AND result = 'SUCCESS'
            AND actor_id IS NOT NULL AND resource_type = 'DISCOVERY_EXECUTOR_TECHNICAL_IDENTITY'
            AND payload = '{}'::jsonb)
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

CREATE TABLE discovery.discovery_executors (
    id discovery.uuid_v4 PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    normalized_name varchar(200) NOT NULL UNIQUE CHECK (btrim(normalized_name) <> ''),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status = 'PENDING'),
    created_by_user_id discovery.uuid_v4 NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version = 0),
    CHECK (updated_at >= created_at)
);

CREATE INDEX discovery_executors_order_idx
    ON discovery.discovery_executors (normalized_name ASC, id ASC);

CREATE TABLE discovery.discovery_executor_scopes (
    executor_id discovery.uuid_v4 NOT NULL REFERENCES discovery.discovery_executors(id),
    scope_id discovery.uuid_v4 NOT NULL REFERENCES discovery.authorized_scopes(id),
    associated_at timestamptz NOT NULL,
    PRIMARY KEY (executor_id, scope_id)
);

CREATE INDEX discovery_executor_scopes_scope_idx
    ON discovery.discovery_executor_scopes (scope_id, executor_id);

ALTER TABLE discovery.outbox_events
    DROP CONSTRAINT outbox_events_resource_id_fkey,
    DROP CONSTRAINT outbox_events_event_type_check,
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN (
            'DISCOVERY_SCOPE_CREATED',
            'DISCOVERY_SCOPE_STATUS_CHANGED',
            'DISCOVERY_EXECUTOR_PRE_REGISTERED'
        )
    ),
    DROP CONSTRAINT outbox_events_resource_type_check,
    ADD CONSTRAINT outbox_events_resource_type_check CHECK (
        resource_type IN ('AUTHORIZED_SCOPE', 'DISCOVERY_EXECUTOR')
    ),
    DROP CONSTRAINT outbox_events_check,
    ADD CONSTRAINT outbox_events_payload_check CHECK (
        jsonb_typeof(payload) = 'object'
        AND (
            (event_type = 'DISCOVERY_SCOPE_CREATED'
                AND resource_type = 'AUTHORIZED_SCOPE'
                AND payload = '{"status":"ACTIVE"}'::jsonb)
            OR (event_type = 'DISCOVERY_SCOPE_STATUS_CHANGED'
                AND resource_type = 'AUTHORIZED_SCOPE'
                AND payload = jsonb_build_object(
                    'previousStatus', payload->'previousStatus',
                    'newStatus', payload->'newStatus'
                )
                AND jsonb_typeof(payload->'previousStatus') = 'string'
                AND jsonb_typeof(payload->'newStatus') = 'string'
                AND payload->>'previousStatus' IN ('ACTIVE', 'DISABLED')
                AND payload->>'newStatus' IN ('ACTIVE', 'DISABLED')
                AND payload->>'previousStatus' <> payload->>'newStatus')
            OR (event_type = 'DISCOVERY_EXECUTOR_PRE_REGISTERED'
                AND resource_type = 'DISCOVERY_EXECUTOR'
                AND payload = '{"status":"PENDING"}'::jsonb)
        )
    );
