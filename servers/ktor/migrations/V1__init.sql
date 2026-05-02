CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS projects (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    owner_user_id   BIGINT NOT NULL REFERENCES users(id),
    api_key_hash  VARCHAR(255) NOT NULL,
    plan_id         VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_owner ON projects(owner_user_id);
CREATE INDEX idx_projects_api_key ON projects(api_key_hash);

CREATE TABLE IF NOT EXISTS events_inbox (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES projects(id),
    payload     JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed   BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_events_inbox_project_unprocessed ON events_inbox(project_id, processed)
    WHERE NOT processed;

CREATE TABLE IF NOT EXISTS events (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES projects(id),
    event_name      VARCHAR(255) NOT NULL,
    screen          VARCHAR(255),
    props           JSONB,
    session_id      VARCHAR(128),
    ts              TIMESTAMPTZ NOT NULL,
    was_offline     BOOLEAN NOT NULL DEFAULT false,
    country         CHAR(2),
    device_class    VARCHAR(20),
    language        VARCHAR(10),
    platform        VARCHAR(20),
    sdk_version     VARCHAR(20),
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_project_ts ON events(project_id, ts);
CREATE INDEX idx_events_project_name_ts ON events(project_id, event_name, ts);

CREATE TABLE IF NOT EXISTS event_counts_daily (
    project_id  BIGINT NOT NULL REFERENCES projects(id),
    day         DATE NOT NULL,
    event_name  VARCHAR(255) NOT NULL,
    count       BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (project_id, day, event_name)
);

CREATE TABLE IF NOT EXISTS usage_counters (
    project_id  BIGINT NOT NULL REFERENCES projects(id),
    period      CHAR(6) NOT NULL,
    events_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (project_id, period)
);