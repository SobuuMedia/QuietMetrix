<?php

/**
 * Matches one actor's events against a funnel definition. Mirrors
 * servers/ktor/.../funnels/FunnelMatcher.kt; keep the two in sync.
 *
 * Strict order: the window starts at entry (the first event matching step 0) and every
 * subsequent step must occur, in order, after the previous match and no later than
 * `entry.ts + windowSeconds`. An actor's entry is fixed at their FIRST step-0 match — a
 * later, independent attempt at the funnel (e.g. the first attempt expired) does not create
 * a second entry or resurrect a dropped actor.
 *
 * $steps: [['key' => string, 'event' => string, 'screen'? => string, 'props'? => array], ...]
 * $events: [['event_name' => string, 'ts' => ISO-8601 string, 'screen'? => string,
 *            'props'? => array], ...] — already filtered to only events whose name matches
 *           some step of this funnel.
 *
 * Returns null if the actor never matched the entry step at all (not in the funnel), else
 * ['reached' => [['step_key' => string, 'ts' => string], ...], 'completed' => bool].
 */
function funnelMatch(array $steps, int $windowSeconds, array $events): ?array {
    if (empty($steps)) return null;

    usort($events, fn($a, $b) => strtotime($a['ts']) <=> strtotime($b['ts']));

    $reached = [];
    $cursor = 0;
    $deadline = null;
    $previousTs = null;

    foreach ($steps as $step) {
        $matchIndex = funnelFindNextMatch($events, $cursor, $step, $deadline, $previousTs);
        if ($matchIndex === null) break;
        $reached[] = ['step_key' => $step['key'], 'ts' => $events[$matchIndex]['ts']];
        $cursor = $matchIndex + 1;
        if ($deadline === null) {
            $deadline = strtotime($events[$matchIndex]['ts']) + $windowSeconds;
        }
        $previousTs = strtotime($events[$matchIndex]['ts']);
    }

    if (empty($reached)) return null;
    return ['reached' => $reached, 'completed' => count($reached) === count($steps)];
}

function funnelFindNextMatch(array $events, int $fromIndex, array $step, ?int $deadline, ?int $previousTs): ?int {
    $n = count($events);
    for ($i = $fromIndex; $i < $n; $i++) {
        $event = $events[$i];
        // Events are sorted by ts, so once we're past the deadline nothing further can match.
        if ($deadline !== null && strtotime($event['ts']) > $deadline) return null;
        if ($previousTs !== null && strtotime($event['ts']) <= $previousTs) continue;
        if (funnelEventMatchesStep($event, $step)) return $i;
    }
    return null;
}

function funnelEventMatchesStep(array $event, array $step): bool {
    if (($event['event_name'] ?? null) !== ($step['event'] ?? null)) return false;
    $stepScreen = $step['screen'] ?? null;
    if ($stepScreen !== null && ($event['screen'] ?? null) !== $stepScreen) return false;
    // Step props are stored as strings; event props decode from JSON with mixed scalar
    // types (int, bool, string). Compare as strings so a filter like {"tier": "2"} still
    // matches an event prop that decoded as the int 2, rather than never matching.
    foreach (($step['props'] ?? []) as $key => $value) {
        $actual = $event['props'][$key] ?? null;
        if ($actual === null || funnelPropToString($actual) !== funnelPropToString($value)) return false;
    }
    return true;
}

/**
 * Stringifies a decoded JSON scalar the way funnelStepsForWire() does, so prop comparison
 * agrees with what got stored. A plain (string) cast is not safe here: (string)false === ''
 * in PHP, which would make a false prop indistinguishable from an absent/empty one.
 */
function funnelPropToString($value): string {
    if (is_bool($value)) return $value ? 'true' : 'false';
    return (string)$value;
}

/**
 * Nearest-rank percentile: rank = ceil(p/100 * n), 1-indexed into the sorted values, clamped
 * to [1, n]. Mirrors servers/ktor/.../funnels/Percentile.kt. Returns null for an empty input.
 */
function funnelPercentile(array $values, float $p): ?int {
    if (empty($values)) return null;
    sort($values, SORT_NUMERIC);
    $n = count($values);
    $rank = (int)ceil($p / 100.0 * $n);
    $rank = max(1, min($n, $rank));
    return $values[$rank - 1];
}
