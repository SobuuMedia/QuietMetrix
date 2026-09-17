<?php

/**
 * Joins search{screen} and search_zero_result{screen} cells into a per-screen zero-result
 * rate -- the query text itself is never sent (see the SDK's trackSearch), only whether a
 * search on that screen came back empty. Sorted worst-first: the highest zero-result rate is
 * the most actionable row. Mirrors servers/ktor/.../counters/SearchCounterAnalyzer.kt.
 *
 * $searchCells / $zeroResultCells: [['dims' => ['screen' => string], 'n' => int], ...].
 * Returns a list of ['screen' => string, 'total' => int, 'zero_result' => int, 'rate' => ?float].
 */
function searchCounterAnalyze(array $searchCells, array $zeroResultCells): array {
    $totalByScreen = [];
    foreach ($searchCells as $cell) {
        $screen = $cell['dims']['screen'] ?? null;
        if ($screen === null) continue;
        $totalByScreen[$screen] = ($totalByScreen[$screen] ?? 0) + (int)($cell['n'] ?? 0);
    }
    $zeroByScreen = [];
    foreach ($zeroResultCells as $cell) {
        $screen = $cell['dims']['screen'] ?? null;
        if ($screen === null) continue;
        $zeroByScreen[$screen] = ($zeroByScreen[$screen] ?? 0) + (int)($cell['n'] ?? 0);
    }

    $screens = array_unique(array_merge(array_keys($totalByScreen), array_keys($zeroByScreen)));
    $result = [];
    foreach ($screens as $screen) {
        $total = $totalByScreen[$screen] ?? 0;
        $zeroResult = $zeroByScreen[$screen] ?? 0;
        $result[] = [
            'screen' => (string)$screen,
            'total' => $total,
            'zero_result' => $zeroResult,
            'rate' => $total === 0 ? null : (float)$zeroResult / $total,
        ];
    }

    usort($result, static fn(array $a, array $b): int => ($b['rate'] ?? -1.0) <=> ($a['rate'] ?? -1.0));
    return $result;
}
