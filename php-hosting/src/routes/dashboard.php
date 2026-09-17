<?php

/** GET /api/v1/projects/{id}/aggregates?range=7d → top events, daily counts. */
/**
 * "Ship what maps cleanly": top_events, screen_durations (bucket-approximated), platforms,
 * and daily totals all read from counters. top_screens is approximated from
 * screen_transition (no standalone "screen viewed" counter exists). countries/device_classes
 * are not tracked as counter dimensions yet, so those fields are empty rather than silently
 * faked; offline is always 0 for the same reason. Mirrors
 * servers/ktor/.../routes/DashboardRoutes.kt's /aggregates handler.
 */
function handleProjectAggregates(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $seconds = windowSeconds(365);
    $days = (int)max(1, round($seconds / 86400));
    $toDay = gmdate('Y-m-d');
    $fromDay = gmdate('Y-m-d', time() - $seconds);

    $eventCells = counterReadCells($projectId, 'event', $fromDay, $toDay);
    usort($eventCells, static fn(array $a, array $b): int => $b['n'] <=> $a['n']);
    $topEvents = array_map(
        static fn(array $c): array => ['event_name' => $c['dims']['name'] ?? null, 'count' => $c['n']],
        array_slice($eventCells, 0, 20),
    );

    $transitionCells = counterReadCells($projectId, 'screen_transition', $fromDay, $toDay);
    $topScreens = array_map(
        static fn(array $r): array => ['screen' => $r['screen'], 'count' => $r['n']],
        overviewCounterTopScreens($transitionCells, 20),
    );

    $dwellCells = counterReadCells($projectId, 'screen_dwell', $fromDay, $toDay);
    $screenDurations = array_map(
        static fn(array $r): array => ['screen' => $r['screen'], 'count' => $r['count'], 'avg_ms' => $r['avg_ms'], 'total_ms' => $r['total_ms']],
        array_slice(overviewCounterScreenDurations($dwellCells), 0, 20),
    );

    $dailyTotals = counterDailyTotals($projectId, 'event', $fromDay, $toDay);
    $daily = array_map(
        static fn(array $r): array => ['day' => $r['day'], 'total' => $r['n'], 'offline_total' => 0],
        $dailyTotals,
    );

    $platforms = array_map(
        static fn(array $r): array => ['name' => $r['platform'], 'count' => $r['n']],
        counterTotalsByPlatform($projectId, 'event', $fromDay, $toDay),
    );

    $valueCells = counterReadCells($projectId, 'value', $fromDay, $toDay);
    $topValues = array_map(
        static fn(array $r): array => ['name' => $r['name'], 'total_minor_units' => $r['total_minor_units']],
        array_slice(valueCounterAnalyze($valueCells), 0, 10),
    );

    $totalEvents = array_sum(array_column($eventCells, 'n'));
    $errors = array_sum(array_map(
        static fn(array $c): int => in_array(strtolower($c['dims']['name'] ?? ''), ['error', 'crash', 'exception'], true) ? $c['n'] : 0,
        $eventCells,
    ));

    jsonResponse(200, [
        'window_days'    => $days,
        'totals'         => [
            'events'  => $totalEvents,
            'offline' => 0,
            'errors'  => $errors,
        ],
        'top_events'       => $topEvents,
        'top_screens'      => $topScreens,
        'screen_durations' => $screenDurations,
        'top_values'       => $topValues,
        'daily'            => $daily,
        'countries'      => [],
        'platforms'      => $platforms,
        'device_classes' => [],
    ]);
}

/** GET /api/v1/projects/{id}/transitions?range=7d → screen-to-screen flow. */
/**
 * Reads from the screen_transition counter (see counters.php / counterRegistry.php) instead
 * of the raw events table -- the device itself already reduced navigation to A->B pairs (see
 * ScreenTracker.kt's enter()), so there is no per-session trail to walk here any more.
 * Mirrors servers/ktor/.../routes/DashboardRoutes.kt's /transitions handler.
 */
function handleProjectTransitions(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $days = min(90, max(1, (int)($_GET['days'] ?? 30)));
    $toDay = gmdate('Y-m-d');
    $fromDay = gmdate('Y-m-d', time() - $days * 86400);

    $cells = counterReadCells($projectId, 'screen_transition', $fromDay, $toDay);
    $result = [];
    foreach ($cells as $cell) {
        $from = $cell['dims']['from'] ?? null;
        $to = $cell['dims']['to'] ?? null;
        if ($from === null || $to === null) continue;
        $result[] = ['from_screen' => (string)$from, 'to_screen' => (string)$to, 'count' => $cell['n']];
    }
    usort($result, static fn(array $a, array $b): int => $b['count'] <=> $a['count']);
    $result = array_slice($result, 0, 50);

    jsonResponse(200, ['transitions' => $result]);
}

/**
 * GET /api/v1/projects/{id}/search?days=30 -> per-screen zero-result search rate.
 *
 * The search query itself is never sent (see the SDK's trackSearch) -- only whether it came
 * back empty. Mirrors servers/ktor/.../routes/DashboardRoutes.kt's /search handler.
 */
