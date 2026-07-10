-- Abuse-defense Stage 2: per-install tracking with a salted, hashed anonymousId.
-- See docs/security/publishable-api-key.md.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS install_salt VARCHAR(64);

CREATE TABLE IF NOT EXISTS install_meta (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT      NOT NULL,
    anonymous_id_hash   VARCHAR(64) NOT NULL,
    first_seen_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_count         BIGINT      NOT NULL DEFAULT 0,
    revoked             BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_install_meta_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_install_meta_project_install
    ON install_meta (project_id, anonymous_id_hash);
CREATE INDEX IF NOT EXISTS idx_install_meta_project_revoked
    ON install_meta (project_id, revoked);

-- Backfill install_salt for existing projects so they can hash install ids immediately.
UPDATE projects SET install_salt = md5(random()::text || clock_timestamp()::text) WHERE install_salt IS NULL;