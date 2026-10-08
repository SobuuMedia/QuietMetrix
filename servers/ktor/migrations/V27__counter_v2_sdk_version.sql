ALTER TABLE counter_cells_v2
    ADD COLUMN IF NOT EXISTS sdk_version VARCHAR(32) NOT NULL DEFAULT 'unknown';
