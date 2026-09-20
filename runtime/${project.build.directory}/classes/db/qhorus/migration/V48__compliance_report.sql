CREATE TABLE compliance_report (
    id UUID PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    tenancy_id VARCHAR(255) NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    schedule_id UUID REFERENCES compliance_report_schedule(id),
    artefact_id UUID NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    format VARCHAR(10) NOT NULL
);

CREATE INDEX idx_compliance_report_type_tenant ON compliance_report(report_type, tenancy_id);
CREATE INDEX idx_compliance_report_generated ON compliance_report(generated_at);
