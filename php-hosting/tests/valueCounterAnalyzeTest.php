<?php

/**
 * Unit tests for valueCounterAnalyze() -- mirrors
 * servers/ktor/.../counters/ValueCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/valueCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/valueCounterAnalyze.php';

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

function valueCell(string $name, int $n): array {
    return ['dims' => ['name' => $name], 'n' => $n];
}

$cells = [valueCell('purchase', 499), valueCell('purchase', 999)];
$result = valueCounterAnalyze($cells);
check('sums amounts per name (count)', 1, count($result));
check('sums amounts per name (name)', 'purchase', $result[0]['name']);
check('sums amounts per name (total)', 1498, $result[0]['total_minor_units']);

$cells = [valueCell('purchase', 499), valueCell('subscription', 999)];
$result = valueCounterAnalyze($cells);
$byName = [];
foreach ($result as $r) { $byName[$r['name']] = $r['total_minor_units']; }
ksort($byName);
check('distinct names produce distinct results', ['purchase' => 499, 'subscription' => 999], $byName);

$cells = [valueCell('small', 100), valueCell('big', 100000)];
check('sorts by total amount descending', ['big', 'small'], array_column(valueCounterAnalyze($cells), 'name'));

check('an empty range reports no results', [], valueCounterAnalyze([]));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all value-counter-analyze assertions passed\n";
