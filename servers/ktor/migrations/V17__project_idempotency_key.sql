-- Optional client-supplied dedup key for POST /projects (agents/CLIs). A retry of a
-- timed-out create with the same (owner, key) finds the earlier project instead of
-- minting a second one. Multiple NULLs per owner do not collide (standard Postgres UNIQUE
-- semantics) — this is opt-in. See ProjectRepository.kt / docs/agents/setup.md.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS idx_projects_owner_idempotency
    ON projects(owner_user_id, idempotency_key);
