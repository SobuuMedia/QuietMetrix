-- Additive. Older access JWTs expire normally; older refresh JWTs must sign in again.
CREATE TABLE auth_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX auth_sessions_expiry ON auth_sessions(expires_at);

CREATE TABLE request_rate_buckets (
    bucket_key VARCHAR(64) PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at BIGINT NOT NULL
);
CREATE INDEX request_rate_buckets_expiry ON request_rate_buckets(expires_at);
