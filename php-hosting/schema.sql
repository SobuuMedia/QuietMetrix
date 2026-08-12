-- QuietMetrix — MySQL schema for the IONOS / shared-hosting deployment.
-- Applied automatically on first request when the `users` table is missing.
-- Idempotent: every statement uses CREATE TABLE IF NOT EXISTS so re-running it
-- against an already-installed database is a no-op.

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'admin',   -- admin | developer | reviewer
    status        VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active | invited
    invite_token  CHAR(64)     NULL,                       -- set while status = invited
    invite_expires VARCHAR(32) NULL,                       -- ISO-8601 UTC expiry
    created_at    VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_users_email (email),
    KEY idx_users_invite_token (invite_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS projects (
    id              VARCHAR(36)   NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    description     VARCHAR(1000) NULL,
    owner_user_id   VARCHAR(36)  NOT NULL,
    api_key_hash    CHAR(64)     NOT NULL,    -- SHA-256 hex of the random api token
    api_key_last4   CHAR(4)      NULL,         -- non-sensitive, for masked display
    install_salt    CHAR(64)     NULL,         -- per-project salt for hashing install ids (Stage 2)
    -- Separate salt for funnel/analytics identity (events.install_hash). Unlike install_salt,
    -- this is NEVER rotated on API-key regeneration, so funnel/retention history survives a
    -- key rotation. See docs/security/publishable-api-key.md — Privacy note.
    analytics_salt  CHAR(64)     NULL,
    -- Stage 3 — opt-in event-name allowlist. When strict_schema=1, only events whose name
    -- is in the allowed_events JSON array are accepted. Bounded blast radius for
    -- publishable API keys (F-Droid etc.).
    strict_schema   TINYINT(1)   NOT NULL DEFAULT 0,
    allowed_events  JSON         NULL,
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
    duration_ms  BIGINT       NULL,    -- time-on-screen (ms), promoted from screen_view props
    -- Analytics-salt hash of the install id (projects.analytics_salt). Never the raw value —
    -- see docs/security/publishable-api-key.md — Privacy note.
    install_hash CHAR(64)     NULL,
    PRIMARY KEY (id),
    KEY idx_events_project_ts          (project_id, ts),
    KEY idx_events_project_name_ts     (project_id, event_name, ts),
    KEY idx_events_install_hash        (project_id, install_hash),
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

-- Funnel definitions. Steps are a JSON array (order is positional): [{key, event, name?,
-- screen?, props?}, ...]. A funnel emits no new events — steps reference event names the
-- app already sends. `source`/`locked` prevent SDK auto-registration from silently
-- overwriting a definition an analyst has edited in the dashboard.
CREATE TABLE IF NOT EXISTS funnels (
    id              VARCHAR(36)   NOT NULL,
    project_id      VARCHAR(36)   NOT NULL,
    funnel_key      VARCHAR(64)   NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    description     VARCHAR(1000) NULL,
    steps           JSON          NOT NULL,
    window_seconds  BIGINT        NOT NULL DEFAULT 604800,
    source          VARCHAR(16)   NOT NULL DEFAULT 'dashboard',
    locked          TINYINT(1)    NOT NULL DEFAULT 0,
    count_mode      VARCHAR(16)   NOT NULL DEFAULT 'actor',
    identity_scope  VARCHAR(32)   NOT NULL DEFAULT 'install_or_session',
    correlation_property VARCHAR(128) NULL,
    archived_at     VARCHAR(32)   NULL,
    created_at      VARCHAR(32)   NOT NULL,
    updated_at      VARCHAR(32)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_funnels_project_key (project_id, funnel_key),
    CONSTRAINT fk_funnels_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Last accepted revision per code-owned manifest. Older app releases cannot overwrite a
-- newer funnel definition during a staged rollout.
CREATE TABLE IF NOT EXISTS funnel_manifests (
    project_id VARCHAR(36)  NOT NULL,
    namespace  VARCHAR(128) NOT NULL,
    revision   BIGINT       NOT NULL,
    updated_at VARCHAR(32)  NOT NULL,
    PRIMARY KEY (project_id, namespace),
    CONSTRAINT fk_funnel_manifests_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Abuse-defense Stage 2: per-install tracking. The SDK's anonymousId is salt-hashed
-- (projects.install_salt) before storage — the raw id is never stored. See
-- docs/security/publishable-api-key.md — Privacy note.
CREATE TABLE IF NOT EXISTS install_meta (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    project_id        VARCHAR(36)  NOT NULL,
    anonymous_id_hash CHAR(64)     NOT NULL,
    first_seen_at     VARCHAR(32)  NOT NULL,
    last_seen_at      VARCHAR(32)  NOT NULL,
    event_count       BIGINT       NOT NULL DEFAULT 0,
    revoked           TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY idx_install_meta_project_install (project_id, anonymous_id_hash),
    KEY idx_install_meta_project_revoked (project_id, revoked),
    CONSTRAINT fk_install_meta_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Abuse-defense Stage 4: quarantine + audit tables. See docs/security/publishable-api-key.md.
CREATE TABLE IF NOT EXISTS events_quarantine (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    project_id          VARCHAR(36)  NOT NULL,
    payload             TEXT         NOT NULL,
    quarantine_reason   VARCHAR(50)  NOT NULL,
    quarantine_detail   TEXT,
    client_ip           VARCHAR(45),
    anonymous_id_hash   CHAR(64),
    quarantined_at      VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_quarantine_project_time (project_id, quarantined_at),
    CONSTRAINT fk_q_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ingest_audit (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    project_id          VARCHAR(36)  NOT NULL,
    api_key_last4       CHAR(4),
    client_ip           VARCHAR(45),
    anonymous_id_hash   CHAR(64),
    event_name          VARCHAR(255) NOT NULL,
    disposition         VARCHAR(20)  NOT NULL,
    reason              TEXT,
    audited_at          VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_project_time (project_id, audited_at),
    CONSTRAINT fk_audit_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
