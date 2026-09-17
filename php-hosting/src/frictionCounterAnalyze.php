<?php

/**
 * Sums friction{screen, kind} cells' n per screen -- an absolute count, not a per-view rate.
 * kind is always "rage_tap" today, but summed regardless of value so a future dead_tap/
 * error_state doesn't silently disappear from this total. Mirrors
 * servers/ktor/.../counters/FrictionCounterAnalyzer.kt -- keep the two in sync.
 *
 * $cells: [['dims' => ['screen' => string, 'kind' => string], 'n' => int], ...].
 * Returns a list of ['screen' => string, 'rage_taps' => int], sorted descending.
 */
function frictionCounterAnalyze(array $cells): array {
    $byScreen = [];
    foreach ($cells as $cell) {
        $screen = $cell['dims']['screen'] ?? null;
        if ($screen === null) continue;
        $byScreen[$screen] = ($byScreen[$screen] ?? 0) + (int)($cell['n'] ?? 0);
    }
    $result = [];
    foreach ($byScreen as $screen => $rageTaps) {
        $result[] = ['screen' => (string)$screen, 'rage_taps' => $rageTaps];
    }
    usort($result, static fn(array $a, array $b): int => $b['rage_taps'] <=> $a['rage_taps']);
    return $result;
}
