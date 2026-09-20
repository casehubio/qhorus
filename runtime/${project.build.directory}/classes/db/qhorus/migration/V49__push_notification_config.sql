CREATE TABLE push_notification_config (
    id UUID PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL,
    channel_id UUID NOT NULL,
    url VARCHAR(1024) NOT NULL,
    token VARCHAR(512),
    auth_scheme VARCHAR(64),
    auth_credentials_ref VARCHAR(255),
    tenancy_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_pushed_at TIMESTAMP,
    CONSTRAINT uq_push_config_task_url UNIQUE (task_id, url),
    CONSTRAINT fk_push_config_channel FOREIGN KEY (channel_id)
        REFERENCES channel(id) ON DELETE CASCADE
);

CREATE INDEX idx_push_config_task_id ON push_notification_config(task_id);
CREATE INDEX idx_push_config_channel_id ON push_notification_config(channel_id);
