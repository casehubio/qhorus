ALTER TABLE channel ADD COLUMN space_id UUID;

ALTER TABLE channel ADD CONSTRAINT fk_channel_space FOREIGN KEY (space_id) REFERENCES space(id);

CREATE INDEX idx_channel_space ON channel(space_id);
