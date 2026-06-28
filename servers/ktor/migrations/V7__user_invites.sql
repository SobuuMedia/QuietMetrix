-- Invite-based onboarding. Invited users have no usable password until they
-- accept the invite link and set one (status flips active, token cleared).
ALTER TABLE users ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'active';
ALTER TABLE users ADD COLUMN IF NOT EXISTS invite_token VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS invite_expires_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_users_invite_token ON users (invite_token);
