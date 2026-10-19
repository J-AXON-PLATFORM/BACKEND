CREATE TABLE collector.agent_tasks (
    task_id collector.uuid_v4 PRIMARY KEY,
    dispatch_message_id collector.uuid_v4 NOT NULL UNIQUE,
    agent_id collector.uuid_v4 NOT NULL REFERENCES collector.agents(id),
    task_type varchar(32) NOT NULL CHECK (task_type = 'COLLECT_SNAPSHOT'),
    status varchar(16) NOT NULL CHECK (status IN (
        'PENDING', 'DISPATCHED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED'
    )),
    payload jsonb NOT NULL CHECK (payload = '{}'::jsonb),
    actor_id collector.uuid_v4 NOT NULL,
    correlation_id collector.uuid_v4 NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    dispatched_at timestamptz NULL,
    completed_at timestamptz NULL,
    error_code varchar(64) NULL CHECK (
        error_code IS NULL OR error_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
    ),
    error_message varchar(256) NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (expires_at = created_at + interval '15 minutes'),
    CHECK (status <> 'PENDING' OR dispatched_at IS NULL),
    CHECK (status NOT IN ('DISPATCHED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'REJECTED')
        OR dispatched_at IS NOT NULL),
    CHECK ((status IN ('SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED')) = (completed_at IS NOT NULL)),
    CHECK ((status IN ('FAILED', 'REJECTED')) = (error_code IS NOT NULL)),
    CHECK ((error_code IS NULL) = (error_message IS NULL))
);

CREATE UNIQUE INDEX collector_agent_tasks_non_terminal_type_idx
    ON collector.agent_tasks (agent_id, task_type)
    WHERE status IN ('PENDING', 'DISPATCHED', 'RUNNING');
CREATE UNIQUE INDEX collector_agent_tasks_running_idx
    ON collector.agent_tasks (agent_id)
    WHERE status = 'RUNNING';
CREATE INDEX collector_agent_tasks_agent_created_idx
    ON collector.agent_tasks (agent_id, created_at DESC, task_id DESC);
CREATE INDEX collector_agent_tasks_pending_idx
    ON collector.agent_tasks (status, created_at)
    WHERE status = 'PENDING';
CREATE INDEX collector_agent_tasks_expiration_idx
    ON collector.agent_tasks (expires_at)
    WHERE status IN ('PENDING', 'DISPATCHED');

CREATE TABLE collector.task_result_ingestions (
    ingestion_id collector.uuid_v4 PRIMARY KEY,
    path_agent_id collector.uuid_v4 NOT NULL,
    path_message_id collector.uuid_v4 NOT NULL,
    source_path varchar(192) NOT NULL,
    file_fingerprint char(64) NOT NULL CHECK (file_fingerprint ~ '^[0-9a-f]{64}$'),
    file_size bigint NOT NULL CHECK (file_size >= 0),
    received_at timestamptz NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('ACCEPTED', 'REJECTED', 'CONFLICT')),
    reason_code varchar(64) NULL CHECK (
        reason_code IS NULL OR reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
    ),
    claimed_result_id collector.uuid_v4 NULL,
    claimed_task_id collector.uuid_v4 NULL,
    UNIQUE (source_path, file_fingerprint),
    CHECK ((status = 'ACCEPTED') = (reason_code IS NULL))
);

CREATE TABLE collector.accepted_task_results (
    result_id collector.uuid_v4 PRIMARY KEY,
    message_id collector.uuid_v4 NOT NULL UNIQUE,
    task_id collector.uuid_v4 NOT NULL UNIQUE REFERENCES collector.agent_tasks(task_id),
    ingestion_id collector.uuid_v4 NOT NULL UNIQUE
        REFERENCES collector.task_result_ingestions(ingestion_id),
    agent_id collector.uuid_v4 NOT NULL REFERENCES collector.agents(id),
    task_type varchar(32) NOT NULL CHECK (task_type = 'COLLECT_SNAPSHOT'),
    result_status varchar(16) NOT NULL CHECK (result_status IN ('SUCCEEDED', 'FAILED', 'REJECTED')),
    contract_version varchar(16) NOT NULL CHECK (contract_version = '1.0'),
    created_at timestamptz NOT NULL,
    completed_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    content_sha256 char(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    error_code varchar(64) NULL CHECK (
        error_code IS NULL OR error_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
    ),
    error_message varchar(256) NULL,
    CHECK ((result_status = 'SUCCEEDED') = (error_code IS NULL)),
    CHECK ((error_code IS NULL) = (error_message IS NULL))
);

CREATE INDEX collector_task_result_ingestions_received_idx
    ON collector.task_result_ingestions (received_at);
CREATE INDEX collector_accepted_task_results_agent_completed_idx
    ON collector.accepted_task_results (agent_id, completed_at DESC, result_id DESC);
