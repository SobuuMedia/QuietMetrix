<?php

require_once __DIR__ . '/funnelMatch.php';

/**
 * Aggregates raw event rows into per-step conversion, drop-off, time-to-convert, an optional
 * breakdown, and an optional trend. Mirrors
 * servers/ktor/.../funnels/FunnelAnalyzer.kt — keep the two in sync.
 *
 * $rows: [['actor_key' => string, 'event_name' => string, 'ts' => ISO-8601 string,
 *          'screen'? => string, 'props'? => array, 'country'? => string, 'platform'? => string,
 *          'device_class'? => string, 'language'? => string], ...] — already filtered to
 *         events whose name matches some step, within the (widened) query range.
 * $from/$to: ISO-8601 strings — the STRICT entry-time window; an actor whose entry ts falls
 *            outside [from, to) is excluded entirely, even if later rows are present.
 * $breakdownDimension: null | 'country' | 'platform' | 'device_class' | 'language'.
 *
 * Returns ['entered', 'converted', 'overall_conversion', 'median_total_ms', 'steps',
 *          'breakdown', 'trend', 'counted_by'].
 */
function funnelAnalyze(
    array $steps,
    int $windowSeconds,
    array $rows,
    string $from,
    string $to,
    ?string $breakdownDimension = null,
    bool $withTrend = false,
    string $countMode = 'actor',
    string $identityScope = 'install_or_session',
    ?string $correlationProperty = null
): array {
    $fromTs = strtotime($from);
    $toTs = strtotime($to);

    $byActor = [];
    foreach ($rows as $row) {
        $identity = match ($identityScope) {
            'install' => !empty($row['install_hash']) ? 'install:' . $row['install_hash'] : null,
            'session' => !empty($row['session_id']) ? 'sid:' . $row['session_id'] : null,
            default => $row['actor_key'] ?? null,
        };
        if ($identity === null) continue;
        $attempt = $countMode === 'attempt' ? ($row['props'][$correlationProperty] ?? null) : null;
        if ($countMode === 'attempt' && ($attempt === null || $attempt === '')) continue;
        $key = $attempt === null ? $identity : $identity . '|attempt:' . $attempt;
        $byActor[$key][] = $row;
    }

    $matched = [];
    foreach ($byActor as $actorKey => $actorRows) {
        $events = array_map(fn($r) => [
            'event_name' => $r['event_name'], 'ts' => $r['ts'],
            'screen' => $r['screen'] ?? null, 'props' => $r['props'] ?? [],
        ], $actorRows);
        $result = funnelMatch($steps, $windowSeconds, $events);
        if ($result === null) continue;

        $entryTs = strtotime($result['reached'][0]['ts']);
        if ($entryTs < $fromTs || $entryTs >= $toTs) continue;

        $dimensionValue = null;
        if ($breakdownDimension !== null) {
            $entryRow = null;
            foreach ($actorRows as $r) {
                if ($r['ts'] === $result['reached'][0]['ts']) { $entryRow = $r; break; }
            }
            $dimensionValue = $entryRow[$breakdownDimension] ?? null;
        }

        $matched[] = ['actor_key' => $actorKey, 'result' => $result, 'dimension_value' => $dimensionValue];
    }

    $stepResults = funnelComputeSteps($steps, array_column($matched, 'result'));
    $entered = count($matched);
    $converted = count(array_filter($matched, fn($m) => $m['result']['completed']));
    $overallConversion = funnelSafeDiv($converted, $entered);

    $completedDeltas = [];
    foreach ($matched as $m) {
        if (!$m['result']['completed']) continue;
        $reached = $m['result']['reached'];
        $completedDeltas[] = (strtotime(end($reached)['ts']) - strtotime($reached[0]['ts'])) * 1000;
    }
    $medianTotalMs = funnelPercentile($completedDeltas, 50.0);

    $countedBy = $identityScope === 'session' ? 'session' : ($identityScope === 'install' ? 'install' : 'install');
    if ($identityScope === 'install_or_session' && !empty($matched)) {
        $allSession = true;
        $allInstall = true;
        foreach ($matched as $m) {
            $isSession = str_starts_with($m['actor_key'], 'sid:');
            if ($isSession) $allInstall = false; else $allSession = false;
        }
        $countedBy = $allInstall ? 'install' : ($allSession ? 'session' : 'mixed');
    }
    if ($countMode === 'attempt') $countedBy .= ' attempt';

    $breakdown = null;
    if ($breakdownDimension !== null) {
        $groups = [];
        foreach ($matched as $m) {
            $key = $m['dimension_value'] ?? 'unknown';
            $groups[$key][] = $m;
        }
        $values = [];
        foreach ($groups as $value => $group) {
            $groupResults = array_column($group, 'result');
            $groupConverted = count(array_filter($groupResults, fn($r) => $r['completed']));
            $values[] = [
                'value' => $value,
                'entered' => count($group),
                'overall_conversion' => funnelSafeDiv($groupConverted, count($group)),
                'steps' => funnelComputeSteps($steps, $groupResults),
            ];
        }
        usort($values, fn($a, $b) => $b['entered'] <=> $a['entered']);
        $breakdown = ['dimension' => $breakdownDimension, 'values' => $values];
    }

    $trend = null;
    if ($withTrend) {
        $buckets = [];
        foreach ($matched as $m) {
            $bucket = gmdate('Y-m-d', strtotime($m['result']['reached'][0]['ts']));
            $buckets[$bucket][] = $m;
        }
        ksort($buckets);
        $trend = [];
        foreach ($buckets as $bucket => $group) {
            $groupConverted = count(array_filter($group, fn($m) => $m['result']['completed']));
            $trend[] = [
                'bucket' => $bucket,
                'entered' => count($group),
                'converted' => $groupConverted,
                'conversion' => funnelSafeDiv($groupConverted, count($group)),
            ];
        }
    }

    return [
        'entered' => $entered,
        'converted' => $converted,
        'overall_conversion' => $overallConversion,
        'median_total_ms' => $medianTotalMs,
        'steps' => $stepResults,
        'breakdown' => $breakdown,
        'trend' => $trend,
        'counted_by' => $countedBy,
    ];
}

