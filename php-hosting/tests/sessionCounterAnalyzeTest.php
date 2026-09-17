<?php

/**
 * Unit tests for sessionCounterAnalyze() -- mirrors
 * servers/ktor/.../counters/SessionCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/sessionCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/sessionCounterAnalyze.php';

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

function sessionCell(string $bucket, int $n): array {
    return ['dims' => ['bucket' => $bucket], 'n' => $n];
}

$cells = [sessionCell('0_10s', 3), sessionCell('30_60s', 2)];
$result = sessionCounterAnalyze($cells);
check('totalSessions sums n across every bucket', 5, $result['total_sessions']);

// (3*5000 + 2*45000) / 5 = 21000ms = 21s
check('avgDurationSec approximates from bucket midpoints, weighted by count', 21, $result['avg_duration_sec']);

$result = sessionCounterAnalyze([]);
check('an empty range reports zero rather than dividing by zero (total)', 0, $result['total_sessions']);
check('an empty range reports zero rather than dividing by zero (avg)', 0, $result['avg_duration_sec']);

$buckets = ['0_10s', '10_30s', '30_60s', '60_300s', '300_900s', '900s_plus'];
$cells = array_map(fn($b) => sessionCell($b, 1), $buckets);
check('covers every declared bucket label', 6, sessionCounterAnalyze($cells)['total_sessions']);

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all session-counter-analyze assertions passed\n";
