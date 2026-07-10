ALTER TABLE projects ADD COLUMN IF NOT EXISTS api_key_hash VARCHAR(255);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS api_key_sha256 VARCHAR(64);

-- Copy legacy write_key_* values, but only on databases that were created with
-- the pre-rename schema. Fresh installs (V1 already uses api_key_*) skip this.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'projects' AND column_name = 'write_key_hash') THEN
        UPDATE projects SET api_key_hash = write_key_hash WHERE api_key_hash IS NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'projects' AND column_name = 'write_key_sha256') THEN
        UPDATE projects SET api_key_sha256 = write_key_sha256 WHERE api_key_sha256 IS NULL;
    END IF;
END $$;

ALTER TABLE projects DROP COLUMN IF EXISTS read_key_hash;
ALTER TABLE projects DROP COLUMN IF EXISTS write_key_hash;
ALTER TABLE projects DROP COLUMN IF EXISTS write_key_sha256;

DROP INDEX IF EXISTS idx_projects_write_key;
DROP INDEX IF EXISTS idx_projects_write_key_sha256;

CREATE INDEX IF NOT EXISTS idx_projects_api_key ON projects(api_key_hash);
CREATE INDEX IF NOT EXISTS idx_projects_api_key_sha256 ON projects(api_key_sha256);
