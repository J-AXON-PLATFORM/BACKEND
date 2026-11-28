ALTER TABLE support.outbox_deliveries
    DROP CONSTRAINT outbox_deliveries_consumer_check,
    ADD CONSTRAINT outbox_deliveries_consumer_check
        CHECK (consumer IN ('AUDIT', 'AUTOMATION')),
    DROP CONSTRAINT outbox_deliveries_last_error_code_check,
    ADD CONSTRAINT outbox_deliveries_last_error_code_check CHECK (
        last_error_code IS NULL
        OR (consumer = 'AUDIT' AND last_error_code IN (
            'AUDIT_REJECTED', 'AUDIT_CONFLICT', 'AUDIT_UNAVAILABLE'))
        OR (consumer = 'AUTOMATION' AND last_error_code IN (
            'AUTOMATION_REJECTED', 'AUTOMATION_CONFLICT', 'AUTOMATION_UNAVAILABLE'))
    );
