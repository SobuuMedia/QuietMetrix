<?php

/**
 * Unit tests for searchCounterAnalyze() -- mirrors
 * servers/ktor/.../counters/SearchCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/searchCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/searchCounterAnalyze.php';

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

function searchCell(string $screen, int $n): array {
    return ['dims' => ['screen' => $screen], 'n' => $n];
}

$result = searchCounterAnalyze([searchCell('Library', 100)], [searchCell('Library', 25)]);
check('computes the zero-result rate per screen (count)', 1, count($result));
check('computes the zero-result rate per screen (screen)', 'Library', $result[0]['screen']);
check('computes the zero-result rate per screen (total)', 100, $result[0]['total']);
check('computes the zero-result rate per screen (zero_result)', 25, $result[0]['zero_result']);
check('computes the zero-result rate per screen (rate)', 0.25, $result[0]['rate']);

$result = searchCounterAnalyze([searchCell('Library', 50)], []);
check('a screen with searches but no zero-result cell reports a zero rate (zero_result)', 0, $result[0]['zero_result']);
check('a screen with searches but no zero-result cell reports a zero rate (rate)', 0.0, $result[0]['rate']);

$result = searchCounterAnalyze([], [searchCell('Library', 1)]);
check('a screen with zero total searches reports a null rate (count)', 1, count($result));
check('a screen with zero total searches reports a null rate (rate)', null, $result[0]['rate']);

$result = searchCounterAnalyze(
    [searchCell('Good', 100), searchCell('Bad', 100)],
    [searchCell('Good', 5), searchCell('Bad', 80)],
);
check('sorts worst-first by zero-result rate descending', ['Bad', 'Good'], array_column($result, 'screen'));

check('an empty range reports no results', [], searchCounterAnalyze([], []));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all search-counter-analyze assertions passed\n";
