-- Index on commitment.obligor to support CommitmentStore.findOpenByObligor() query.
-- Without this index, the query scans the full commitment table on every call.
-- Refs #229.
CREATE INDEX idx_commitment_obligor ON commitment (obligor);
