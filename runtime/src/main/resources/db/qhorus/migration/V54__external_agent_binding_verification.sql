ALTER TABLE external_agent_binding ADD COLUMN verification_status VARCHAR(20) DEFAULT 'UNVERIFIED';
ALTER TABLE external_agent_binding ADD COLUMN verified_at TIMESTAMP;
ALTER TABLE external_agent_binding ADD COLUMN verification_key_id VARCHAR(255);
