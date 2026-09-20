CREATE TABLE compliance_report_schedule (
    id UUID PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    channel_id UUID,
    schedule_json TEXT NOT NULL,
    format VARCHAR(10) NOT NULL,
    tenancy_id VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
