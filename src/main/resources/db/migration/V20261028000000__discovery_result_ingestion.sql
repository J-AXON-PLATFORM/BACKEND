ALTER TABLE discovery.discovery_jobs
    ADD COLUMN started_at timestamptz NULL,
    ADD COLUMN completed_at timestamptz NULL,
    ADD COLUMN failure_code varchar(32) NULL,
    ADD COLUMN result_checked_at timestamptz NULL,
    ADD COLUMN targets_considered numeric(39, 0) NULL,
    ADD COLUMN targets_scanned integer NULL,
    ADD COLUMN observations_found integer NULL,
    ADD CONSTRAINT discovery_jobs_result_rule CHECK (
        (status NOT IN ('SUCCEEDED', 'FAILED')
            AND started_at IS NULL AND completed_at IS NULL AND failure_code IS NULL
            AND targets_considered IS NULL AND targets_scanned IS NULL
            AND observations_found IS NULL)
        OR (status = 'SUCCEEDED' AND version = 2
            AND started_at IS NOT NULL AND completed_at IS NOT NULL
            AND completed_at >= started_at
            AND failure_code IS NULL
            AND targets_considered IS NOT NULL AND targets_considered >= 0
            AND targets_scanned IS NOT NULL AND targets_scanned >= 0
            AND observations_found IS NOT NULL AND observations_found >= 0
            AND targets_scanned = targets_considered
            AND observations_found <= targets_scanned)
        OR (status = 'FAILED' AND version = 2
            AND started_at IS NOT NULL AND completed_at IS NOT NULL
            AND completed_at >= started_at
            AND failure_code IS NOT NULL AND failure_code IN (
                'JOB_EXPIRED', 'SCOPE_TOO_LARGE', 'UNSUPPORTED_PLATFORM',
                'EXECUTION_INTERRUPTED', 'EXECUTION_ERROR')
            AND targets_considered IS NOT NULL AND targets_considered >= 0
            AND targets_scanned IS NOT NULL AND targets_scanned >= 0
            AND targets_scanned <= targets_considered
            AND observations_found IS NOT NULL AND observations_found = 0)
    );

CREATE INDEX discovery_jobs_result_reconciliation_idx
    ON discovery.discovery_jobs (result_checked_at, job_id)
    WHERE status IN ('DISPATCHED', 'SUCCEEDED', 'FAILED', 'EXPIRED');

CREATE TABLE discovery.discovery_result_ingestions (
    ingestion_id discovery.uuid_v4 PRIMARY KEY,
    path_executor_id discovery.uuid_v4 NOT NULL,
    path_job_id discovery.uuid_v4 NOT NULL,
    source_path varchar(512) NOT NULL CHECK (btrim(source_path) <> ''),
    file_fingerprint varchar(64) NOT NULL CHECK (file_fingerprint ~ '^[0-9a-f]{64}$'),
    file_size bigint NOT NULL CHECK (file_size >= 0),
    received_at timestamptz NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('ACCEPTED', 'REJECTED', 'CONFLICT')),
    reason_code varchar(64) NULL,
    claimed_result_id discovery.uuid_v4 NULL,
    claimed_job_id discovery.uuid_v4 NULL,
    UNIQUE (source_path, file_fingerprint),
    CHECK ((status = 'ACCEPTED' AND reason_code IS NULL)
        OR (status IN ('REJECTED', 'CONFLICT') AND reason_code IS NOT NULL))
);

CREATE INDEX discovery_result_ingestions_received_idx
    ON discovery.discovery_result_ingestions (received_at, ingestion_id);

