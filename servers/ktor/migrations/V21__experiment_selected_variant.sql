-- Expand only: preserves original experiment allocation and legacy ended rows.
ALTER TABLE experiments ADD COLUMN selected_variant VARCHAR(32) NULL;
