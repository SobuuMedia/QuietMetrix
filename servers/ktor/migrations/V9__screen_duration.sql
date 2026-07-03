-- Per-screen dwell time. `screen_view` events carry a duration_ms (milliseconds spent on the
-- previous screen); promoted from the event props into a real column for efficient aggregation.
ALTER TABLE events ADD COLUMN duration_ms BIGINT NULL;
