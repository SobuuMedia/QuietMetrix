<?php

/**
 * Unit tests for funnelCounterAnalyze() -- mirrors
 * servers/ktor/.../funnels/FunnelCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/funnelCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/funnelCounterAnalyze.php';

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

function steps(int $n): array {
    $out = [];
    for ($i = 1; $i <= $n; $i++) $out[] = ['key' => "step$i", 'event' => "e$i"];
    return $out;
}

function cell(int $step, int $n): array {
    return ['step' => $step, 'n' => $n];
}

// --- counts at each depth become each step's count ----------------------------------------
$result = funnelCounterAnalyze(steps(3), [cell(1, 100), cell(2, 60), cell(3, 20)]);
check('counts at each depth', [100, 60, 20], array_column($result['steps'], 'count'));
check('entered', 100, $result['entered']);
check('converted', 20, $result['converted']);

// --- conversion and drop rates --------------------------------------------------------------
$result = funnelCounterAnalyze(steps(3), [cell(1, 100), cell(2, 50), cell(3, 25)]);
check('conversion_from_entry step0', 1.0, $result['steps'][0]['conversion_from_entry']);
check('conversion_from_entry step1', 0.5, $result['steps'][1]['conversion_from_entry']);
check('conversion_from_entry step2', 0.25, $result['steps'][2]['conversion_from_entry']);
check('conversion_from_previous step0', 0.0, $result['steps'][0]['conversion_from_previous']);
check('conversion_from_previous step1', 0.5, $result['steps'][1]['conversion_from_previous']);
check('dropped step0', 0, $result['steps'][0]['dropped']);
check('dropped step1', 50, $result['steps'][1]['dropped']);
check('drop_rate step1', 0.5, $result['steps'][1]['drop_rate']);

// --- overall conversion ----------------------------------------------------------------------
$result = funnelCounterAnalyze(steps(2), [cell(1, 40), cell(2, 10)]);
check('overall_conversion', 0.25, $result['overall_conversion']);

// --- a step never reached counts as zero ------------------------------------------------------
$result = funnelCounterAnalyze(steps(3), [cell(1, 10)]);
check('unreached steps are zero', [10, 0, 0], array_column($result['steps'], 'count'));
check('overall_conversion when never converted', 0.0, $result['overall_conversion']);

// --- an out-of-range step index is ignored ----------------------------------------------------
$result = funnelCounterAnalyze(steps(2), [cell(1, 10), cell(5, 3)]);
check('out-of-range cell ignored', [10, 0], array_column($result['steps'], 'count'));

// --- no timing or breakdown data is available from counters ------------------------------------
$result = funnelCounterAnalyze(steps(2), [cell(1, 5)]);
check('median_total_ms is null', null, $result['median_total_ms']);
check('breakdown is null', null, $result['breakdown']);
check('trend is null', null, $result['trend']);
check('median_ms_from_previous is null', null, $result['steps'][0]['median_ms_from_previous']);
check('p90_ms_from_previous is null', null, $result['steps'][0]['p90_ms_from_previous']);

// --- counted_by reflects attempt mode -----------------------------------------------------------
check('counted_by actor', 'install', funnelCounterAnalyze(steps(1), [], 'actor')['counted_by']);
check('counted_by attempt', 'install attempt', funnelCounterAnalyze(steps(1), [], 'attempt')['counted_by']);

// --- an empty step list produces an empty, zeroed result ------------------------------------------
$result = funnelCounterAnalyze([], [cell(1, 10)]);
check('empty steps: entered', 0, $result['entered']);
check('empty steps: converted', 0, $result['converted']);
check('empty steps: overall_conversion', 0.0, $result['overall_conversion']);
check('empty steps: steps list', [], $result['steps']);

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all funnel-counter-analyze assertions passed\n";
