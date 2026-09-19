<?php

/**
 * Regression test: ensureInstalled() must create the `counters` / `counters_quarantine`
 * tables on an EXISTING install (one whose `users` table already exists), not just on a
 * fresh install. schema.sql only ever runs in full when `users` is missing (see
 * ensureInstalled()'s step 1), so a table added to schema.sql after a project has already
 * shipped needs its own createTableIfMissing() guard -- exactly like funnels,
 * funnel_manifests and access_tokens already have. counters/counters_quarantine were added
 * to schema.sql by the aggregate-only ingest migration without that guard, so every
 * pre-existing install's DB never got these tables: counterReadCells() and friends then throw
 * "Table '...counters' doesn't exist", which index.php turns into a 500 on every analytics
 * route (aggregates, transitions, search, friction, sessions).
 *
 * Requires a real MySQL reachable via BOOTSTRAP_TEST_HOST/_NAME/_USER/_PASS env vars (this
 * exercises information_schema + ENGINE=InnoDB DDL that sqlite can't run). Skips (exit 0) if
 * unset, so it stays out of the DB-less CI smoke test.
 *
 * Run: php php-hosting/tests/bootstrapCountersTableTest.php   (exit 0 = pass/skip, 1 = fail)
 */

$testHost = getenv('BOOTSTRAP_TEST_HOST');
if ($testHost === false || $testHost === '') {
    echo "skipped: set BOOTSTRAP_TEST_HOST to run (needs a real MySQL)\n";
    exit(0);
}

define('DB_HOST', $testHost);
define('DB_NAME', getenv('BOOTSTRAP_TEST_NAME') ?: 'quietmetrix');
define('DB_USER', getenv('BOOTSTRAP_TEST_USER') ?: 'root');
define('DB_PASS', getenv('BOOTSTRAP_TEST_PASS') ?: '');
define('DB_CHARSET', 'utf8mb4');

require_once __DIR__ . '/../src/helpers.php';
require_once __DIR__ . '/../src/db.php';
require_once __DIR__ . '/../src/bootstrap.php';

$db = getDb();

$failures = 0;
function check(string $label, $expected, $actual): void {
    global $failures;
    if ($expected === $actual) {
        echo "  ok: $label\n";
        return;
    }
    $failures++;
    echo "FAIL: $label\n";
    echo '    expected: ' . var_export($expected, true) . "\n";
    echo '    actual:   ' . var_export($actual, true) . "\n";
}

// --- fixture: a full install (all tables, including counters/counters_quarantine) --------

$sql = file_get_contents(__DIR__ . '/../schema.sql');
$cleaned = preg_replace('/--[^\r\n]*/', '', $sql);
foreach (explode(';', $cleaned) as $stmt) {
    $stmt = trim($stmt);
    if ($stmt === '') continue;
    $db->exec($stmt);
}

// Simulate a pre-migration existing install: `users` (and everything else) already present,
// but `counters`/`counters_quarantine` are the ones missing -- these are the two tables this
// migration added to schema.sql without a matching createTableIfMissing() guard.
$db->exec('DROP TABLE IF EXISTS counters');
$db->exec('DROP TABLE IF EXISTS counters_quarantine');

check('fixture: users already exists (existing install, not fresh)', true, tableExists($db, 'users'));
check('fixture: counters is missing before ensureInstalled()', false, tableExists($db, 'counters'));
check('fixture: counters_quarantine is missing before ensureInstalled()', false, tableExists($db, 'counters_quarantine'));

// --- the actual bug: ensureInstalled() on this existing install must still create them ---

define('ADMIN_EMAIL', 'admin@example.com');
define('ADMIN_PASSWORD', 'irrelevant-for-this-test');

ensureInstalled();

check('ensureInstalled() creates counters on an existing install', true, tableExists($db, 'counters'));
check('ensureInstalled() creates counters_quarantine on an existing install', true, tableExists($db, 'counters_quarantine'));

// A second run must stay idempotent (CREATE TABLE IF NOT EXISTS semantics via tableExists()).
ensureInstalled();
check('ensureInstalled() is idempotent for counters', true, tableExists($db, 'counters'));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all bootstrap counters-table assertions passed\n";
