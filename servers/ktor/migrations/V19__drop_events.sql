-- V19: Drop the raw event-stream ingest path.
--
-- QuietMetrix is now aggregate-only ingest: every dashboard read (Overview, Flow, Funnels,
-- Retention, Sessions) is served from `counters` (see V18). Nothing writes to these tables
-- any more — `/track` and `/track/batch` are removed, and the SDK's event queue/HTTP
-- transport that posted to them is gone too. This is a breaking, one-way migration: any
-- historical per-event or per-session row in these tables is deleted, not archived.
--
-- `event_counts_daily` and `usage_counters` are untouched: they belong to the (separate,
-- already-unlimited-in-self-host) quota system, not the event stream.

DROP TABLE IF EXISTS events_quarantine;
DROP TABLE IF EXISTS ingest_audit;
DROP TABLE IF EXISTS install_meta;
DROP TABLE IF EXISTS events_inbox;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS events;

-- install_salt / analytics_salt existed to hash the SDK's `anonymousId` for /track's abuse
-- defense and funnel/retention identity — both are on-device now, so no server-side install
-- identifier exists to hash. strict_schema / allowed_events (an event-name allowlist enforced
-- at /track) has nothing left to enforce against.
ALTER TABLE projects DROP COLUMN IF EXISTS install_salt;
ALTER TABLE projects DROP COLUMN IF EXISTS analytics_salt;
ALTER TABLE projects DROP COLUMN IF EXISTS strict_schema;
ALTER TABLE projects DROP COLUMN IF EXISTS allowed_events;
