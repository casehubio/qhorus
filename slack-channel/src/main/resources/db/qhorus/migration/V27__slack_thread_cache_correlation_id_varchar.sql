ALTER TABLE slack_thread_cache ALTER COLUMN correlation_id TYPE VARCHAR(255) USING correlation_id::text;
