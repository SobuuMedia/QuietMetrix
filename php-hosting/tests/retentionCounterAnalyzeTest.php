<?php

/**
 * Unit tests for retentionCounterAnalyze() -- mirrors
 * servers/ktor/.../retention/RetentionCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/retentionCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/retentionCounterAnalyze.php';

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

function rcell(string $cohort, int $day, int $n): array {
    return ['cohort' => $cohort, 'day' => $day, 'n' => $n];
}

// --- size comes from the day-0 cell -----------------------------------------------------
$result = retentionCounterAnalyze([rcell('2026-W36', 0, 100)]);
check('size from day-0', 100, $result[0]['size']);

// --- day-N percentages are n over the cohort size -----------------------------------------
$result = retentionCounterAnalyze([rcell('2026-W36', 0, 100), rcell('2026-W36', 1, 40), rcell('2026-W36', 7, 10)]);
check('day1 percentage', 0.4, $result[0]['day1']);
check('day7 percentage', 0.1, $result[0]['day7']);
check('day3 is null', null, $result[0]['day3']);
check('day14 is null', null, $result[0]['day14']);
check('day30 is null', null, $result[0]['day30']);

// --- a cohort with no day-0 cell has null percentages, not a divide-by-zero ------------------
$result = retentionCounterAnalyze([rcell('2026-W36', 7, 10)]);
check('size when no day-0 cell', 0, $result[0]['size']);
check('day7 is null when size is zero', null, $result[0]['day7']);

// --- multiple cohorts are kept separate and sorted by cohort label ---------------------------
$result = retentionCounterAnalyze([rcell('2026-W37', 0, 50), rcell('2026-W36', 0, 100)]);
check('cohorts sorted by label', ['2026-W36', '2026-W37'], array_column($result, 'cohort'));

// --- an empty cell list produces an empty result ----------------------------------------------
check('empty input', [], retentionCounterAnalyze([]));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all retention-counter-analyze assertions passed\n";
