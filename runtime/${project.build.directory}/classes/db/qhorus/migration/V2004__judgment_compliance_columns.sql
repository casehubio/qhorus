ALTER TABLE message_ledger_entry ADD COLUMN judgment_id UUID;
ALTER TABLE message_ledger_entry ADD COLUMN judgment_type VARCHAR(100);
ALTER TABLE message_ledger_entry ADD COLUMN verification_outcome VARCHAR(20);
ALTER TABLE message_ledger_entry ADD COLUMN evidence_quality DOUBLE PRECISION;

ALTER TABLE message_ledger_entry ADD CONSTRAINT chk_evidence_quality
    CHECK (evidence_quality IS NULL OR (evidence_quality >= 0 AND evidence_quality <= 1));

CREATE INDEX idx_mle_toolname ON message_ledger_entry(tool_name);
