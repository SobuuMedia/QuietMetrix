<?php

/**
 * Unit tests for frictionCounterAnalyze() -- mirrors
 * servers/ktor/.../counters/FrictionCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/frictionCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/frictionCounterAnalyze.php';

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

function frictionCell(string $screen, string $kind, int $n): array {
    return ['dims' => ['screen' => $screen, 'kind' => $kind], 'n' => $n];
}

$cells = [frictionCell('Checkout', 'rage_tap', 5), frictionCell('Checkout', 'rage_tap', 3)];
$result = frictionCounterAnalyze($cells);
check('sums rage taps per screen (count)', 1, count($result));
check('sums rage taps per screen (screen)', 'Checkout', $result[0]['screen']);
check('sums rage taps per screen (rage_taps)', 8, $result[0]['rage_taps']);

$cells = [frictionCell('Checkout', 'rage_tap', 5), frictionCell('Home', 'rage_tap', 2)];
$result = frictionCounterAnalyze($cells);
$byScreen = [];
foreach ($result as $r) { $byScreen[$r['screen']] = $r['rage_taps']; }
ksort($byScreen);
check('distinct screens produce distinct results', ['Checkout' => 5, 'Home' => 2], $byScreen);

$cells = [frictionCell('Quiet', 'rage_tap', 1), frictionCell('Loud', 'rage_tap', 50)];
check('sorts by rage-tap count descending', ['Loud', 'Quiet'], array_column(frictionCounterAnalyze($cells), 'screen'));

check('an empty range reports no results', [], frictionCounterAnalyze([]));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all friction-counter-analyze assertions passed\n";
