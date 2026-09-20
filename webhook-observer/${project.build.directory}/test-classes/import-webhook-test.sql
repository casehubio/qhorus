CREATE TABLE IF NOT EXISTS ledger_subject_sequence (
    subject_id VARCHAR(255) NOT NULL PRIMARY KEY,
    next_sequence BIGINT NOT NULL DEFAULT 1
);
