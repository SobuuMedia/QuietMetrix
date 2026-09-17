<?php

/**
 * Aggregates retention{cohort, day} cells (see the SDK's RetentionReporter) into per-cohort
 * size and day-N percentages. Mirrors servers/ktor/.../retention/RetentionCounterAnalyzer.kt
 * -- keep the two in sync.
 *
 * $cells: [['cohort' => string, 'day' => int, 'n' => int], ...] -- already k-anonymity gated
 * by counterReadCells(). day=0 is the cohort-size signal; every device reports it once, on
 * first launch. Every other tracked day (1/3/7/14/30) is an "at least N days later" mark, so
 * a cohort's day-N count can never exceed its size -- both come from the same persisted
 * per-device signal.
 *
 * Returns a list of ['cohort' => string, 'size' => int, 'day1' => ?float, 'day3' => ?float,
 * 'day7' => ?float, 'day14' => ?float, 'day30' => ?float], sorted by cohort label.
 */
function retentionCounterAnalyze(array $cells): array {
    $byCohort = [];
    foreach ($cells as $cellRow) {
        $cohort = $cellRow['cohort'] ?? null;
        if ($cohort === null) continue;
        $byCohort[$cohort][(int)($cellRow['day'] ?? -1)] = (int)($cellRow['n'] ?? 0);
    }

    $result = [];
    foreach ($byCohort as $cohort => $byDay) {
        $size = $byDay[0] ?? 0;
        $result[] = [
            'cohort' => $cohort,
            'size' => $size,
            'day1' => retentionCounterRatio($byDay[1] ?? null, $size),
            'day3' => retentionCounterRatio($byDay[3] ?? null, $size),
            'day7' => retentionCounterRatio($byDay[7] ?? null, $size),
            'day14' => retentionCounterRatio($byDay[14] ?? null, $size),
            'day30' => retentionCounterRatio($byDay[30] ?? null, $size),
        ];
    }

    usort($result, static fn(array $a, array $b): int => $a['cohort'] <=> $b['cohort']);
    return $result;
}

function retentionCounterRatio(?int $n, int $size): ?float {
    if ($n === null || $size === 0) return null;
    return $n / $size;
}
