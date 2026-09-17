<?php

/**
 * Joins activation{cohort} cells against $cohortSizes -- each cohort's device count from
 * retention's day="0" signal (see retentionCounterAnalyze()) -- so an activation rate has the
 * identical denominator a retention rate for the same cohort would. Every cohort in
 * $cohortSizes appears in the result, even with zero activations. Mirrors
 * servers/ktor/.../counters/ActivationCounterAnalyzer.kt -- keep the two in sync.
 *
 * $cells: [['cohort' => string, 'n' => int], ...] (from counterReadCells()).
 * $cohortSizes: [cohort => size, ...].
 * Returns a list of ['cohort' => string, 'size' => int, 'activated' => int, 'rate' => ?float],
 * sorted by cohort label.
 */
function activationCounterAnalyze(array $cells, array $cohortSizes): array {
    $activatedByCohort = [];
    foreach ($cells as $cell) {
        $cohort = $cell['cohort'] ?? null;
        if ($cohort === null) continue;
        $activatedByCohort[$cohort] = ($activatedByCohort[$cohort] ?? 0) + (int)($cell['n'] ?? 0);
    }

    $result = [];
    foreach ($cohortSizes as $cohort => $size) {
        $activated = $activatedByCohort[$cohort] ?? 0;
        $result[] = [
            'cohort' => (string)$cohort,
            'size' => $size,
            'activated' => $activated,
            'rate' => $size === 0 ? null : (float)$activated / $size,
        ];
    }

    usort($result, static fn(array $a, array $b): int => $a['cohort'] <=> $b['cohort']);
    return $result;
}
