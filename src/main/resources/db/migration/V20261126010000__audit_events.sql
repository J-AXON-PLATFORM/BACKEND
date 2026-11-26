CREATE DOMAIN audit_reporting.uuid_v4 AS uuid
    CHECK ((get_byte(uuid_send(VALUE), 6) >> 4) = 4 AND (get_byte(uuid_send(VALUE), 8) & 192) = 128);

CREATE TABLE audit_reporting.audit_events (
    audit_event_id audit_reporting.uuid_v4 PRIMARY KEY,
    source_event_id audit_reporting.uuid_v4 NOT NULL UNIQUE,
    event_type varchar(64) NOT NULL CHECK (event_type = 'AI_SOURCE_DISABLED'),
    event_version varchar(16) NOT NULL CHECK (event_version = '1.0'),
    producer_module varchar(32) NOT NULL CHECK (producer_module IN (
        'INVENTORY', 'DISCOVERY', 'COLLECTOR', 'SUPPORT', 'AI', 'IDENTITY', 'AUTOMATION',
        'AUDIT_REPORTING')),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    actor_type varchar(32) NOT NULL CHECK (actor_type IN (
        'HUMAN_USER', 'SYSTEM', 'AGENT', 'DISCOVERY_EXECUTOR', 'AUTOMATION')),
    actor_id audit_reporting.uuid_v4 NULL,
    resource_type varchar(64) NOT NULL,
    resource_id audit_reporting.uuid_v4 NOT NULL,
    resource_version bigint NULL CHECK (resource_version >= 0),
    result varchar(16) NOT NULL CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),
    correlation_id audit_reporting.uuid_v4 NOT NULL,
    metadata jsonb NOT NULL CHECK (jsonb_typeof(metadata) = 'object' AND octet_length(metadata::text) <= 4096),
    CONSTRAINT audit_events_catalog_check CHECK (
        event_type = 'AI_SOURCE_DISABLED'
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
);

CREATE INDEX audit_events_occurred_idx
    ON audit_reporting.audit_events (occurred_at DESC, audit_event_id DESC);
CREATE INDEX audit_events_recorded_idx ON audit_reporting.audit_events (recorded_at);
CREATE INDEX audit_events_correlation_idx ON audit_reporting.audit_events (correlation_id);
CREATE INDEX audit_events_resource_idx ON audit_reporting.audit_events (resource_type, resource_id);
CREATE INDEX audit_events_actor_idx ON audit_reporting.audit_events (actor_id);
CREATE INDEX audit_events_type_idx ON audit_reporting.audit_events (event_type);

CREATE FUNCTION audit_reporting.reject_audit_event_mutation() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_reporting.audit_events is append-only'
        USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_reporting.audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_reporting.reject_audit_event_mutation();

CREATE TRIGGER audit_events_no_truncate
    BEFORE TRUNCATE ON audit_reporting.audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_reporting.reject_audit_event_mutation();
