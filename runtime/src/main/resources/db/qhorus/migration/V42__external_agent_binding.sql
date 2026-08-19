CREATE TABLE external_agent_binding (
    id UUID PRIMARY KEY,
    instance_id VARCHAR(255) NOT NULL,
    endpoint VARCHAR(1024) NOT NULL,
    auth_config_key VARCHAR(255),
    protocol_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_eab_instance_id UNIQUE (instance_id),
    CONSTRAINT fk_eab_instance FOREIGN KEY (instance_id) REFERENCES instance(instance_id) ON DELETE CASCADE
);
