-- Idempotent counter ingestion receipts. A receipt is committed atomically with its counter
-- writes; retries with the same payload return the original response without applying twice.
CREATE TABLE IF NOT EXISTS counter_batch_receipts (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    batch_id VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    claim_token VARCHAR(32) NOT NULL,
    accepted INTEGER NOT NULL DEFAULT 0,
    quarantined INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, batch_id)
);

CREATE INDEX IF NOT EXISTS idx_counter_batch_receipts_created
    ON counter_batch_receipts(created_at);
