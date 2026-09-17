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
    plan_id         VARCHAR(50)  NULL,         -- nullable, cloud-only field
    -- Optional client-supplied dedup key for POST /projects (agents/CLIs). A retry of a
    -- timed-out create with the same (owner, key) finds the earlier project instead of
    -- minting a second one. Multiple NULLs per owner do not collide (standard SQL UNIQUE
    -- semantics) — this is opt-in.
    idempotency_key VARCHAR(128) NULL,
    created_at      VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_projects_api_key    (api_key_hash),
    UNIQUE KEY idx_projects_owner_idempotency (owner_user_id, idempotency_key),
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

-- Long-lived personal access tokens ("qm_pat_…") that let an agent/CLI call the API
-- without a user's password. Only a sha256 hash is stored — see src/accessTokens.php.
CREATE TABLE IF NOT EXISTS access_tokens (
    id            VARCHAR(36)  NOT NULL,
    user_id       VARCHAR(36)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    token_hash    CHAR(64)     NOT NULL,   -- SHA-256 hex of the plaintext token
    token_last4   CHAR(4)      NOT NULL,   -- non-sensitive, for masked display
    scopes        VARCHAR(500) NOT NULL,   -- comma-separated scope slugs
    created_at    VARCHAR(32)  NOT NULL,
    expires_at    VARCHAR(32)  NULL,
    last_used_at  VARCHAR(32)  NULL,
    revoked_at    VARCHAR(32)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_access_tokens_hash (token_hash),
    KEY idx_access_tokens_user (user_id, revoked_at),
    CONSTRAINT fk_at_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
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

-- Counter-based ingest (aggregate-only analytics). A counter cell is
-- (project, day, metric, platform, app_version, country, dims_hash) -> n. dims_hash is
-- counterRegistry.php's sha256 of the canonicalized dims map; dims stores that same map as
-- display JSON. devices is a running count of distinct installs that have ever contributed to
-- this cell (see the SDK's per-cell "first flush today" flag) -- no install identifier is ever
-- stored. Every read path must filter devices >= k (k-anonymity threshold); see counters.php.
CREATE TABLE IF NOT EXISTS counters (
    project_id   VARCHAR(36)  NOT NULL,
    day          VARCHAR(10)  NOT NULL,
    metric       VARCHAR(64)  NOT NULL,
    platform     VARCHAR(20)  NOT NULL DEFAULT '',
    app_version  VARCHAR(32)  NOT NULL DEFAULT '',
    country      CHAR(2)      NOT NULL DEFAULT '',
    dims_hash    CHAR(64)     NOT NULL,
    dims         TEXT         NOT NULL,
    n            BIGINT       NOT NULL DEFAULT 0,
    devices      BIGINT       NOT NULL DEFAULT 0,
    updated_at   VARCHAR(32)  NOT NULL,
    PRIMARY KEY (project_id, day, metric, platform, app_version, country, dims_hash),
    KEY idx_counters_project_metric_day (project_id, metric, day),
    CONSTRAINT fk_counters_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Cells rejected by counterRegistry.php (unknown metric, undeclared/malformed dims) or by the
-- per-metric distinct-cell cardinality cap. Kept for operator review.
CREATE TABLE IF NOT EXISTS counters_quarantine (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    project_id         VARCHAR(36)  NOT NULL,
    payload            TEXT         NOT NULL,
    quarantine_reason  VARCHAR(50)  NOT NULL,
    quarantine_detail  TEXT,
    quarantined_at     VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_counters_quarantine_project (project_id, quarantined_at),
    CONSTRAINT fk_counters_q_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
