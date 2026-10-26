CREATE TABLE discovery.discovery_jobs (
    job_id discovery.uuid_v4 PRIMARY KEY,
    executor_id discovery.uuid_v4 NOT NULL REFERENCES discovery.discovery_executors(id),
    scope_id discovery.uuid_v4 NOT NULL REFERENCES discovery.authorized_scopes(id),
    profile varchar(16) NOT NULL CHECK (profile = 'BASIC'),
    status varchar(16) NOT NULL CHECK (status IN (
        'PENDING', 'DISPATCHED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED'
    )),
    actor_id discovery.uuid_v4 NOT NULL,
    correlation_id discovery.uuid_v4 NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    dispatched_at timestamptz NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    FOREIGN KEY (executor_id, scope_id)
        REFERENCES discovery.discovery_executor_scopes(executor_id, scope_id),
    CHECK (expires_at = created_at + interval '1 hour'),
    CHECK (updated_at >= created_at),
    CHECK (status <> 'PENDING' OR (dispatched_at IS NULL AND version = 0)),
    CHECK (status <> 'DISPATCHED' OR (dispatched_at IS NOT NULL AND version = 1)),
    CHECK (status NOT IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
        OR dispatched_at IS NOT NULL)
);

CREATE UNIQUE INDEX discovery_jobs_nonterminal_executor_idx
    ON discovery.discovery_jobs (executor_id)
    WHERE status IN ('PENDING', 'DISPATCHED', 'RUNNING');
CREATE INDEX discovery_jobs_pending_idx
    ON discovery.discovery_jobs (status, created_at)
    WHERE status = 'PENDING';
CREATE INDEX discovery_jobs_expiration_idx
    ON discovery.discovery_jobs (expires_at)
    WHERE status IN ('PENDING', 'DISPATCHED');

ALTER TABLE discovery.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check,
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN (
            'DISCOVERY_SCOPE_CREATED',
            'DISCOVERY_SCOPE_STATUS_CHANGED',
            'DISCOVERY_EXECUTOR_PRE_REGISTERED',
            'DISCOVERY_EXECUTOR_ACTIVATED',
            'DISCOVERY_JOB_CREATED',
            'DISCOVERY_JOB_DISPATCHED'
        )
    ),
    DROP CONSTRAINT outbox_events_resource_type_check,
    ADD CONSTRAINT outbox_events_resource_type_check CHECK (
        resource_type IN ('AUTHORIZED_SCOPE', 'DISCOVERY_EXECUTOR', 'DISCOVERY_JOB')
    ),
    DROP CONSTRAINT outbox_events_payload_check,
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
            OR (event_type = 'DISCOVERY_EXECUTOR_ACTIVATED'
                AND resource_type = 'DISCOVERY_EXECUTOR'
                AND resource_version = 1
                AND payload = '{"status":"ACTIVE"}'::jsonb)
            OR (event_type = 'DISCOVERY_JOB_CREATED'
                AND resource_type = 'DISCOVERY_JOB'
                AND resource_version = 0
                AND payload = '{"status":"PENDING","profile":"BASIC"}'::jsonb)
            OR (event_type = 'DISCOVERY_JOB_DISPATCHED'
                AND resource_type = 'DISCOVERY_JOB'
                AND resource_version = 1
                AND payload = '{"status":"DISPATCHED"}'::jsonb)
        )
    );
