-- Global user role: admin | developer | reviewer.
-- Existing users default to admin so the first/bootstrap account keeps full access.
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'admin';
