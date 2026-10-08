-- Protocol v2 keeps hour-resolution cells and retry receipts separate from v1.
-- A nullable hour denotes a daily cell; 0-23 denotes one UTC hour. Never sum both
-- resolutions in one report.
CREATE TABLE IF NOT EXISTS counter_cells_v2 (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    day DATE NOT NULL,
    -- -1 represents a daily cell so the composite key remains non-null.
    hour SMALLINT NOT NULL DEFAULT -1,
    metric VARCHAR(64) NOT NULL,
    platform VARCHAR(20) NOT NULL DEFAULT 'unknown',
    app_version VARCHAR(32) NOT NULL DEFAULT '',
    country CHAR(2) NOT NULL DEFAULT '  ',
    dims_hash CHAR(64) NOT NULL,
    dims TEXT NOT NULL,
    n BIGINT NOT NULL DEFAULT 0,
    contributors BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, day, hour, metric, platform, app_version, country, dims_hash),
    CHECK (hour = -1 OR hour BETWEEN 0 AND 23)
);
CREATE INDEX IF NOT EXISTS idx_counter_cells_v2_project_metric_time
    ON counter_cells_v2(project_id, metric, day, hour);

CREATE TABLE IF NOT EXISTS counter_batch_receipts_v2 (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    batch_id VARCHAR(64) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    claim_token CHAR(32) NOT NULL,
    accepted INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, batch_id)
);
CREATE INDEX IF NOT EXISTS idx_counter_batch_receipts_v2_created
    ON counter_batch_receipts_v2(created_at);
