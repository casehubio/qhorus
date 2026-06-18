CREATE TABLE slack_thread_cache (
    channel_id     UUID        NOT NULL,
    correlation_id UUID        NOT NULL,
    thread_ts      VARCHAR(32) NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    CONSTRAINT pk_slack_thread_cache  PRIMARY KEY (channel_id, correlation_id),
    CONSTRAINT uq_slack_thread_ts     UNIQUE (channel_id, thread_ts)
);
