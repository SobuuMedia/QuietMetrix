<?php

/**
 * The counter-based replacement for funnelAnalyze(). Mirrors
 * servers/ktor/.../funnels/FunnelCounterAnalyzer.kt -- keep the two in sync.
 *
 * Instead of matching a raw event trail per actor, this aggregates the funnel_step{f, rev,
 * step} cells the device itself already reduced its progress to (see the SDK's
 * FunnelEvaluator) -- each device reports one counter increment per new depth it reaches, so
 * summing n at each depth directly gives every step's count. There is no per-actor
 * reconstruction here because there is nothing to reconstruct: the trail never left the
 * device.
 *
 * $cells: [['step' => int, 'n' => int], ...] -- already filtered to one funnel key and k-anon
 * gated by counterReadCells(). $steps: [['key' => string, 'name'? => string, ...], ...].
 *
 * What this cannot compute, because the underlying data no longer exists server-side:
 * per-step timing (median_ms_from_previous / p90_ms_from_previous / median_total_ms), a
 * dimensional breakdown, and a trend -- all null. A day-bucketed trend is a plausible future
 * addition (the counters table's day column is already there; counterReadCells just collapses
 * it today), the others are gone for good under aggregate-only ingest.
 *
 * Revision note: cells are not filtered by the funnel manifest's rev dim here -- funnels
 * currently have no per-row stored revision to filter against server-side, so a funnel
 * redefinition (different step count) blends old and new depth counts under the same step
 * index. Acceptable for now; flagged rather than silently wrong.
 */
function funnelCounterAnalyze(array $steps, array $cells, string $countMode = 'actor'): array {
    if (empty($steps)) {
        return [
            'entered' => 0,
            'converted' => 0,
            'overall_conversion' => 0.0,
            'median_total_ms' => null,
            'steps' => [],
            'breakdown' => null,
            'trend' => null,
            'counted_by' => funnelCounterCountedBy($countMode),
        ];
    }

    $counts = array_fill(0, count($steps), 0);
    foreach ($cells as $cellRow) {
        $index = ($cellRow['step'] ?? 0) - 1;
        if ($index >= 0 && $index < count($steps)) {
            $counts[$index] += (int)($cellRow['n'] ?? 0);
        }
    }

    $entered = $counts[0];
    $converted = $counts[count($counts) - 1];

    $stepResults = [];
    foreach ($steps as $i => $step) {
        $count = $counts[$i];
        $previousCount = $i === 0 ? $entered : $counts[$i - 1];
        $stepResults[] = [
            'key' => $step['key'],
            'name' => $step['name'] ?? null,
            'count' => $count,
            'conversion_from_entry' => funnelCounterSafeDiv($count, $entered),
            // The entry step has no previous step; 0 is deliberate so clients do not display
            // a misleading "100% from previous" on an empty funnel.
            'conversion_from_previous' => $i === 0 ? 0.0 : funnelCounterSafeDiv($count, $previousCount),
            'dropped' => $previousCount - $count,
            'drop_rate' => $i === 0 ? 0.0 : funnelCounterSafeDiv($previousCount - $count, $previousCount),
            'median_ms_from_previous' => null,
            'p90_ms_from_previous' => null,
        ];
    }

    return [
        'entered' => $entered,
        'converted' => $converted,
        'overall_conversion' => funnelCounterSafeDiv($converted, $entered),
        'median_total_ms' => null,
        'steps' => $stepResults,
        'breakdown' => null,
        'trend' => null,
        'counted_by' => funnelCounterCountedBy($countMode),
    ];
}

function funnelCounterCountedBy(string $countMode): string {
    return $countMode === 'attempt' ? 'install attempt' : 'install';
}

function funnelCounterSafeDiv(int $numerator, int $denominator): float {
    return $denominator === 0 ? 0.0 : $numerator / $denominator;
}
