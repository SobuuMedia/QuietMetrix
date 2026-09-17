<?php

/**
 * Unit tests for activationCounterAnalyze() -- mirrors
 * servers/ktor/.../counters/ActivationCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/activationCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/activationCounterAnalyze.php';

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

function activationCell(string $cohort, int $n): array {
    return ['cohort' => $cohort, 'n' => $n];
}

$result = activationCounterAnalyze([activationCell('2026-W36', 30)], ['2026-W36' => 100]);
check('computes activation rate against the cohort size (count)', 1, count($result));
check('computes activation rate against the cohort size (cohort)', '2026-W36', $result[0]['cohort']);
check('computes activation rate against the cohort size (size)', 100, $result[0]['size']);
check('computes activation rate against the cohort size (activated)', 30, $result[0]['activated']);
check('computes activation rate against the cohort size (rate)', 0.3, $result[0]['rate']);

$result = activationCounterAnalyze([], ['2026-W36' => 50]);
check('a cohort with no activation cells still appears, with a zero rate (activated)', 0, $result[0]['activated']);
check('a cohort with no activation cells still appears, with a zero rate (rate)', 0.0, $result[0]['rate']);

$result = activationCounterAnalyze([], ['2026-W36' => 0]);
check('a zero-size cohort reports a null rate rather than dividing by zero', null, $result[0]['rate']);

$result = activationCounterAnalyze(
    [activationCell('2026-W37', 5), activationCell('2026-W36', 10)],
    ['2026-W36' => 20, '2026-W37' => 20],
);
check('distinct cohorts sort by cohort label', ['2026-W36', '2026-W37'], array_column($result, 'cohort'));
check('distinct cohorts sum activations independently (W36)', 10, $result[0]['activated']);
check('distinct cohorts sum activations independently (W37)', 5, $result[1]['activated']);

$result = activationCounterAnalyze(
    [activationCell('2026-W36', 10), activationCell('2026-W36', 5)],
    ['2026-W36' => 30],
);
check('multiple cells for the same cohort sum together', 15, $result[0]['activated']);

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all activation-counter-analyze assertions passed\n";
