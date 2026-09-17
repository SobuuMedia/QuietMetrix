<?php

/**
 * Unit tests for the pure access-token primitives (scope encode/decode, expiry check,
 * token minting shape). Mirrors the api-key threat model — only a sha256 hash is ever
 * meant to be persisted, the plaintext `qm_pat_…` token is generated here and handed
 * back once by the route layer (untested — DB-backed, like handleProjectsCreate()).
 *
 * Run: php php-hosting/tests/accessTokenTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/helpers.php';
require_once __DIR__ . '/../src/accessTokens.php';

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

// --- scope encode/decode round-trip ----------------------------------------------------

check('encodeScopes joins with commas', 'projects:create,projects:read',
    encodeAccessTokenScopes(['projects:create', 'projects:read']));

check('decodeScopes splits on commas', ['projects:create', 'projects:read'],
    decodeAccessTokenScopes('projects:create,projects:read'));

check('decodeScopes trims whitespace', ['projects:create', 'projects:read'],
    decodeAccessTokenScopes('projects:create, projects:read'));

check('decodeScopes drops blank entries', ['projects:create'],
    decodeAccessTokenScopes('projects:create,,'));

check('encodeScopes/decodeScopes round-trip', ['projects:create'],
    decodeAccessTokenScopes(encodeAccessTokenScopes(['projects:create'])));

check('hasAccessTokenScope finds a granted scope', true,
    hasAccessTokenScope('projects:create,projects:read', 'projects:read'));

check('hasAccessTokenScope rejects an ungranted scope', false,
    hasAccessTokenScope('projects:read', 'projects:create'));

// --- token minting -----------------------------------------------------------------------

$minted = mintAccessToken();
check('mintAccessToken produces the qm_pat_ prefix', true,
    str_starts_with($minted['token'], 'qm_pat_'));

check('mintAccessToken last4 matches the token tail', substr($minted['token'], -4),
    $minted['last4']);

check('mintAccessToken hash is sha256 of the token, not the token itself', true,
    $minted['tokenHash'] === hash('sha256', $minted['token']) && $minted['tokenHash'] !== $minted['token']);

check('mintAccessToken hash is a 64-char hex digest', 64, strlen($minted['tokenHash']));

$mintedAgain = mintAccessToken();
check('mintAccessToken tokens are not reused', true, $minted['token'] !== $mintedAgain['token']);

// --- expiry ------------------------------------------------------------------------------

check('accessTokenIsExpired is false for a null expiry', false, accessTokenIsExpired(null));

check('accessTokenIsExpired is false for a future timestamp', false,
    accessTokenIsExpired(gmdate('Y-m-d\TH:i:s\Z', time() + 3600)));

check('accessTokenIsExpired is true for a past timestamp', true,
    accessTokenIsExpired(gmdate('Y-m-d\TH:i:s\Z', time() - 3600)));

// --- row shaping (listAccessTokens) -------------------------------------------------------
// Regression: array `+` union keeps the LEFT side's value on key collision, so
// `$row + ['scopes' => decodeAccessTokenScopes(...)]` silently discarded the decode and
// left 'scopes' as the raw DB string — invisible to any test that doesn't inspect the
// actual returned array shape, which this one does.

$shaped = shapeAccessTokenRow(['id' => '1', 'scopes' => 'projects:create,projects:read']);
check('shapeAccessTokenRow decodes scopes into an array, not a raw string', ['projects:create', 'projects:read'],
    $shaped['scopes']);

check('shapeAccessTokenRow preserves other columns unchanged', '1', $shaped['id']);

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all access-token assertions passed\n";
