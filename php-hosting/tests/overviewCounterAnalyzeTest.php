<?php

/**
 * Unit tests for overviewCounterTopScreens()/overviewCounterScreenDurations() -- mirrors
 * servers/ktor/.../counters/OverviewCounterAnalyzerTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/overviewCounterAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/overviewCounterAnalyze.php';

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

function transitionCell(string $from, string $to, int $n): array {
    return ['dims' => ['from' => $from, 'to' => $to], 'n' => $n];
}

function dwellCell(string $screen, string $bucket, int $n): array {
    return ['dims' => ['screen' => $screen, 'bucket' => $bucket], 'n' => $n];
}

// --- overviewCounterTopScreens ---------------------------------------------------------------

$cells = [transitionCell('Home', 'Detail', 10), transitionCell('Search', 'Detail', 5), transitionCell('Home', 'Cart', 3)];
$result = overviewCounterTopScreens($cells, 10);
check('sums n by destination across every origin', [['screen' => 'Detail', 'n' => 15], ['screen' => 'Cart', 'n' => 3]], $result);

$cells = [transitionCell('A', 'X', 3), transitionCell('A', 'Y', 2), transitionCell('A', 'Z', 1)];
check('respects the limit', [['screen' => 'X', 'n' => 3], ['screen' => 'Y', 'n' => 2]], overviewCounterTopScreens($cells, 2));

// --- overviewCounterScreenDurations ------------------------------------------------------------

$cells = [dwellCell('Home', '0_5s', 6), dwellCell('Home', '5_10s', 4)];
$result = overviewCounterScreenDurations($cells);
check('sums bucket counts into a total count per screen', 1, count($result));
check('screen name', 'Home', $result[0]['screen']);
check('total count', 10, $result[0]['count']);

// (6*2500 + 4*7500) / 10 = 4500ms
check('approximates avgMs from bucket midpoints, weighted by count', 4500, $result[0]['avg_ms']);
check('total_ms', 45000, $result[0]['total_ms']);

$buckets = ['0_5s', '5_10s', '10_30s', '30_60s', '60_300s', '300s_plus'];
$cells = array_map(fn($b) => dwellCell('Screen', $b, 1), $buckets);
check('covers every declared bucket label', 6, overviewCounterScreenDurations($cells)[0]['count']);

$cells = [dwellCell('Popular', '0_5s', 100), dwellCell('Rare', '0_5s', 1)];
$result = overviewCounterScreenDurations($cells);
check('sorts by count descending', ['Popular', 'Rare'], array_column($result, 'screen'));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all overview-counter-analyze assertions passed\n";
