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
