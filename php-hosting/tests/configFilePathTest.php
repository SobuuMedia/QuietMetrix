<?php

/**
 * Regression test for the production incident of 2026-09-19: a local test/e2e
 * config.php (DB_HOST=127.0.0.1:3307, DB_NAME=quietmetrix_e2e) was manually FTP'd
 * over the real production config.php, taking the whole site down with
 * "SQLSTATE[HY000] [2002] Connection refused" on every route -- ensureInstalled()
 * calls getDb() before any route dispatch, so a bad config.php breaks everything,
 * not just one endpoint.
 *
 * configFilePath() lets local/e2e testing point at a distinctly-named file (e.g.
 * config.e2e.php) via the QM_CONFIG_FILE env var, so a file literally named
 * config.php never has to exist in a working copy used for local testing --
 * removing the whole class of "which config.php did I just upload" mistakes.
 * index.php and diag.php must always resolve the config path through this
 * function rather than hardcoding 'config.php', or this protection is bypassed.
 *
 * Run: php php-hosting/tests/configFilePathTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/helpers.php';

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

putenv('QM_CONFIG_FILE'); // ensure a clean slate between checks
check('defaults to config.php when QM_CONFIG_FILE is unset', 'config.php', configFilePath());

putenv('QM_CONFIG_FILE='); // set-but-empty must not silently resolve to an empty require path
check('falls back to config.php when QM_CONFIG_FILE is empty', 'config.php', configFilePath());

putenv('QM_CONFIG_FILE=config.e2e.php');
check('honours a QM_CONFIG_FILE override', 'config.e2e.php', configFilePath());
putenv('QM_CONFIG_FILE'); // clean up for any test that runs after this one in the same process

// Guards against a config path escaping the php-hosting/ directory (e.g. an env
// var accidentally set to '../../../etc/passwd' or an absolute path elsewhere).
putenv('QM_CONFIG_FILE=../secrets/config.php');
check('rejects a path containing a directory separator', 'config.php', configFilePath());
putenv('QM_CONFIG_FILE');

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all configFilePath assertions passed\n";
