<?php

/**
 * Shapes /sessions from session{bucket} cells (see the SDK's SessionTracker/sessionBucket()) --
 * no per-session duration exists server-side under aggregate-only ingest, so avg_duration_sec
 * is a weighted average of each bucket's midpoint, not a true mean. total_sessions is exact.
 * Mirrors servers/ktor/.../counters/SessionCounterAnalyzer.kt -- keep the two in sync.
 */

/** ms(midpoint) per session bucket label -- see the SDK's SessionBucket.kt. The open-ended
 *  top bucket has no true midpoint; 1800000ms is an arbitrary anchor, not a claim about actual
 *  session length in that bucket. */
function sessionCounterBucketMidpointMs(): array {
    return [
        '0_10s' => 5000,
        '10_30s' => 20000,
        '30_60s' => 45000,
        '60_300s' => 180000,
        '300_900s' => 600000,
        '900s_plus' => 1800000,
    ];
}

/**
 * $cells: [['dims' => ['bucket' => string], 'n' => int], ...] (from counterReadCells()).
 * Returns ['total_sessions' => int, 'avg_duration_sec' => int].
 */
function sessionCounterAnalyze(array $cells): array {
    $midpoints = sessionCounterBucketMidpointMs();
    $total = 0;
    $totalMs = 0;
    foreach ($cells as $cell) {
        $n = (int)($cell['n'] ?? 0);
        $total += $n;
        $totalMs += ($midpoints[$cell['dims']['bucket'] ?? ''] ?? 0) * $n;
    }
    if ($total === 0) {
        return ['total_sessions' => 0, 'avg_duration_sec' => 0];
    }
    return [
        'total_sessions' => $total,
        'avg_duration_sec' => intdiv(intdiv($totalMs, $total), 1000),
    ];
}
