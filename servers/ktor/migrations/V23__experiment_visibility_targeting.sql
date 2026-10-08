-- Additive v2 experiment semantics. Existing definitions are explicitly legacy so their
-- enrollment + split behavior and historical results cannot be reinterpreted.
ALTER TABLE experiments
    ADD COLUMN IF NOT EXISTS mode VARCHAR(16) NOT NULL DEFAULT 'legacy',
    ADD COLUMN IF NOT EXISTS placement_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS revision INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS b_percent INT,
    ADD COLUMN IF NOT EXISTS languages TEXT,
    ADD COLUMN IF NOT EXISTS match_mode VARCHAR(8) NOT NULL DEFAULT 'all',
    ADD COLUMN IF NOT EXISTS goal_event VARCHAR(64),
    ADD COLUMN IF NOT EXISTS icon_key VARCHAR(32);

-- The v1 experiments/config response remains unchanged. New builders opt into v2 semantics.
