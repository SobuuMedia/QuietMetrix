-- V14: Funnel definitions.
--
-- Steps are stored as a JSON array (order is positional): [{key, event, name?, screen?,
-- props?}, ...]. A funnel emits no new events — steps reference event names the app already
-- sends. `source`/`locked` prevent SDK auto-registration from silently overwriting a
-- definition an analyst has edited in the dashboard.

CREATE TABLE IF NOT EXISTS funnels (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    funnel_key      VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(1000),
    -- TEXT, not JSONB: Exposed's `text()` DSL type binds as VARCHAR, and Postgres's JDBC
    -- driver refuses to implicitly cast a VARCHAR parameter into jsonb on INSERT/UPDATE
    -- (see events.props / projects.allowed_events, which hit this same mismatch pre-existing
    -- this migration). Steps are only ever read/written as opaque JSON text by this codebase,
    -- so TEXT with no native JSON validation is the correct, working column type.
    steps           TEXT NOT NULL,
    window_seconds  BIGINT NOT NULL DEFAULT 604800,
    source          VARCHAR(16) NOT NULL DEFAULT 'dashboard',
    locked          BOOLEAN NOT NULL DEFAULT false,
    archived_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, funnel_key)
);

CREATE INDEX IF NOT EXISTS idx_funnels_project ON funnels(project_id) WHERE archived_at IS NULL;
