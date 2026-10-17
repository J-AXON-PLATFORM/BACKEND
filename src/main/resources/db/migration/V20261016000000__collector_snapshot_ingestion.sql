CREATE TABLE collector.snapshot_ingestions (
    ingestion_id collector.uuid_v4 PRIMARY KEY,
    claimed_agent_id collector.uuid_v4 NOT NULL,
    claimed_message_id collector.uuid_v4 NOT NULL,
    source_path varchar(160) NOT NULL,
    file_fingerprint char(64) NOT NULL CHECK (file_fingerprint ~ '^[0-9a-f]{64}$'),
    file_size bigint NOT NULL CHECK (file_size >= 0),
    received_at timestamptz NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('ACCEPTED', 'REJECTED', 'CONFLICT')),
    reason_code varchar(64) NULL CHECK (reason_code IS NULL OR reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    snapshot_id collector.uuid_v4 NULL,
    CHECK ((status = 'ACCEPTED') = (snapshot_id IS NOT NULL)),
    CHECK ((status = 'ACCEPTED') = (reason_code IS NULL)),
    UNIQUE (source_path, file_fingerprint)
);

CREATE TABLE collector.accepted_snapshots (
    snapshot_id collector.uuid_v4 PRIMARY KEY,
    message_id collector.uuid_v4 NOT NULL UNIQUE,
    ingestion_id collector.uuid_v4 NOT NULL UNIQUE
        REFERENCES collector.snapshot_ingestions(ingestion_id),
    agent_id collector.uuid_v4 NOT NULL REFERENCES collector.agents(id),
    contract_version varchar(16) NOT NULL CHECK (contract_version = '1.0'),
    created_at timestamptz NOT NULL,
    captured_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    content_sha256 char(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    hostname varchar(253) NOT NULL,
    os_family varchar(16) NOT NULL CHECK (os_family IN ('WINDOWS', 'LINUX', 'MACOS', 'OTHER')),
    os_name varchar(128) NOT NULL,
    os_version varchar(128) NOT NULL,
    architecture varchar(16) NOT NULL CHECK (architecture IN ('X86_64', 'X86', 'ARM64', 'ARM32', 'OTHER')),
    logical_cpu_count integer NOT NULL CHECK (logical_cpu_count >= 1),
    memory_bytes bigint NOT NULL CHECK (memory_bytes >= 0),
    storage_volumes jsonb NOT NULL CHECK (jsonb_typeof(storage_volumes) = 'array'),
    CHECK (jsonb_array_length(storage_volumes) <= 64)
);

CREATE INDEX collector_snapshot_ingestions_received_at_idx
    ON collector.snapshot_ingestions (received_at);
CREATE INDEX collector_accepted_snapshots_agent_captured_idx
    ON collector.accepted_snapshots (agent_id, captured_at DESC, received_at DESC, snapshot_id DESC);
CREATE INDEX collector_accepted_snapshots_received_at_idx
    ON collector.accepted_snapshots (received_at);
