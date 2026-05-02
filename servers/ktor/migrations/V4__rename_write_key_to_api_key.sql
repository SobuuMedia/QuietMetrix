ALTER TABLE projects ADD COLUMN IF NOT EXISTS api_key_hash VARCHAR(255);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS api_key_sha256 VARCHAR(64);

UPDATE projects SET api_key_hash = write_key_hash;
UPDATE projects SET api_key_sha256 = write_key_sha256;

ALTER TABLE projects DROP COLUMN IF EXISTS read_key_hash;
ALTER TABLE projects DROP COLUMN IF EXISTS write_key_hash;
ALTER TABLE projects DROP COLUMN IF EXISTS write_key_sha256;

DROP INDEX IF EXISTS idx_projects_write_key;
DROP INDEX IF EXISTS idx_projects_write_key_sha256;

CREATE INDEX IF NOT EXISTS idx_projects_api_key ON projects(api_key_hash);
CREATE INDEX IF NOT EXISTS idx_projects_api_key_sha256 ON projects(api_key_sha256);