function handleProjectSearch(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $days = min(90, max(1, (int)($_GET['days'] ?? 30)));
    $toDay = gmdate('Y-m-d');
    $fromDay = gmdate('Y-m-d', time() - $days * 86400);

    $searchCells = counterReadCells($projectId, 'search', $fromDay, $toDay);
    $zeroResultCells = counterReadCells($projectId, 'search_zero_result', $fromDay, $toDay);
    $screens = array_map(
        static fn(array $r): array => ['screen' => $r['screen'], 'total' => $r['total'], 'zero_result' => $r['zero_result'], 'rate' => $r['rate']],
        searchCounterAnalyze($searchCells, $zeroResultCells),
    );

    jsonResponse(200, ['screens' => $screens]);
}

/**
 * GET /api/v1/projects/{id}/friction?days=30 -> per-screen rage-tap count.
 *
 * Mirrors servers/ktor/.../routes/DashboardRoutes.kt's /friction handler. See the SDK's
 * RageTapDetector/FrictionCounterBridge for how this is captured on-device.
 */
function handleProjectFriction(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $days = min(90, max(1, (int)($_GET['days'] ?? 30)));
    $toDay = gmdate('Y-m-d');
    $fromDay = gmdate('Y-m-d', time() - $days * 86400);

    $frictionCells = counterReadCells($projectId, 'friction', $fromDay, $toDay);
    $screens = array_map(
        static fn(array $r): array => ['screen' => $r['screen'], 'rage_taps' => $r['rage_taps']],
        frictionCounterAnalyze($frictionCells),
    );

    jsonResponse(200, ['screens' => $screens]);
}

/**
 * GET /api/v1/projects/{id}/sessions?range=7d → session analytics.
 *
 * Reads from the session{bucket} counter (see counters.php / sessionCounterAnalyze.php)
 * instead of grouping the raw events table by session_id -- the device itself already
 * reduced a session to one bucketed length (see the SDK's SessionTracker). No avg_events
 * field: no counter ties an event count to a session under aggregate-only ingest, so it
 * would be a permanently-fake number rather than an honest gap. Mirrors
 * servers/ktor/.../routes/DashboardRoutes.kt's /sessions handler.
 */
function handleProjectSessions(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $seconds = windowSeconds(90);
    $toDay = gmdate('Y-m-d');
    $fromDay = gmdate('Y-m-d', time() - $seconds);

    $cells = counterReadCells($projectId, 'session', $fromDay, $toDay);
    $analysis = sessionCounterAnalyze($cells);
    $dailySessions = array_map(
        static fn(array $r): array => ['day' => $r['day'], 'total' => $r['n']],
        counterDailyTotals($projectId, 'session', $fromDay, $toDay),
    );

    jsonResponse(200, [
        'total_sessions'     => $analysis['total_sessions'],
        'avg_duration_sec'   => $analysis['avg_duration_sec'],
        'daily_sessions'     => $dailySessions,
    ]);
}

/** GET /api/v1/projects/{id}/retention?days=30 → cohort retention matrix. */
/**
 * Reads from the retention counter (see counters.php / counterRegistry.php) instead of
 * inferring cohorts from session first-seen dates -- the device itself already knows its
 * first-launch date and reports day="0" (cohort join) and each "at least N days later" mark
 * it reaches (see the SDK's RetentionReporter). Mirrors
 * servers/ktor/.../routes/DashboardRoutes.kt's /retention handler.
 */
function handleProjectRetention(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $days = max(1, min(90, (int)($_GET['days'] ?? 30)));
    $toDay = gmdate('Y-m-d');
    $fromDay = gmdate('Y-m-d', time() - $days * 86400);

    $counterCells = counterReadCells($projectId, 'retention', $fromDay, $toDay);
    $cells = [];
    foreach ($counterCells as $c) {
        $cohort = $c['dims']['cohort'] ?? null;
        $day = isset($c['dims']['day']) ? (int)$c['dims']['day'] : null;
        if ($cohort === null || $day === null) continue;
        $cells[] = ['cohort' => $cohort, 'day' => $day, 'n' => $c['n']];
    }

    $cohorts = retentionCounterAnalyze($cells);

    // Null (not 0.0) means "this project hasn't configured activationEvent" -- distinct from
    // a real 0% rate. The server can't see the SDK's config, so it approximates: no
    // activation{cohort} cells anywhere in the queried window is read as "not tracked" for
    // every cohort in this response, rather than a suspicious all-zero row.
    $activationCounterCells = counterReadCells($projectId, 'activation', $fromDay, $toDay);
    $activationCells = [];
    foreach ($activationCounterCells as $c) {
        $cohort = $c['dims']['cohort'] ?? null;
        if ($cohort === null) continue;
        $activationCells[] = ['cohort' => $cohort, 'n' => $c['n']];
    }
    $activationRateByCohort = [];
    if (!empty($activationCells)) {
        $cohortSizes = [];
        foreach ($cohorts as $r) { $cohortSizes[$r['cohort']] = $r['size']; }
        foreach (activationCounterAnalyze($activationCells, $cohortSizes) as $r) {
            $activationRateByCohort[$r['cohort']] = $r['rate'];
        }
    }

    $result = array_map(static function (array $r) use ($activationRateByCohort): array {
        return [
            'cohort_date' => $r['cohort'],
            'size' => $r['size'],
            'day1' => $r['day1'],
            'day3' => $r['day3'],
            'day7' => $r['day7'],
            'day14' => $r['day14'],
            'day30' => $r['day30'],
            'activation_rate' => $activationRateByCohort[$r['cohort']] ?? null,
        ];
    }, $cohorts);

    jsonResponse(200, ['cohorts' => $result]);
}
