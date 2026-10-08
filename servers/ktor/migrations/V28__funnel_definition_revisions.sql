-- Immutable semantic snapshots let dashboards edit funnels without reinterpreting old results.
ALTER TABLE funnels ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS funnel_definition_revisions (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    funnel_key VARCHAR(64) NOT NULL,
    revision INTEGER NOT NULL,
    steps TEXT NOT NULL,
    window_seconds BIGINT NOT NULL,
    count_mode VARCHAR(16) NOT NULL,
    identity_scope VARCHAR(32) NOT NULL,
    correlation_property VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, funnel_key, revision)
);

-- Existing dashboard definitions become revision one in their own dashboard namespace.
-- Their previous manual results are not backfilled into the new immutable revision.
UPDATE funnels SET revision = 1 WHERE source = 'dashboard' AND revision = 0;

INSERT INTO funnel_definition_revisions
    (project_id, funnel_key, revision, steps, window_seconds, count_mode, identity_scope, correlation_property, created_at)
SELECT project_id, funnel_key, revision, steps, window_seconds, count_mode, identity_scope, correlation_property, created_at
FROM funnels
ON CONFLICT (project_id, funnel_key, revision) DO NOTHING;
