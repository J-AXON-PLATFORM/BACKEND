ALTER TABLE collector.agents
    DROP CONSTRAINT agents_status_check,
    ADD CONSTRAINT agents_status_check CHECK (status IN ('PENDING', 'ACTIVE')),
    ADD COLUMN sftp_username varchar(64) NULL,
    ADD COLUMN activated_at timestamptz NULL,
    ADD CONSTRAINT collector_agents_activation_state_check CHECK (
        (status = 'PENDING' AND sftp_username IS NULL AND activated_at IS NULL)
        OR (status = 'ACTIVE' AND sftp_username IS NOT NULL AND activated_at IS NOT NULL)
    ),
    ADD CONSTRAINT collector_agents_sftp_username_unique UNIQUE (sftp_username);

CREATE UNIQUE INDEX collector_agents_one_active_per_asset_idx
    ON collector.agents (asset_id)
    WHERE status = 'ACTIVE';

ALTER TABLE collector.outbox_events
    DROP CONSTRAINT outbox_events_event_type_check,
    ADD CONSTRAINT outbox_events_event_type_check CHECK (
        event_type IN ('AGENT_PRE_REGISTERED', 'AGENT_ACTIVATED')
    );
