<?php

/**
 * Returns the singleton PDO handle. Errors are thrown as exceptions; the global
 * exception handler in index.php catches them and returns a sanitised 500.
 */
function getDb(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST
             . ';dbname=' . DB_NAME
             . ';charset=' . DB_CHARSET;
        $pdo = new PDO($dsn, DB_USER, DB_PASS, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}

/** True if the given table exists in the configured database. */
function tableExists(PDO $db, string $table): bool {
    $stmt = $db->prepare(
        'SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ? LIMIT 1'
    );
    $stmt->execute([DB_NAME, $table]);
    return $stmt->fetchColumn() !== false;
}
