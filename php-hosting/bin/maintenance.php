<?php
/** Trusted CLI only: cron every minute. No public HTTP maintenance endpoint. */
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
require_once __DIR__ . '/../src/helpers.php';
require_once __DIR__ . '/../' . configFilePath();
require_once __DIR__ . '/../src/db.php';
require_once __DIR__ . '/../src/auth.php';
require_once __DIR__ . '/../src/maintenance.php';
require_once __DIR__ . '/../src/ai.php';
try {
    $deleted = dataMaintenance(getDb(), true);
    aiCleanup(getDb());
    echo "Maintenance completed; expired rows removed: $deleted\n";
} catch (Throwable $e) {
    fwrite(STDERR, 'Maintenance failed: ' . get_class($e) . "\n");
    exit(1);
}
