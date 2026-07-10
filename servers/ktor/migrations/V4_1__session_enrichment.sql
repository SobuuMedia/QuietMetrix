-- V4: Session tracking, GeoIP enrichment, browser/OS detection, screen resolution, anonymous_id

-- New sessions table for session lifecycle tracking
CREATE TABLE IF NOT EXISTS sessions (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES projects(id),
    session_id      VARCHAR(128) NOT NULL,
    anonymous_id    VARCHAR(128),
    user_id         VARCHAR(128),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    duration_sec    INT,
    event_count     INT NOT NULL DEFAULT 0,
    country         CHAR(2),
    city            VARCHAR(100),
    region          VARCHAR(100),
    device_class    VARCHAR(20),
    browser         VARCHAR(50),
    browser_version VARCHAR(50),
    os              VARCHAR(50),
    os_version      VARCHAR(50),
    platform        VARCHAR(20),
    screen_width    INT,
    screen_height   INT,
    language        VARCHAR(10),
    referrer        VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_project_session ON sessions(project_id, session_id);
CREATE INDEX idx_sessions_project_started ON sessions(project_id, started_at);
CREATE INDEX idx_sessions_anonymous ON sessions(anonymous_id);

-- Enrich events table with new columns
ALTER TABLE events ADD COLUMN IF NOT EXISTS anonymous_id    VARCHAR(128);
ALTER TABLE events ADD COLUMN IF NOT EXISTS city            VARCHAR(100);
ALTER TABLE events ADD COLUMN IF NOT EXISTS region          VARCHAR(100);
ALTER TABLE events ADD COLUMN IF NOT EXISTS browser         VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS browser_version VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS os              VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS os_version      VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS screen_width    INT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS screen_height   INT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS referrer        VARCHAR(500);
ALTER TABLE events ADD COLUMN IF NOT EXISTS session_number  INT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_session_start BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_session_end   BOOLEAN NOT NULL DEFAULT false;

-- Indexes for new breakdown queries
CREATE INDEX IF NOT EXISTS idx_events_country ON events(project_id, country) WHERE country IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_browser ON events(project_id, browser) WHERE browser IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_os ON events(project_id, os) WHERE os IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_anonymous ON events(anonymous_id) WHERE anonymous_id IS NOT NULL;
