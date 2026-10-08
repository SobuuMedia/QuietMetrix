ALTER TABLE counter_batch_receipts_v2
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '14 days');
CREATE INDEX IF NOT EXISTS idx_counter_batch_receipts_v2_expires
    ON counter_batch_receipts_v2(expires_at);
