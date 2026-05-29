-- commitment_id on message: links to commitment table entry.
-- Auto-set by infrastructure on QUERY/COMMAND. Added to entity but missing from
-- prior migrations.
ALTER TABLE message ADD COLUMN IF NOT EXISTS commitment_id UUID;
