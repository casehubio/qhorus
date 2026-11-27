-- V2010: Template match fields on message_ledger_entry
-- Records structured governance evidence extracted by protocol template matching.
-- Refs casehubio/qhorus#546

ALTER TABLE message_ledger_entry ADD COLUMN template_namespace VARCHAR(200);
ALTER TABLE message_ledger_entry ADD COLUMN template_name     VARCHAR(200);
ALTER TABLE message_ledger_entry ADD COLUMN template_fields   TEXT;
ALTER TABLE message_ledger_entry ADD COLUMN template_score    DOUBLE PRECISION;
