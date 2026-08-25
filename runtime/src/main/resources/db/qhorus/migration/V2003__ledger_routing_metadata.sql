ALTER TABLE message_ledger_entry ADD COLUMN routing_original_target VARCHAR(255);
ALTER TABLE message_ledger_entry ADD COLUMN routing_selected_agent VARCHAR(255);
ALTER TABLE message_ledger_entry ADD COLUMN routing_strategy VARCHAR(100);
ALTER TABLE message_ledger_entry ADD COLUMN routing_candidate_count INTEGER;
