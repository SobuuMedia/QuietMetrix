<?php

/**
 * Unit tests for experimentTwoProportionZTest() -- shares its fixtures with
 * servers/ktor/.../experiments/ExperimentStatsTest.kt; both backends must agree on a given
 * input's z-score/p-value.
 *
 * Run: php php-hosting/tests/experimentStatsTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/experimentStats.php';

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

function checkClose(string $label, float $expected, float $actual, float $tolerance = 1e-4): void {
    check($label, true, abs($expected - $actual) < $tolerance);
}

$r1 = experimentTwoProportionZTest(100, 1000, 130, 1000);
checkClose('100/1000 vs 130/1000 z', 2.1027, $r1['z']);
checkClose('100/1000 vs 130/1000 p_value', 0.0355, $r1['p_value']);
check('100/1000 vs 130/1000 significant', true, $r1['significant']);

$r2 = experimentTwoProportionZTest(50, 500, 52, 500);
checkClose('50/500 vs 52/500 z', 0.2090, $r2['z']);
checkClose('50/500 vs 52/500 p_value', 0.8345, $r2['p_value']);
check('50/500 vs 52/500 not significant', false, $r2['significant']);

$r3 = experimentTwoProportionZTest(200, 1000, 260, 1000);
checkClose('200/1000 vs 260/1000 z', 3.1881, $r3['z']);
checkClose('200/1000 vs 260/1000 p_value', 0.0014, $r3['p_value']);
check('200/1000 vs 260/1000 significant', true, $r3['significant']);

check('0/100 vs 0/100 is null (zero standard error)', null, experimentTwoProportionZTest(0, 100, 0, 100));
check('an empty baseline sample is null', null, experimentTwoProportionZTest(0, 0, 5, 100));
check('an empty challenger sample is null', null, experimentTwoProportionZTest(5, 100, 0, 0));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all experiment-stats assertions passed\n";
