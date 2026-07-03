<?php

/** GET /api/v1/projects/{id}/events?limit=&before= → recent raw events. */
function handleProjectEvents(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);

    $limit = max(1, min(500, (int)($_GET['limit'] ?? 100)));
    $before = $_GET['before'] ?? null;

    $sql = 'SELECT id, event_name, screen, props, session_id, ts, was_offline,
                   country, device_class, language, platform, sdk_version, duration_ms
              FROM events
             WHERE project_id = ?';
    $params = [$projectId];
    if (is_string($before) && $before !== '') {
        $sql .= ' AND ts < ?';
        $params[] = $before;
    }
    $sql .= ' ORDER BY ts DESC LIMIT ' . $limit;

    $stmt = getDb()->prepare($sql);
    $stmt->execute($params);
    $rows = $stmt->fetchAll();
    foreach ($rows as &$r) {
        if (isset($r['props']) && is_string($r['props'])) {
            $r['props'] = json_decode($r['props'], true);
        }
        $r['was_offline'] = (bool)$r['was_offline'];
        $r['duration_ms'] = isset($r['duration_ms']) ? (int)$r['duration_ms'] : null;
    }
    jsonResponse(200, ['events' => $rows]);
}

/** GET /api/v1/projects/{id}/aggregates?range=7d → top events, daily counts. */
function handleProjectAggregates(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);

    $seconds = windowSeconds(365);
    $days = (int)max(1, round($seconds / 86400));
    $since = gmdate('Y-m-d\TH:i:s\Z', time() - $seconds);
    // Sub-day windows are bucketed by hour (ISO "YYYY-MM-DDTHH"); else by day.
    $bucket = bucketExpr($seconds);

    $db = getDb();

    // Top events
    $stmt = $db->prepare(
        'SELECT event_name, COUNT(*) AS count
           FROM events
          WHERE project_id = ? AND ts >= ?
          GROUP BY event_name
          ORDER BY count DESC
          LIMIT 20'
    );
    $stmt->execute([$projectId, $since]);
    $topEvents = $stmt->fetchAll();

    // Top screens
    $stmt = $db->prepare(
        'SELECT screen, COUNT(*) AS count
           FROM events
          WHERE project_id = ? AND ts >= ? AND screen IS NOT NULL
          GROUP BY screen
          ORDER BY count DESC
          LIMIT 20'
    );
    $stmt->execute([$projectId, $since]);
    $topScreens = $stmt->fetchAll();

    // Per-screen dwell time — from screen_view events carrying a duration_ms
    $stmt = $db->prepare(
        "SELECT screen,
                COUNT(*)            AS count,
                ROUND(AVG(duration_ms)) AS avg_ms,
                SUM(duration_ms)    AS total_ms
           FROM events
          WHERE project_id = ? AND ts >= ? AND event_name = 'screen_view'
            AND screen IS NOT NULL AND duration_ms IS NOT NULL
          GROUP BY screen
          ORDER BY total_ms DESC
          LIMIT 20"
    );
    $stmt->execute([$projectId, $since]);
    $screenDurations = array_map(static function (array $r): array {
        return [
            'screen'   => $r['screen'],
            'count'    => (int)$r['count'],
            'avg_ms'   => (int)$r['avg_ms'],
            'total_ms' => (int)$r['total_ms'],
        ];
    }, $stmt->fetchAll());

    // Daily/hourly counts — emitted as { day, total, offline_total }
    $stmt = $db->prepare(
        "SELECT $bucket AS day,
                COUNT(*) AS total,
                SUM(was_offline) AS offline_total
           FROM events
          WHERE project_id = ? AND ts >= ?
          GROUP BY $bucket
          ORDER BY day"
    );
    $stmt->execute([$projectId, $since]);
    $daily = $stmt->fetchAll();

    // Total + offline split for the window
    $stmt = $db->prepare(
        'SELECT COUNT(*) AS total, SUM(was_offline) AS offline_total
           FROM events WHERE project_id = ? AND ts >= ?'
    );
    $stmt->execute([$projectId, $since]);
    $totals = $stmt->fetch() ?: ['total' => 0, 'offline_total' => 0];

    // Error count — events the app reported as errors (CrashWatch owns the details)
    $stmt = $db->prepare(
        "SELECT COUNT(*) AS total
           FROM events
          WHERE project_id = ? AND ts >= ?
            AND LOWER(event_name) IN ('error', 'crash', 'exception')"
    );
    $stmt->execute([$projectId, $since]);
    $errors = (int)(($stmt->fetch() ?: ['total' => 0])['total']);

    // Country breakdown
    $stmt = $db->prepare(
        'SELECT country AS name, COUNT(*) AS count
           FROM events WHERE project_id = ? AND ts >= ? AND country IS NOT NULL
           GROUP BY country ORDER BY count DESC LIMIT 20'
    );
    $stmt->execute([$projectId, $since]);
    $countries = $stmt->fetchAll();

    // Platform breakdown
    $stmt = $db->prepare(
        'SELECT platform AS name, COUNT(*) AS count
           FROM events WHERE project_id = ? AND ts >= ? AND platform IS NOT NULL
           GROUP BY platform ORDER BY count DESC'
    );
    $stmt->execute([$projectId, $since]);
    $platforms = $stmt->fetchAll();

    // Device class breakdown
    $stmt = $db->prepare(
        'SELECT device_class AS name, COUNT(*) AS count
           FROM events WHERE project_id = ? AND ts >= ? AND device_class IS NOT NULL
           GROUP BY device_class ORDER BY count DESC'
    );
    $stmt->execute([$projectId, $since]);
    $deviceClasses = $stmt->fetchAll();

    jsonResponse(200, [
        'window_days'    => $days,
        'totals'         => [
            'events'  => (int)$totals['total'],
            'offline' => (int)($totals['offline_total'] ?? 0),
            'errors'  => $errors,
        ],
        'top_events'       => $topEvents,
        'top_screens'      => $topScreens,
        'screen_durations' => $screenDurations,
        'daily'            => $daily,
        'countries'      => $countries,
        'platforms'      => $platforms,
        'device_classes' => $deviceClasses,
    ]);
}

