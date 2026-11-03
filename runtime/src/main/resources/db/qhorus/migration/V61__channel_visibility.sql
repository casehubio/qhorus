ALTER TABLE channel ADD COLUMN visibility VARCHAR(20);
ALTER TABLE channel ADD COLUMN bound_group_id UUID;
CREATE INDEX idx_channel_visibility ON channel(visibility);
CREATE INDEX idx_channel_bound_group_id ON channel(bound_group_id);
