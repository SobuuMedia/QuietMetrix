-- Abuse-defense Stage 3: opt-in event-name allowlist for publishable-key projects.
-- See docs/security/publishable-api-key.md.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS strict_schema BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS allowed_events TEXT;