/** GET /api/v1/projects/{id}/transitions?range=7d → screen-to-screen flow. */
function handleProjectTransitions(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);

    $since = gmdate('Y-m-d\TH:i:s\Z', time() - windowSeconds(90));

    $stmt = getDb()->prepare(
        "SELECT session_id, screen, ts
           FROM events
          WHERE project_id = ? AND ts >= ? AND event_name = 'screen_view'
            AND screen IS NOT NULL AND screen <> '' AND session_id IS NOT NULL
          ORDER BY session_id, ts"
    );
    $stmt->execute([$projectId, $since]);

    // Count each from -> to hop with a nested map, so from/to stay structured. Avoid packing the
    // pair into a single delimited string key: that round-trip is fragile (a delimiter char in a
    // screen name, or a mangled multibyte separator, silently drops the destination).
    $transitions = [];   // from_screen => [ to_screen => count ]
    $prevKey = null;
    $prevScreen = null;

    while ($row = $stmt->fetch()) {
        $currentKey = $row['session_id'];
        $currentScreen = $row['screen'];

        if ($currentKey === $prevKey && $prevScreen !== null && $currentScreen !== $prevScreen) {
            $transitions[$prevScreen][$currentScreen] = ($transitions[$prevScreen][$currentScreen] ?? 0) + 1;
        }
        $prevKey = $currentKey;
        $prevScreen = $currentScreen;
    }

    $result = [];
    foreach ($transitions as $from => $tos) {
        foreach ($tos as $to => $count) {
            // Cast: PHP silently coerces digit-only array keys to int.
            $result[] = ['from_screen' => (string)$from, 'to_screen' => (string)$to, 'count' => $count];
        }
    }
    usort($result, static fn(array $a, array $b): int => $b['count'] <=> $a['count']);
    $result = array_slice($result, 0, 50);

    jsonResponse(200, ['transitions' => $result]);
}

