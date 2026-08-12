<?php

/**
 * Unit tests for resolveCorsOrigin() — the pure origin-resolution behind the
 * Access-Control-Allow-Origin header. No web server or `header()` needed.
 *
 * Run: php php-hosting/tests/corsTest.php   (exit 0 = pass, 1 = fail)
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

$dash = 'https://quietmetrix.getsobuu.com';
$list = 'https://devweb.getsobuu.com,https://getsobuu.com,https://www.getsobuu.com';

check('echoes an allowlisted dev origin', 'https://devweb.getsobuu.com',
    resolveCorsOrigin('https://devweb.getsobuu.com', $dash, $list));
check('echoes the prod apex origin', 'https://getsobuu.com',
    resolveCorsOrigin('https://getsobuu.com', $dash, $list));
check('echoes the prod www origin', 'https://www.getsobuu.com',
    resolveCorsOrigin('https://www.getsobuu.com', $dash, $list));
check('unlisted origin falls back to the pinned origin (not echoed)', $dash,
    resolveCorsOrigin('https://evil.example.com', $dash, $list));
check('null request origin falls back to the pinned origin', $dash,
    resolveCorsOrigin(null, $dash, $list));
check('no allowlist keeps legacy single-origin behaviour', $dash,
    resolveCorsOrigin('https://devweb.getsobuu.com', $dash, ''));
check('wildcard as the single pin is honoured', '*',
    resolveCorsOrigin('https://devweb.getsobuu.com', '*', $list));
check('wildcard inside the allowlist means any origin', '*',
    resolveCorsOrigin('https://anything.example.com', $dash, '*'));
check('allowlist entries are whitespace-trimmed', 'https://getsobuu.com',
    resolveCorsOrigin('https://getsobuu.com', $dash, ' https://getsobuu.com , https://www.getsobuu.com '));
check('empty pinned origin + unlisted request yields null (no header)', null,
    resolveCorsOrigin('https://evil.example.com', '', $list));

// --- ingest origin policy (isIngestOriginAllowed) --------------------------
check('ingest: allowlisted browser origin is accepted', true,
    isIngestOriginAllowed('https://devweb.getsobuu.com', $list));
check('ingest: unlisted browser origin is rejected', false,
    isIngestOriginAllowed('https://evil.example.com', $list));
check('ingest: wildcard accepts any origin', true,
    isIngestOriginAllowed('https://evil.example.com', '*'));
check('ingest: empty allowlist is not enforced', true,
    isIngestOriginAllowed('https://evil.example.com', ''));
check('ingest: no Origin (mobile SDK) is accepted', true,
    isIngestOriginAllowed(null, $list));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all CORS origin assertions passed\n";
