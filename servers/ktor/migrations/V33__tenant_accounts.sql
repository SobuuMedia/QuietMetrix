-- Existing installs are one trusted organization; new customers receive separate opaque IDs.
ALTER TABLE users ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'legacy';
ALTER TABLE users ADD COLUMN platform_operator BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX users_tenant ON users(tenant_id);
