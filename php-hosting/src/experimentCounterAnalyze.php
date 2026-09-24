<?php

require_once __DIR__ . '/experimentStats.php';

/**
 * Turns `experiment{exp, variant, action}` counter cells into a per-variant comparison,
 * mirrored exactly by servers/ktor/.../experiments/ExperimentCounterAnalyzer.kt.
 *
 * $experiment: ['experiment_key' => string, 'variants' => [['name','weight'], ...]] (or the
 * full row from findExperimentRow() -- only 'variants' is read, already JSON-decoded).
 * $cells: rows from counterReadCells($projectId, 'experiment', ...), i.e.
 * [['dims' => ['exp'=>..,'variant'=>..,'action'=>..], 'n' => int], ...] -- already k-anon gated.
 *
 * Returns ['variants' => [['name','impressions','interactions','rate'], ...],
 * 'comparison' => ['baseline','challenger','z','p_value','significant','winner'] | null].
 * A null impressions/interactions/rate means the underlying cell is hidden by k-anonymity or
 * has no data yet -- never reported as 0.
 */
const EXPERIMENT_MIN_SAMPLE = 30;

function experimentCounterAnalyze(array $experiment, array $cells): array {
    $experimentKey = $experiment['experiment_key'] ?? ($experiment['key'] ?? '');
    $variants = $experiment['variants'] ?? [];

    $byVariantAction = [];
    foreach ($cells as $cell) {
        $dims = $cell['dims'] ?? [];
        if (($dims['exp'] ?? null) !== $experimentKey) continue;
        $variant = $dims['variant'] ?? null;
        $action = $dims['action'] ?? null;
        if ($variant === null || $action === null) continue;
        $mapKey = $variant . '|' . $action;
        $byVariantAction[$mapKey] = ($byVariantAction[$mapKey] ?? 0) + (int)($cell['n'] ?? 0);
    }

    $results = [];
    foreach ($variants as $v) {
        $name = $v['name'] ?? '';
        $impressions = $byVariantAction["$name|impression"] ?? null;
        $interactions = $byVariantAction["$name|interaction"] ?? null;
        $rate = ($impressions !== null && $interactions !== null && $impressions > 0)
            ? $interactions / $impressions
            : null;
        $results[] = ['name' => $name, 'impressions' => $impressions, 'interactions' => $interactions, 'rate' => $rate];
    }

    $comparison = experimentBuildComparison($results);
    return ['variants' => $results, 'comparison' => $comparison];
}

function experimentBuildComparison(array $results): ?array {
    if (count($results) !== 2) return null;
    [$baseline, $challenger] = $results;
    if ($baseline['rate'] === null || $challenger['rate'] === null) return null;
    if (($baseline['impressions'] ?? 0) < EXPERIMENT_MIN_SAMPLE || ($challenger['impressions'] ?? 0) < EXPERIMENT_MIN_SAMPLE) return null;

    $z = experimentTwoProportionZTest(
        (int)$baseline['interactions'], (int)$baseline['impressions'],
        (int)$challenger['interactions'], (int)$challenger['impressions'],
    );
    if ($z === null) return null;

    $winner = null;
    if ($z['significant']) {
        $winner = $challenger['rate'] > $baseline['rate'] ? $challenger['name'] : $baseline['name'];
    }

    return [
        'baseline' => $baseline['name'],
        'challenger' => $challenger['name'],
        'z' => $z['z'],
        'p_value' => $z['p_value'],
        'significant' => $z['significant'],
        'winner' => $winner,
    ];
}
