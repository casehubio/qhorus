CREATE TABLE IF NOT EXISTS ledger_subject_sequence (
    subject_id UUID        PRIMARY KEY,
    next_seq   BIGINT      NOT NULL
);
