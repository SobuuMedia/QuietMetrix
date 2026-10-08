-- Keep protocol-v2 cells distinct by their single report projection.
-- Existing cells were written before explicit projections existed and represent the
-- project-wide/global projection, while platform and country remain metadata columns.
ALTER TABLE counter_cells_v2
    ADD COLUMN segment_kind VARCHAR(20) NOT NULL DEFAULT 'global',
    ADD COLUMN segment_value VARCHAR(128) NOT NULL DEFAULT 'all';

ALTER TABLE counter_cells_v2 DROP CONSTRAINT counter_cells_v2_pkey;
ALTER TABLE counter_cells_v2 ADD PRIMARY KEY
    (project_id, day, hour, metric, platform, app_version, country, dims_hash, segment_kind, segment_value);

CREATE INDEX idx_counter_cells_v2_segment_time
    ON counter_cells_v2(project_id, metric, segment_kind, segment_value, day, hour);
