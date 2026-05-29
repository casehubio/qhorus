-- allowed_types on channel: comma-separated MessageType names permitted on this channel.
-- Null = all types permitted (open channel). Added to entity but missing from prior migrations.
ALTER TABLE channel ADD COLUMN allowed_types TEXT;

-- deadline on message: when the obligation must be discharged (QUERY/COMMAND only).
-- Null = no temporal constraint. Added to entity but missing from prior migrations.
ALTER TABLE message ADD COLUMN deadline TIMESTAMP;

-- acknowledged_at on message: when the obligation was explicitly accepted.
-- Null in v1; populated by ACK mechanism. Added to entity but missing from prior migrations.
ALTER TABLE message ADD COLUMN acknowledged_at TIMESTAMP;
