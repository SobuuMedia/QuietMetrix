<?php

/**
 * Unit tests for the pure session-scope predicates in src/auth.php. requireAnalyticsAccess()
 * itself is untested here — like requireProjectAccess()/requireSessionOrToken(), it calls
 * exit() on failure and hits the DB, so it's exercised for real only via a live server (see
 * docs/agents/setup.md's verification steps), the same category as handleProjectsCreate().
 *
 * Run: php php-hosting/tests/authScopeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/auth.php';

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

// --- sessionCanReadAnalytics ---------------------------------------------------------------

check('a dashboard session (no scopes key) can always read analytics', true,
    sessionCanReadAnalytics(['sub' => '1', 'role' => 'developer']));

check('a PAT with analytics:read can read analytics', true,
    sessionCanReadAnalytics(['sub' => '1', 'scopes' => ['analytics:read']]));

check('a PAT with only projects:read cannot read analytics', false,
    sessionCanReadAnalytics(['sub' => '1', 'scopes' => ['projects:read']]));

check('a PAT with only projects:create cannot read analytics', false,
    sessionCanReadAnalytics(['sub' => '1', 'scopes' => ['projects:create']]));

check('a PAT with no scopes at all cannot read analytics', false,
    sessionCanReadAnalytics(['sub' => '1', 'scopes' => []]));

check('a PAT with analytics:read among other scopes can read analytics', true,
    sessionCanReadAnalytics(['sub' => '1', 'scopes' => ['projects:create', 'analytics:read']]));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all auth-scope assertions passed\n";
