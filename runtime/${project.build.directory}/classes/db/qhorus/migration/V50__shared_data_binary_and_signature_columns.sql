-- SharedData binary content support
ALTER TABLE shared_data ADD COLUMN binary_content BYTEA;

-- Compliance report signature metadata
ALTER TABLE compliance_report ADD COLUMN signature_status VARCHAR(20) NOT NULL DEFAULT 'UNSIGNED';
ALTER TABLE compliance_report ADD COLUMN signed_at TIMESTAMP;
ALTER TABLE compliance_report ADD COLUMN signer_dn VARCHAR(500);
ALTER TABLE compliance_report ADD COLUMN key_ref VARCHAR(100);
ALTER TABLE compliance_report ADD COLUMN signing_profile VARCHAR(10);
ALTER TABLE compliance_report ADD COLUMN signature_artefact_id UUID;