/** @param array $results List of funnelMatch() results (non-null). */
function funnelComputeSteps(array $steps, array $results): array {
    $entered = count($results);
    $counts = [];
    foreach ($steps as $i => $step) {
        $counts[$i] = count(array_filter($results, fn($r) => count($r['reached']) > $i));
    }

    $out = [];
    foreach ($steps as $i => $step) {
        $count = $counts[$i];
        $previousCount = $i === 0 ? $entered : $counts[$i - 1];
        $deltasMs = [];
        if ($i > 0) {
            foreach ($results as $r) {
                if (count($r['reached']) <= $i) continue;
                $deltasMs[] = (strtotime($r['reached'][$i]['ts']) - strtotime($r['reached'][$i - 1]['ts'])) * 1000;
            }
        }
        $out[] = [
            'key' => $step['key'],
            'name' => $step['name'] ?? null,
            'count' => $count,
            'conversion_from_entry' => funnelSafeDiv($count, $entered),
            'conversion_from_previous' => $i === 0 ? 0.0 : funnelSafeDiv($count, $previousCount),
            'dropped' => $previousCount - $count,
            'drop_rate' => $i === 0 ? 0.0 : funnelSafeDiv($previousCount - $count, $previousCount),
            'median_ms_from_previous' => $i === 0 ? null : funnelPercentile($deltasMs, 50.0),
            'p90_ms_from_previous' => $i === 0 ? null : funnelPercentile($deltasMs, 90.0),
        ];
    }
    return $out;
}

function funnelSafeDiv(int $numerator, int $denominator): float {
    return $denominator === 0 ? 0.0 : $numerator / $denominator;
}
