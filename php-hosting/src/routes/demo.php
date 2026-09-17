<?php

// Demo data routes. Only mounted when DEBUG === true. Returns deterministic
// synthetic data so the dashboard's "Use demo data" toggle has something to
// render without hitting the real database. Production deployments never
// reach this file because index.php gates the require_once on DEBUG.

/** GET /api/v1/_demo/aggregates */
function handleDemoAggregates(): void {
    requireSession();   // still gated to logged-in users; demo, not anonymous
    $seconds = windowSeconds(365);
    $hourly  = $seconds <= 86400;
    $days    = (int)max(1, round($seconds / 86400));

    $daily = [];
    $total = 0;
    $offline = 0;
    if ($hourly) {
        // Hourly buckets ("YYYY-MM-DDTHH") for sub-day windows.
        $hours = (int)max(1, round($seconds / 3600));
        for ($i = $hours - 1; $i >= 0; $i--) {
            $bucket = gmdate('Y-m-d\TH', time() - $i * 3600);
            $base   = 120 + (int)(sin($i / 3.0) * 60);
            $off    = (int)($base * 0.07);
            $daily[] = ['day' => $bucket, 'total' => $base, 'offline_total' => $off];
            $total   += $base;
            $offline += $off;
        }
    } else {
        for ($i = $days - 1; $i >= 0; $i--) {
            $day  = gmdate('Y-m-d', time() - $i * 86400);
            // Stable pseudo-random shape: weekly seasonality + a small offline tail
            $base = 800 + (int)(sin($i / 4.0) * 250);
            $off  = (int)($base * 0.07);
            $daily[] = ['day' => $day, 'total' => $base, 'offline_total' => $off];
            $total   += $base;
            $offline += $off;
        }
    }

    jsonResponse(200, [
        'demo'          => true,
        'window_days'   => $days,
        'totals'        => ['events' => $total, 'offline' => $offline, 'errors' => (int)($total * 0.01)],
        'top_events'    => [
            ['event_name' => 'page_view',   'count' => (int)($total * 0.45)],
            ['event_name' => 'login',       'count' => (int)($total * 0.18)],
            ['event_name' => 'signup',      'count' => (int)($total * 0.12)],
            ['event_name' => 'purchase',    'count' => (int)($total * 0.09)],
            ['event_name' => 'screen_view', 'count' => (int)($total * 0.16)],
        ],
        'top_screens'   => [
            ['screen' => 'home',     'count' => (int)($total * 0.32)],
            ['screen' => 'pricing',  'count' => (int)($total * 0.21)],
            ['screen' => 'login',    'count' => (int)($total * 0.18)],
            ['screen' => 'docs',     'count' => (int)($total * 0.14)],
            ['screen' => 'settings', 'count' => (int)($total * 0.15)],
        ],
        'screen_durations' => [
            ['screen' => 'docs',     'count' => (int)($total * 0.14), 'avg_ms' => 94000, 'total_ms' => (int)($total * 0.14) * 94000],
            ['screen' => 'pricing',  'count' => (int)($total * 0.21), 'avg_ms' => 47000, 'total_ms' => (int)($total * 0.21) * 47000],
            ['screen' => 'home',     'count' => (int)($total * 0.32), 'avg_ms' => 22000, 'total_ms' => (int)($total * 0.32) * 22000],
            ['screen' => 'settings', 'count' => (int)($total * 0.15), 'avg_ms' => 18000, 'total_ms' => (int)($total * 0.15) * 18000],
            ['screen' => 'login',    'count' => (int)($total * 0.18), 'avg_ms' =>  9000, 'total_ms' => (int)($total * 0.18) *  9000],
        ],
        'daily'         => $daily,
    ]);
}
