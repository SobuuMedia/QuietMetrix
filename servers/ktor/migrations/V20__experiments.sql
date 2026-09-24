-- V20: A/B testing experiment definitions.
--
-- An experiment has exactly two variants (baseline + challenger). Assignment happens entirely
-- on the SDK side (deterministic local hashing) -- this table only holds the definition an
-- operator configures in the dashboard; the server never learns which device got which
-- variant. See docs/sdk/experiments.md.

CREATE TABLE IF NOT EXISTS experiments (
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    experiment_key   VARCHAR(32) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    screen           VARCHAR(128),
    status           VARCHAR(16) NOT NULL DEFAULT 'draft',
    traffic_percent  INT NOT NULL DEFAULT 100,
    -- TEXT, not JSONB: Exposed's text() DSL type binds as VARCHAR, and Postgres's JDBC driver
    -- refuses to implicitly cast a VARCHAR parameter into jsonb on INSERT/UPDATE -- see the
    -- same note on funnels.steps in V14__funnels.sql. Only ever read/written as opaque JSON
    -- text by this codebase.
    variants         TEXT NOT NULL,   -- JSON [{name, weight}, {name, weight}]
    countries        TEXT,            -- JSON ["ES","FR"] or NULL = all countries
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at       TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    UNIQUE (project_id, experiment_key)
);

CREATE INDEX IF NOT EXISTS idx_experiments_project_status ON experiments(project_id, status);