/** GET /api/v1/projects/{id}/sessions?range=7d → session analytics. */
function handleProjectSessions(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);

    $seconds = windowSeconds(90);
    $since = gmdate('Y-m-d\TH:i:s\Z', time() - $seconds);
    $bucket = bucketExpr($seconds);

    $db = getDb();

    // Session-level stats
    $stmt = $db->prepare(
        'SELECT COUNT(DISTINCT session_id) AS total_sessions,
                ROUND(COUNT(*) / NULLIF(COUNT(DISTINCT session_id), 0), 1) AS avg_events,
                ROUND(AVG(duration_sec), 0) AS avg_duration_sec
           FROM (
               SELECT session_id,
                      COUNT(*) AS event_count,
                      UNIX_TIMESTAMP(MAX(ts)) - UNIX_TIMESTAMP(MIN(ts)) AS duration_sec
                 FROM events
                WHERE project_id = ? AND ts >= ? AND session_id IS NOT NULL
                GROUP BY session_id
           ) s'
    );
    $stmt->execute([$projectId, $since]);
    $stats = $stmt->fetch() ?: ['total_sessions' => 0, 'avg_events' => 0, 'avg_duration_sec' => 0];

    // Daily/hourly sessions
    $stmt = $db->prepare(
        "SELECT $bucket AS day, COUNT(DISTINCT session_id) AS total
           FROM events
          WHERE project_id = ? AND ts >= ? AND session_id IS NOT NULL
          GROUP BY $bucket
          ORDER BY day"
    );
    $stmt->execute([$projectId, $since]);
    $dailySessions = $stmt->fetchAll();

    jsonResponse(200, [
        'total_sessions'     => (int)$stats['total_sessions'],
        'avg_events'         => (float)$stats['avg_events'],
        'avg_duration_sec'   => (int)$stats['avg_duration_sec'],
        'daily_sessions'     => $dailySessions,
    ]);
}

/** GET /api/v1/projects/{id}/retention?days=30 → cohort retention matrix. */
function handleProjectRetention(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);

    $days = max(1, min(90, (int)($_GET['days'] ?? 30)));
    $since = gmdate('Y-m-d\TH:i:s\Z', time() - $days * 86400);

    $db = getDb();

    // Get each session_id's first event date within the window
    $stmt = $db->prepare(
        'SELECT session_id, MIN(DATE(ts)) AS first_day
           FROM events
          WHERE project_id = ? AND ts >= ? AND session_id IS NOT NULL
          GROUP BY session_id'
    );
    $stmt->execute([$projectId, $since]);
    $sessionFirstDays = $stmt->fetchAll();

    // Group by cohort date
    $cohorts = [];
    foreach ($sessionFirstDays as $row) {
        $cohortDate = $row['first_day'];
        $cohorts[$cohortDate][] = $row['session_id'];
    }

    // For each cohort date, compute day-N retention
    $retentionOffsets = [1, 3, 7, 14, 30];
    $result = [];

    foreach ($cohorts as $cohortDate => $sessionIds) {
        $size = count($sessionIds);
        $retention = ['cohort_date' => $cohortDate, 'size' => $size];

        foreach ($retentionOffsets as $offset) {
            $targetDate = gmdate('Y-m-d', strtotime($cohortDate) + $offset * 86400);
            if ($targetDate > gmdate('Y-m-d')) {
                $retention["day$offset"] = null; // not yet observable
                continue;
            }

            $placeholders = implode(',', array_fill(0, count($sessionIds), '?'));
            if ($placeholders === '') {
                $retention["day$offset"] = null;
                continue;
            }

            $params = array_merge([$projectId, $targetDate], $sessionIds);
            $stmt = $db->prepare(
                "SELECT COUNT(DISTINCT session_id) AS returned
                   FROM events
                  WHERE project_id = ? AND DATE(ts) = ? AND session_id IN ($placeholders)"
            );
            $stmt->execute($params);
            $row = $stmt->fetch();
            $returned = (int)$row['returned'];
            $retention["day$offset"] = $size > 0 ? round($returned / $size, 4) : 0;
        }

        $result[] = $retention;
    }

    jsonResponse(200, ['cohorts' => $result]);
}
