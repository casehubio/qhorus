CREATE TABLE webhook_registration (
    id UUID PRIMARY KEY,
    channel_id UUID,
    url VARCHAR(2048) NOT NULL,
    secret_ref VARCHAR(255),
    headers TEXT,
    tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_webhook_url_channel_tenant UNIQUE (url, channel_id, tenancy_id),
    CONSTRAINT fk_webhook_channel FOREIGN KEY (channel_id) REFERENCES channel(id)
);

