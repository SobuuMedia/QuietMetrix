-- Non-sensitive last 4 chars of an API key for masked display in the dashboard.
-- The full key is never recoverable (only hashes are stored).
ALTER TABLE projects ADD COLUMN IF NOT EXISTS api_key_last4 VARCHAR(4);
