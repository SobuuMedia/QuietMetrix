<?php

/**
 * First-run bootstrap. Idempotent and cheap: most requests do a single indexed
 * SELECT to confirm the schema is in place and the admin exists.
 *
 *   1. If `users` table is missing, execute schema.sql against the database.
 *   2. If no user with ADMIN_EMAIL exists, create one with ADMIN_PASSWORD.
 *
 * Designed for shared hosting where the operator just uploads files — there is
 * no separate /setup wizard, no CLI step required.
 */
function ensureInstalled(): void {
    $db = getDb();

    // 1. Schema bootstrap
    if (!tableExists($db, 'users')) {
        $sql = file_get_contents(__DIR__ . '/../schema.sql');
        if ($sql === false) {
            throw new RuntimeException('schema.sql is missing from the deployment');
        }

        // Strip line comments (`-- ...` to end-of-line) BEFORE splitting on `;`.
        // Without this, a stray `;` inside a comment would truncate a statement
        // mid-definition. The schema has no string literals containing `;`, so
        // a naive split-on-semicolon is otherwise safe.
        $cleaned = preg_replace('/--[^\r\n]*/', '', $sql);

        foreach (explode(';', $cleaned) as $stmt) {
            $stmt = trim($stmt);
            if ($stmt === '') continue;
            $db->exec($stmt);
        }
    }

    // 1b. Additive column migrations for installs created before these columns
    // existed. MySQL has no portable `ADD COLUMN IF NOT EXISTS`, so guard each
    // ALTER with an information_schema check. Cheap: a handful of indexed reads.
    addColumnIfMissing($db, 'users', 'status', "VARCHAR(20) NOT NULL DEFAULT 'active'");
    addColumnIfMissing($db, 'users', 'invite_token', 'CHAR(64) NULL');
    addColumnIfMissing($db, 'users', 'invite_expires', 'VARCHAR(32) NULL');
    addColumnIfMissing($db, 'projects', 'description', 'VARCHAR(1000) NULL');
    addColumnIfMissing($db, 'projects', 'api_key_last4', 'CHAR(4) NULL');
    // Abuse-defense Stage 2: per-project install-id salt (backfilled for existing projects).
    addColumnIfMissing($db, 'projects', 'install_salt', 'CHAR(64) NULL');
    // Funnel/analytics identity: a separate salt from install_salt, never rotated on key
    // regeneration. See docs/security/publishable-api-key.md — Privacy note.
    addColumnIfMissing($db, 'projects', 'analytics_salt', 'CHAR(64) NULL');
    // Stage 3: event-name allowlist
    addColumnIfMissing($db, 'projects', 'strict_schema', "TINYINT(1) NOT NULL DEFAULT 0");
    addColumnIfMissing($db, 'projects', 'allowed_events', 'JSON NULL');
    // Backfill salts for any projects that predate the column.
    $db->exec("UPDATE projects SET install_salt = SUBSTR(MD5(CONCAT(RAND(), UUID())), 1, 64) WHERE install_salt IS NULL");
    // events: device classification + time-on-screen. Existing installs created
    // before these columns existed must gain them, or inserts that reference them
    // fail and every tracked event 500s (silently dropping duration_ms/device_class).
    addColumnIfMissing($db, 'events', 'device_class', 'VARCHAR(20) NULL');
    addColumnIfMissing($db, 'events', 'duration_ms', 'BIGINT NULL');
    addColumnIfMissing($db, 'events', 'install_hash', 'CHAR(64) NULL');

    // 1c. New tables for installs created before they existed. schema.sql (step 1 above) only
    // runs when `users` is missing, so every later table needs its own guarded CREATE. In
    // particular, tracking writes ingest_audit on every accepted event; omitting it here made
    // an upgraded installation return HTTP 500 for every track request after the event insert.
    createTableIfMissing($db, 'install_meta', "
        CREATE TABLE install_meta (
            id BIGINT NOT NULL AUTO_INCREMENT,
            project_id VARCHAR(36) NOT NULL,
            anonymous_id_hash CHAR(64) NOT NULL,
            first_seen_at VARCHAR(32) NOT NULL,
            last_seen_at VARCHAR(32) NOT NULL,
            event_count BIGINT NOT NULL DEFAULT 0,
            revoked TINYINT(1) NOT NULL DEFAULT 0,
            PRIMARY KEY (id),
            UNIQUE KEY idx_install_meta_project_install (project_id, anonymous_id_hash),
            KEY idx_install_meta_project_revoked (project_id, revoked),
            CONSTRAINT fk_install_meta_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    createTableIfMissing($db, 'events_quarantine', "
        CREATE TABLE events_quarantine (
            id BIGINT NOT NULL AUTO_INCREMENT,
            project_id VARCHAR(36) NOT NULL,
            payload TEXT NOT NULL,
            quarantine_reason VARCHAR(50) NOT NULL,
            quarantine_detail TEXT,
            client_ip VARCHAR(45),
            anonymous_id_hash CHAR(64),
            quarantined_at VARCHAR(32) NOT NULL,
            PRIMARY KEY (id),
            KEY idx_quarantine_project_time (project_id, quarantined_at),
            CONSTRAINT fk_q_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    createTableIfMissing($db, 'ingest_audit', "
        CREATE TABLE ingest_audit (
            id BIGINT NOT NULL AUTO_INCREMENT,
            project_id VARCHAR(36) NOT NULL,
            api_key_last4 CHAR(4),
            client_ip VARCHAR(45),
            anonymous_id_hash CHAR(64),
            event_name VARCHAR(255) NOT NULL,
            disposition VARCHAR(20) NOT NULL,
            reason TEXT,
            audited_at VARCHAR(32) NOT NULL,
            PRIMARY KEY (id),
            KEY idx_audit_project_time (project_id, audited_at),
            CONSTRAINT fk_audit_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    createTableIfMissing($db, 'funnels', "
        CREATE TABLE funnels (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    addColumnIfMissing($db, 'funnels', 'count_mode', "VARCHAR(16) NOT NULL DEFAULT 'actor'");
    addColumnIfMissing($db, 'funnels', 'identity_scope', "VARCHAR(32) NOT NULL DEFAULT 'install_or_session'");
    addColumnIfMissing($db, 'funnels', 'correlation_property', 'VARCHAR(128) NULL');
    createTableIfMissing($db, 'funnel_manifests', "
        CREATE TABLE funnel_manifests (
            project_id VARCHAR(36) NOT NULL,
            namespace VARCHAR(128) NOT NULL,
            revision BIGINT NOT NULL,
            updated_at VARCHAR(32) NOT NULL,
            PRIMARY KEY (project_id, namespace),
            CONSTRAINT fk_funnel_manifests_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    // Personal access tokens (agents/CLIs) — see docs/agents/setup.md.
    createTableIfMissing($db, 'access_tokens', "
        CREATE TABLE access_tokens (
            id            VARCHAR(36)  NOT NULL,
            user_id       VARCHAR(36)  NOT NULL,
            name          VARCHAR(255) NOT NULL,
            token_hash    CHAR(64)     NOT NULL,
            token_last4   CHAR(4)      NOT NULL,
            scopes        VARCHAR(500) NOT NULL,
            created_at    VARCHAR(32)  NOT NULL,
            expires_at    VARCHAR(32)  NULL,
            last_used_at  VARCHAR(32)  NULL,
            revoked_at    VARCHAR(32)  NULL,
            PRIMARY KEY (id),
            UNIQUE KEY idx_access_tokens_hash (token_hash),
            KEY idx_access_tokens_user (user_id, revoked_at),
            CONSTRAINT fk_at_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ");
    // Idempotent POST /projects (agents/CLIs retrying a lost response). The INSERT in
    // handleProjectsCreate() always references this column, so an install missing it would
    // fail to create ANY project, not just skip idempotency — this guard is load-bearing.
    addColumnIfMissing($db, 'projects', 'idempotency_key', 'VARCHAR(128) NULL');
    addIndexIfMissing($db, 'projects', 'idx_projects_owner_idempotency',
        'UNIQUE KEY idx_projects_owner_idempotency (owner_user_id, idempotency_key)');

    // 2. First admin user
    $stmt = $db->prepare('SELECT id FROM users WHERE email = ? LIMIT 1');
    $stmt->execute([ADMIN_EMAIL]);
    if ($stmt->fetch() === false) {
        $db->prepare(
            'INSERT INTO users (id, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)'
        )->execute([
            uuid4(),
            ADMIN_EMAIL,
            password_hash(ADMIN_PASSWORD, PASSWORD_BCRYPT, ['cost' => 10]),
            'admin',
            now(),
        ]);
    }
}

/**
 * Adds `$column $definition` to `$table` if the column is not already present.
 * Idempotent ALTER for shared-hosting MySQL (no `ADD COLUMN IF NOT EXISTS`).
 */
function addColumnIfMissing(PDO $db, string $table, string $column, string $definition): void {
    $stmt = $db->prepare(
        'SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ? LIMIT 1'
    );
    $stmt->execute([$table, $column]);
    if ($stmt->fetchColumn() === false) {
        // Identifiers are hard-coded constants from this file, not user input.
        $db->exec("ALTER TABLE `$table` ADD COLUMN `$column` $definition");
    }
}

/**
 * Creates `$table` from `$createSql` if it does not already exist. Sibling to
 * addColumnIfMissing() for whole tables introduced after a project shipped: schema.sql
 * alone never runs against an existing install (it only executes when `users` is missing),
 * so a new table needs its own guarded CREATE here.
 */
function createTableIfMissing(PDO $db, string $table, string $createSql): void {
    if (!tableExists($db, $table)) {
        $db->exec($createSql);
    }
}

/**
 * Adds `$indexDefinition` (e.g. "UNIQUE KEY name (col1, col2)") to `$table` if an index of
 * that name does not already exist. Sibling to addColumnIfMissing() — MySQL has no portable
 * `CREATE INDEX IF NOT EXISTS` either, so this guards with the same information_schema check.
 */
function addIndexIfMissing(PDO $db, string $table, string $indexName, string $indexDefinition): void {
    $stmt = $db->prepare(
        'SELECT 1 FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? LIMIT 1'
    );
    $stmt->execute([$table, $indexName]);
    if ($stmt->fetchColumn() === false) {
        // Identifiers are hard-coded constants from this file, not user input.
        $db->exec("ALTER TABLE `$table` ADD $indexDefinition");
    }
}

/**
 * Resolve the analytics window in seconds from the `?range` token (1h, 1d, 7d,
 * 30d, 90d), falling back to the legacy `?days` query param. Clamped to
 * [1 day .. $maxDays].
 */
function windowSeconds(int $maxDays = 365): int {
    static $map = ['1h' => 3600, '1d' => 86400, '7d' => 604800, '30d' => 2592000, '90d' => 7776000];
    $range = $_GET['range'] ?? null;
    if (is_string($range) && isset($map[$range])) {
        return min($map[$range], $maxDays * 86400);
    }
    $days = max(1, min($maxDays, (int)($_GET['days'] ?? 30)));
    return $days * 86400;
}

/**
 * SQL grouping expression for time-series buckets. Windows of one day or less
 * bucket by hour (ISO prefix "YYYY-MM-DDTHH"); longer windows bucket by day.
 * The returned expression is a constant, never built from user input.
 */
function bucketExpr(int $seconds): string {
    return $seconds <= 86400 ? 'SUBSTRING(ts, 1, 13)' : 'DATE(ts)';
}
