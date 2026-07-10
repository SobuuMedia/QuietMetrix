-- V2: Multi-project membership support
-- Adds project_members table for team access
-- Adds deleted_at to projects for soft-delete
-- Adds plan_id to users for per-user plan tracking

CREATE TABLE IF NOT EXISTS project_members (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'viewer',
    -- roles: 'owner', 'admin', 'viewer'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(project_id, user_id)
);

CREATE INDEX idx_project_members_user ON project_members(user_id);
CREATE INDEX idx_project_members_project ON project_members(project_id);

ALTER TABLE projects ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE users ADD COLUMN IF NOT EXISTS plan_id VARCHAR(50);