CREATE TABLE discovery.accepted_discovery_results (
    result_id discovery.uuid_v4 PRIMARY KEY,
    job_id discovery.uuid_v4 NOT NULL UNIQUE REFERENCES discovery.discovery_jobs(job_id),
    ingestion_id discovery.uuid_v4 NOT NULL UNIQUE
        REFERENCES discovery.discovery_result_ingestions(ingestion_id),
    executor_id discovery.uuid_v4 NOT NULL REFERENCES discovery.discovery_executors(id),
    scope_id discovery.uuid_v4 NOT NULL REFERENCES discovery.authorized_scopes(id),
    schema_version varchar(16) NOT NULL CHECK (schema_version = '1.0'),
    profile varchar(16) NOT NULL CHECK (profile = 'BASIC'),
    result_status varchar(16) NOT NULL CHECK (result_status IN ('SUCCEEDED', 'FAILED')),
    failure_code varchar(32) NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz NOT NULL CHECK (completed_at >= started_at),
    received_at timestamptz NOT NULL,
    content_sha256 varchar(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    content_size bigint NOT NULL CHECK (content_size >= 0 AND content_size <= 5242880),
    targets_considered numeric(39, 0) NOT NULL CHECK (targets_considered >= 0),
    targets_scanned integer NOT NULL CHECK (targets_scanned >= 0 AND targets_scanned <= 4096),
    observations_found integer NOT NULL CHECK (observations_found >= 0 AND observations_found <= 4096),
    FOREIGN KEY (executor_id, scope_id)
        REFERENCES discovery.discovery_executor_scopes(executor_id, scope_id),
    UNIQUE (result_id, job_id, result_status),
    CHECK (targets_scanned <= targets_considered),
    CHECK (observations_found <= targets_scanned),
    CHECK (result_status <> 'SUCCEEDED' OR targets_scanned = targets_considered),
    CHECK ((result_status = 'SUCCEEDED' AND failure_code IS NULL)
        OR (result_status = 'FAILED' AND failure_code IS NOT NULL AND failure_code IN (
            'JOB_EXPIRED', 'SCOPE_TOO_LARGE', 'UNSUPPORTED_PLATFORM',
            'EXECUTION_INTERRUPTED', 'EXECUTION_ERROR') AND observations_found = 0))
);

CREATE TABLE discovery.discovered_devices (
    discovered_device_id discovery.uuid_v4 PRIMARY KEY,
    result_id discovery.uuid_v4 NOT NULL,
    job_id discovery.uuid_v4 NOT NULL,
    result_status varchar(16) NOT NULL CHECK (result_status = 'SUCCEEDED'),
    observation_index integer NOT NULL CHECK (observation_index >= 0 AND observation_index < 4096),
    status varchar(16) NOT NULL CHECK (status = 'UNREVIEWED'),
    ip_address inet NOT NULL,
    mac_address macaddr NULL,
    hostname varchar(253) NULL CHECK (hostname IS NULL OR btrim(hostname) <> ''),
    evidence varchar(8)[] NOT NULL CHECK (
        cardinality(evidence) BETWEEN 1 AND 3
        AND evidence <@ ARRAY['ARP', 'ICMP', 'TCP']::varchar[]),
    open_tcp_ports integer[] NOT NULL CHECK (
        cardinality(open_tcp_ports) <= 6
        AND open_tcp_ports <@ ARRAY[22, 53, 80, 443, 445, 3389]),
    observed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (result_id, job_id, result_status)
        REFERENCES discovery.accepted_discovery_results(result_id, job_id, result_status),
    UNIQUE (job_id, observation_index),
    UNIQUE (job_id, ip_address)
);

CREATE INDEX discovered_devices_job_order_idx
    ON discovery.discovered_devices (job_id, observation_index, discovered_device_id);

ALTER TABLE discovery.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check,
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN (
            'DISCOVERY_SCOPE_CREATED',
            'DISCOVERY_SCOPE_STATUS_CHANGED',
            'DISCOVERY_EXECUTOR_PRE_REGISTERED',
            'DISCOVERY_EXECUTOR_ACTIVATED',
            'DISCOVERY_JOB_CREATED',
            'DISCOVERY_JOB_DISPATCHED',
            'DISCOVERY_JOB_COMPLETED'
        )
    ),
    DROP CONSTRAINT outbox_events_actor_type_check,
    ADD CONSTRAINT outbox_events_actor_type_check CHECK (
        actor_type IN ('HUMAN_USER', 'DISCOVERY_EXECUTOR')
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
                    'newStatus', payload->'newStatus')
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
            OR (event_type = 'DISCOVERY_JOB_COMPLETED'
                AND actor_type = 'DISCOVERY_EXECUTOR'
                AND resource_type = 'DISCOVERY_JOB'
                AND resource_version = 2
                AND payload = jsonb_build_object(
                    'status', payload->'status',
                    'observationsFound', payload->'observationsFound')
                AND jsonb_typeof(payload->'status') = 'string'
                AND payload->>'status' IN ('SUCCEEDED', 'FAILED')
                AND jsonb_typeof(payload->'observationsFound') = 'number')
        )
    );
