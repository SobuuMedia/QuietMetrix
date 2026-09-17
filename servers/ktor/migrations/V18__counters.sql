-- V18: Counter-based ingest (aggregate-only analytics).
--
-- A counter cell is (project, day, metric, platform, app_version, country, dims_hash) -> n.
-- `dims_hash` is CounterRegistry's sha256 of the canonicalized dims map; `dims` stores the
-- same map as display JSON. `devices` is the number of distinct installs that have ever
-- contributed to this cell (see CounterRegistry / the SDK's per-cell "first flush today"
-- flag) — no install identifier is ever stored, only this running count. Every read path
-- must filter `devices >= k` (k-anonymity threshold); see CounterRepository.
--
-- The composite primary key (not a surrogate id) makes the ingest write a plain upsert:
-- INSERT ... ON CONFLICT DO UPDATE SET n = n + ?, devices = devices + ?.

CREATE TABLE IF NOT EXISTS counters (
    project_id   BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    day          DATE NOT NULL,
    metric       VARCHAR(64) NOT NULL,
    platform     VARCHAR(20) NOT NULL DEFAULT '',
    app_version  VARCHAR(32) NOT NULL DEFAULT '',
    country      CHAR(2) NOT NULL DEFAULT '',
    dims_hash    CHAR(64) NOT NULL,
    dims         TEXT NOT NULL,
    n            BIGINT NOT NULL DEFAULT 0,
    devices      BIGINT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, day, metric, platform, app_version, country, dims_hash)
);

CREATE INDEX IF NOT EXISTS idx_counters_project_metric_day
    ON counters(project_id, metric, day);

-- Cells rejected by CounterRegistry (unknown metric, undeclared/malformed dims) or by the
-- per-metric distinct-cell cardinality cap. Kept for operator review, same pattern as
-- events_quarantine.
CREATE TABLE IF NOT EXISTS counters_quarantine (
    id                 BIGSERIAL PRIMARY KEY,
    project_id         BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    payload            TEXT NOT NULL,
    quarantine_reason  VARCHAR(50) NOT NULL,
    quarantine_detail  TEXT,
    quarantined_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_counters_quarantine_project
    ON counters_quarantine(project_id, quarantined_at);
