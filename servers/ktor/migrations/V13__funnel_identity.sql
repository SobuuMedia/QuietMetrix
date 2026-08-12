-- V13: Funnel identity foundation.
--
-- Fixes a live bug: the SDK sends the per-install pseudonymous id nested under `ctx`, and both
-- servers already salt-hash it on arrival, but `events.anonymous_id` was populated with the RAW
-- value (never actually reachable in practice because of a separate wire-format bug, now fixed
-- in the SDK). This migration removes the raw-capable column entirely and replaces it with a
-- hash-only column, computed from a salt that is dedicated to analytics/funnel identity and is
-- never rotated on API-key regeneration (unlike `install_salt`, which IS rotated and backs the
-- separate abuse-defense pipeline in `install_meta`). See docs/security/publishable-api-key.md.

ALTER TABLE projects ADD COLUMN IF NOT EXISTS analytics_salt CHAR(64);

ALTER TABLE events ADD COLUMN IF NOT EXISTS install_hash CHAR(64);
CREATE INDEX IF NOT EXISTS idx_events_install_hash ON events(project_id, install_hash) WHERE install_hash IS NOT NULL;

ALTER TABLE events DROP COLUMN IF EXISTS anonymous_id;

-- `sessions.anonymous_id` / `sessions.user_id` were written only by startSession()/endSession(),
-- which have no callers anywhere in the codebase — dead columns that would otherwise carry the
-- same raw-value problem forward.
ALTER TABLE sessions DROP COLUMN IF EXISTS anonymous_id;
ALTER TABLE sessions DROP COLUMN IF EXISTS user_id;
