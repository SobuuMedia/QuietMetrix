-- Long-lived personal access tokens ("qm_pat_…") that let an agent/CLI call the API
-- without a user's password. Only a sha256 hash is stored — see
-- AccessTokenRepository.kt / docs/agents/setup.md.
CREATE TABLE IF NOT EXISTS access_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    token_sha256    VARCHAR(64) NOT NULL,
    token_last4     VARCHAR(4) NOT NULL,
    -- Comma-separated scope slugs, e.g. "projects:create,projects:read".
    scopes          VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    UNIQUE (token_sha256)
);

CREATE INDEX IF NOT EXISTS idx_access_tokens_user ON access_tokens(user_id, revoked_at);
