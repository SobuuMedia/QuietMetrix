<?php

/**
 * Shapes two Overview fields that have no direct 1:1 counter reading. Mirrors
 * servers/ktor/.../counters/OverviewCounterAnalyzer.kt -- keep the two in sync.
 */

/**
 * Approximates "top screens" from screen_transition{from,to} cells: there is no standalone
 * "screen viewed" counter, so this sums n by destination (to) across every origin (from) a
 * screen was reached from, and returns the top $limit by that sum.
 *
 * $cells: [['dims' => ['from' => string, 'to' => string], 'n' => int], ...] (from
 * counterReadCells()). Returns a list of ['screen' => string, 'n' => int], sorted descending.
 */
function overviewCounterTopScreens(array $cells, int $limit): array {
    $byScreen = [];
    foreach ($cells as $cell) {
        $to = $cell['dims']['to'] ?? null;
        if ($to === null) continue;
        $byScreen[$to] = ($byScreen[$to] ?? 0) + (int)($cell['n'] ?? 0);
    }
    $result = [];
    foreach ($byScreen as $screen => $n) {
        $result[] = ['screen' => (string)$screen, 'n' => $n];
    }
    usort($result, static fn(array $a, array $b): int => $b['n'] <=> $a['n']);
    return array_slice($result, 0, $limit);
}

/** ms(midpoint) per screen_dwell bucket label -- see the SDK's dwellBucket.kt. The open-ended
 *  top bucket has no true midpoint; 450000ms is an arbitrary anchor, not a claim about actual
 *  dwell time in that bucket. */
function overviewCounterBucketMidpointMs(): array {
    return [
        '0_5s' => 2500,
        '5_10s' => 7500,
        '10_30s' => 20000,
        '30_60s' => 45000,
        '60_300s' => 180000,
        '300s_plus' => 450000,
    ];
}

/**
 * Reconstructs an approximate average/total dwell time per screen from
 * screen_dwell{screen,bucket} cells -- exact per-view durations no longer exist server-side
 * under aggregate-only ingest, so avg_ms/total_ms are a weighted average of each bucket's
 * midpoint, not a true mean. count (total dwell observations for the screen) is exact.
 *
 * $cells: [['dims' => ['screen' => string, 'bucket' => string], 'n' => int], ...]. Returns a
 * list of ['screen' => string, 'count' => int, 'avg_ms' => int, 'total_ms' => int], sorted by
 * count descending.
 */
function overviewCounterScreenDurations(array $cells): array {
    $midpoints = overviewCounterBucketMidpointMs();
    $byScreen = [];
    foreach ($cells as $cell) {
        $screen = $cell['dims']['screen'] ?? null;
        if ($screen === null) continue;
        $n = (int)($cell['n'] ?? 0);
        $midpoint = $midpoints[$cell['dims']['bucket'] ?? ''] ?? 0;
        if (!isset($byScreen[$screen])) {
            $byScreen[$screen] = ['count' => 0, 'total_ms' => 0];
        }
        $byScreen[$screen]['count'] += $n;
        $byScreen[$screen]['total_ms'] += $midpoint * $n;
    }

    $result = [];
    foreach ($byScreen as $screen => $agg) {
        $count = $agg['count'];
        $result[] = [
            'screen' => (string)$screen,
            'count' => $count,
            'avg_ms' => $count > 0 ? intdiv($agg['total_ms'], $count) : 0,
            'total_ms' => $agg['total_ms'],
        ];
    }
    usort($result, static fn(array $a, array $b): int => $b['count'] <=> $a['count']);
    return $result;
}
