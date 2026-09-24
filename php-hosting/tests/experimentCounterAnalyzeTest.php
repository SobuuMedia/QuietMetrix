<?php

/**
 * Unit tests for experimentCounterAnalyze() -- mirrors
 * servers/ktor/.../experiments/ExperimentCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/experimentCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/experimentCounterAnalyze.php';

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

function cell(string $exp, string $variant, string $action, int $n): array {
    return ['dims' => ['exp' => $exp, 'variant' => $variant, 'action' => $action], 'n' => $n];
}

function experiment(string $key = 'checkout_cta'): array {
    return [
        'experiment_key' => $key,
        'variants' => [['name' => 'a', 'weight' => 50], ['name' => 'b', 'weight' => 50]],
    ];
}

// --- both variants present with enough sample ----------------------------------------------
$analysis1 = experimentCounterAnalyze(experiment(), [
    cell('checkout_cta', 'a', 'impression', 1000),
    cell('checkout_cta', 'a', 'interaction', 100),
    cell('checkout_cta', 'b', 'impression', 1000),
    cell('checkout_cta', 'b', 'interaction', 130),
]);
check('variant a rate', 0.1, $analysis1['variants'][0]['rate']);
check('variant b rate', 0.13, $analysis1['variants'][1]['rate']);
check('comparison is present', true, $analysis1['comparison'] !== null);
check('comparison baseline is a', 'a', $analysis1['comparison']['baseline']);
check('comparison challenger is b', 'b', $analysis1['comparison']['challenger']);
check('comparison is significant', true, $analysis1['comparison']['significant']);
check('winner is b', 'b', $analysis1['comparison']['winner']);

// --- a missing interaction cell yields a null rate and no comparison -----------------------
$analysis2 = experimentCounterAnalyze(experiment(), [
    cell('checkout_cta', 'a', 'impression', 1000),
    cell('checkout_cta', 'a', 'interaction', 100),
    cell('checkout_cta', 'b', 'impression', 1000),
]);
check('variant b impressions present', 1000, $analysis2['variants'][1]['impressions']);
check('variant b interactions is null', null, $analysis2['variants'][1]['interactions']);
check('variant b rate is null', null, $analysis2['variants'][1]['rate']);
check('no comparison when a rate is null', null, $analysis2['comparison']);

// --- below the minimum sample size yields no comparison -------------------------------------
$analysis3 = experimentCounterAnalyze(experiment(), [
    cell('checkout_cta', 'a', 'impression', 29),
    cell('checkout_cta', 'a', 'interaction', 5),
    cell('checkout_cta', 'b', 'impression', 1000),
    cell('checkout_cta', 'b', 'interaction', 130),
]);
check('variant a rate present despite low sample', true, $analysis3['variants'][0]['rate'] !== null);
check('no comparison below minimum sample', null, $analysis3['comparison']);

// --- cells for another experiment key are ignored -------------------------------------------
$analysis4 = experimentCounterAnalyze(experiment(), [
    cell('other_experiment', 'a', 'impression', 1000),
    cell('other_experiment', 'a', 'interaction', 500),
]);
check('cells for another experiment are ignored (impressions null)', null, $analysis4['variants'][0]['impressions']);
check('cells for another experiment are ignored (rate null)', null, $analysis4['variants'][0]['rate']);

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all experiment-counter-analyze assertions passed\n";
