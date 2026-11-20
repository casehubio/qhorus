CREATE TABLE protocol_definition (
    id              UUID         NOT NULL PRIMARY KEY,
    channel_id      UUID         NOT NULL,
    protocol_name   VARCHAR(200) NOT NULL,
    yaml_content    TEXT         NOT NULL,
    tenancy_id      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uq_protocol_def_channel_name UNIQUE (channel_id, protocol_name, tenancy_id)
);
