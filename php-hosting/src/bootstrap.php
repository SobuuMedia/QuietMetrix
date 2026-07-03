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
    // events: device classification + time-on-screen. Existing installs created
    // before these columns existed must gain them, or inserts that reference them
    // fail and every tracked event 500s (silently dropping duration_ms/device_class).
    addColumnIfMissing($db, 'events', 'device_class', 'VARCHAR(20) NULL');
    addColumnIfMissing($db, 'events', 'duration_ms', 'BIGINT NULL');

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
