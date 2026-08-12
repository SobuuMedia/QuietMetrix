-- Versioned app-owned funnel manifests and matching semantics.
ALTER TABLE funnels ADD COLUMN IF NOT EXISTS count_mode VARCHAR(16) NOT NULL DEFAULT 'actor';
ALTER TABLE funnels ADD COLUMN IF NOT EXISTS identity_scope VARCHAR(32) NOT NULL DEFAULT 'install_or_session';
ALTER TABLE funnels ADD COLUMN IF NOT EXISTS correlation_property VARCHAR(128);

CREATE TABLE IF NOT EXISTS funnel_manifests (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    namespace VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, namespace)
);
