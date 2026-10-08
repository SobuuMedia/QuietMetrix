-- A dimension budget tracks distinct choices, not every dated counter cell.
-- Null supports lazy backfill of existing rows during ingestion.
ALTER TABLE counter_cells_v2 ADD COLUMN cardinality_hash CHAR(64);
CREATE INDEX idx_counter_cells_v2_cardinality
    ON counter_cells_v2(project_id, metric, cardinality_hash);
