-- QuietMetrix — MySQL schema for the IONOS / shared-hosting deployment.
-- Applied automatically on first request when the `users` table is missing.
-- Idempotent: every statement uses CREATE TABLE IF NOT EXISTS so re-running it
-- against an already-installed database is a no-op.

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'admin',
    created_at    VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS projects (
    id              VARCHAR(36)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    owner_user_id   VARCHAR(36)  NOT NULL,
    api_key_hash    CHAR(64)     NOT NULL,    -- SHA-256 hex of the random api token
    plan_id         VARCHAR(50)  NULL,         -- nullable, cloud-only field
    created_at      VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_projects_api_key    (api_key_hash),
    KEY idx_projects_owner             (owner_user_id),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_members (
    project_id VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'member',
    added_at   VARCHAR(32) NOT NULL,
    PRIMARY KEY (project_id, user_id),
    KEY idx_project_members_user (user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user    FOREIGN KEY (user_id)    REFERENCES users    (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    project_id   VARCHAR(36)  NOT NULL,
    event_name   VARCHAR(255) NOT NULL,
    screen       VARCHAR(255) NULL,
    props        JSON         NULL,
    session_id   VARCHAR(128) NULL,
    ts           VARCHAR(32)  NOT NULL,    -- ISO-8601 UTC, client-supplied
    received_at  VARCHAR(32)  NOT NULL,    -- ISO-8601 UTC, server-stamped
    was_offline  TINYINT(1)   NOT NULL DEFAULT 0,
    country      CHAR(2)      NULL,
    device_class VARCHAR(20)  NULL,
    language     VARCHAR(10)  NULL,
    platform     VARCHAR(20)  NULL,
    sdk_version  VARCHAR(20)  NULL,
    PRIMARY KEY (id),
    KEY idx_events_project_ts          (project_id, ts),
    KEY idx_events_project_name_ts     (project_id, event_name, ts),
    CONSTRAINT fk_events_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS event_counts_daily (
    project_id VARCHAR(36)  NOT NULL,
    day        DATE         NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    count      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (project_id, day, event_name),
    CONSTRAINT fk_ecd_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS usage_counters (
    project_id   VARCHAR(36) NOT NULL,
    period       CHAR(6)     NOT NULL,    -- yyyymm
    events_count BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (project_id, period),
    CONSTRAINT fk_uc_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
