ALTER TABLE projects ADD COLUMN IF NOT EXISTS api_key_sha256 VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_projects_api_key_sha256 ON projects(api_key_sha256);

UPDATE projects SET api_key_sha256 = encode(digest(api_key_hash, 'sha256'), 'hex');

ALTER TABLE projects ALTER COLUMN api_key_sha256 SET NOT NULL;
