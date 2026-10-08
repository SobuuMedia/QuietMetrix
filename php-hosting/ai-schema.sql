CREATE TABLE IF NOT EXISTS ai_plans (
    plan_id VARCHAR(100) PRIMARY KEY,
    ai_enabled BOOLEAN NOT NULL,
    generation_limit BIGINT NOT NULL CHECK (generation_limit >= 0),
    cost_limit BIGINT NOT NULL CHECK (cost_limit >= 0)
);
CREATE TABLE IF NOT EXISTS ai_assignments (
    account_id VARCHAR(100) PRIMARY KEY,
    plan_id VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    expires_at BIGINT
);
CREATE TABLE IF NOT EXISTS ai_monthly_usage (
    account_id VARCHAR(100) NOT NULL,
    month VARCHAR(10) NOT NULL,
    generations BIGINT NOT NULL DEFAULT 0,
    cost BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, month)
);
CREATE TABLE IF NOT EXISTS ai_operations (
    operation_id VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(100) NOT NULL,
    project_id VARCHAR(100) NOT NULL,
    month VARCHAR(10) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    reserved_cost BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    result MEDIUMTEXT,
    result_expires BIGINT NOT NULL,
    INDEX ai_operations_expiry (result_expires),
    INDEX ai_operations_account_time (account_id, created_at)
);

CREATE TABLE IF NOT EXISTS ai_summary_cache (
    cache_key VARCHAR(64) PRIMARY KEY,
    result MEDIUMTEXT,
    expires_at BIGINT NOT NULL DEFAULT 0,
    operation_id VARCHAR(64),
    lease_until BIGINT NOT NULL DEFAULT 0,
    INDEX ai_summary_expiry (expires_at)
);
