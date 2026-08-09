-- message_ledger_entry: FK to ledger_entry(id) from casehub-ledger (V1000 range)
CREATE TABLE IF NOT EXISTS message_ledger_entry (
    id                 UUID         NOT NULL,
    channel_id         UUID         NOT NULL,
    message_id         BIGINT       NOT NULL,
    message_type       VARCHAR(50)  NOT NULL,
    target             VARCHAR(255),
    content            TEXT,
    correlation_id     VARCHAR(255),
    commitment_id      UUID,
    tool_name          VARCHAR(255),
    duration_ms        BIGINT,
    token_count        BIGINT,
    context_refs       TEXT,
    source_entity      TEXT,
    topic              VARCHAR(200),
    context_window_pct SMALLINT,
    CONSTRAINT pk_message_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_message_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry (id)
);

CREATE INDEX IF NOT EXISTS idx_mle_channel ON message_ledger_entry (channel_id);
CREATE INDEX IF NOT EXISTS idx_mle_message_id ON message_ledger_entry (message_id);
CREATE INDEX IF NOT EXISTS idx_mle_correlation ON message_ledger_entry (correlation_id);
