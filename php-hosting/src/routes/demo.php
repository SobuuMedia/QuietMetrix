<?php

// Demo data routes. Only mounted when DEBUG === true. Returns deterministic
// synthetic data so the dashboard's "Use demo data" toggle has something to
// render without hitting the real database. Production deployments never
// reach this file because index.php gates the require_once on DEBUG.

/** GET /api/v1/_demo/aggregates */
function handleDemoAggregates(): void {
    requireSession();   // still gated to logged-in users; demo, not anonymous
    $days = max(1, min(365, (int)($_GET['days'] ?? 30)));

    $daily = [];
    $total = 0;
    $offline = 0;
    for ($i = $days - 1; $i >= 0; $i--) {
        $day  = gmdate('Y-m-d', time() - $i * 86400);
        // Stable pseudo-random shape: weekly seasonality + a small offline tail
        $base = 800 + (int)(sin($i / 4.0) * 250);
        $off  = (int)($base * 0.07);
        $daily[] = ['day' => $day, 'total' => $base, 'offline_total' => $off];
        $total   += $base;
        $offline += $off;
    }

    jsonResponse(200, [
        'demo'          => true,
        'window_days'   => $days,
        'totals'        => ['events' => $total, 'offline' => $offline],
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
        'daily'         => $daily,
    ]);
}

/** GET /api/v1/_demo/events */
function handleDemoEvents(): void {
    requireSession();
    $events = [];
    $names    = ['page_view', 'login', 'signup', 'purchase', 'screen_view'];
    $screens  = ['home', 'pricing', 'login', 'docs', 'settings', null];
    $platforms = ['android', 'ios', 'web', 'macos', 'jvm'];
    for ($i = 0; $i < 50; $i++) {
        $events[] = [
            'id'           => 1000 - $i,
            'event_name'   => $names[$i % count($names)],
            'screen'       => $screens[$i % count($screens)],
            'props'        => $i % 3 === 0 ? ['plan' => 'pro', 'amount' => 29] : null,
            'session_id'   => 'demo-' . ($i % 7),
            'ts'           => gmdate('Y-m-d\TH:i:s\Z', time() - $i * 60),
            'was_offline'  => $i % 11 === 0,
            'country'      => ['US','GB','DE','FR','ES','BR'][$i % 6],
            'device_class' => $i % 2 === 0 ? 'mobile' : 'desktop',
            'language'     => 'en',
            'platform'     => $platforms[$i % count($platforms)],
            'sdk_version'  => '0.2.0',
        ];
    }
    jsonResponse(200, ['demo' => true, 'events' => $events]);
}
