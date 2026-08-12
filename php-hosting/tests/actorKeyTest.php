<?php

/**
 * Unit tests for actorKey() — the unit of conversion for funnel matching.
 * Mirrors servers/ktor/.../funnels/ActorKey.kt; keep the two in sync.
 *
 * Run: php php-hosting/tests/actorKeyTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/funnelActor.php';

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

check('prefers the install hash when present', 'abc123',
    actorKey('abc123', 'sess-1'));

check('falls back to a prefixed session id when install hash is absent', 'sid:sess-1',
    actorKey(null, 'sess-1'));

check('falls back to session id when install hash is blank', 'sid:sess-1',
    actorKey('', 'sess-1'));

check('is null when both are absent', null,
    actorKey(null, null));

check('is null when both are blank', null,
    actorKey('', ''));

check('install hash never collides with a session-id fallback (by session)', 'sid:abc123',
    actorKey(null, 'abc123'));

check('install hash never collides with a session-id fallback (by hash)', 'abc123',
    actorKey('abc123', 'sess-1'));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all actorKey assertions passed\n";
