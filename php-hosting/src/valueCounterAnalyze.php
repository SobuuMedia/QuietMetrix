<?php

/**
 * Sums value{name} cells' n -- an amount in integer minor units, not an occurrence count --
 * per name. Mirrors servers/ktor/.../counters/ValueCounterAnalyzer.kt -- keep the two in sync.
 *
 * $cells: [['dims' => ['name' => string], 'n' => int], ...] (from counterReadCells()).
 * Returns a list of ['name' => string, 'total_minor_units' => int], sorted by total descending.
 */
function valueCounterAnalyze(array $cells): array {
    $byName = [];
    foreach ($cells as $cell) {
        $name = $cell['dims']['name'] ?? null;
        if ($name === null) continue;
        $byName[$name] = ($byName[$name] ?? 0) + (int)($cell['n'] ?? 0);
    }
    $result = [];
    foreach ($byName as $name => $total) {
        $result[] = ['name' => (string)$name, 'total_minor_units' => $total];
    }
    usort($result, static fn(array $a, array $b): int => $b['total_minor_units'] <=> $a['total_minor_units']);
    return $result;
}
