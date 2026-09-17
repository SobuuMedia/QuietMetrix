<?php

/**
 * POST /api/v1/counters -- the sole ingest path. Mirrors
 * servers/ktor/.../routes/CounterRoutes.kt. No install identifier ever reaches this route:
 * each item's `u` flag is the SDK's own "first flush of this cell today" signal, so there is
 * nothing here to hash or throttle per-install.
 */
function handleCounters(): void {
    try {
        handleCountersRequest();
    } catch (Throwable $e) {
        error_log('[QuietMetrix] counters stage counters_unclassified: ' . get_class($e) . ': ' . $e->getMessage());
        jsonResponse(500, ['error' => 'internal_error', 'message' => 'Counters ingest failed']);
    }
}

function handleCountersRequest(): void {
    checkOrigin();

    $projectId = requireApiKey();

    $rateLimitRps = defined('RATE_LIMIT_RPS') ? (int)RATE_LIMIT_RPS : 10;
    $rateLimitBurst = defined('RATE_LIMIT_BURST') ? (int)RATE_LIMIT_BURST : 60;
    enforceLimit("counters:{$projectId}", $rateLimitRps, $rateLimitBurst);

    $ip = clientIp() ?? 'unknown';
    if (defined('INGEST_IP_ENABLED') && INGEST_IP_ENABLED && $ip !== 'unknown') {
        $ipRps = defined('INGEST_IP_RPS') ? (int)INGEST_IP_RPS : 5;
        $ipBurst = defined('INGEST_IP_BURST') ? (int)INGEST_IP_BURST : 60;
        enforceLimit("counters:ip:{$ip}", $ipRps, $ipBurst);
    }

    $body = getJsonBody();
    $counters = $body['counters'] ?? null;
    if (!is_array($counters) || empty($counters)) {
        errorResponse(400, 'empty_batch', 'counters must be non-empty');
        return;
    }

    $today = gmdate('Y-m-d');
    $requestedDay = is_string($body['day'] ?? null) ? $body['day'] : $today;
    $day = counterClampDay($requestedDay, $today);

    $platform = is_array($body['sdk'] ?? null) ? (string)($body['sdk']['platform'] ?? '') : '';
    $app = is_array($body['app'] ?? null) ? $body['app'] : [];
    $appVersion = (string)($app['version'] ?? '');
    $country = strtoupper(substr((string)($app['country'] ?? ''), 0, 2));

    $cap = defined('COUNTERS_MAX_DISTINCT_CELLS_PER_METRIC') ? (int)COUNTERS_MAX_DISTINCT_CELLS_PER_METRIC : 500;

    $accepted = 0;
    $quarantined = 0;
    foreach ($counters as $item) {
        if (!is_array($item)) { $quarantined++; continue; }
        $metric = (string)($item['m'] ?? '');
        $dims = is_array($item['d'] ?? null) ? $item['d'] : [];
        $n = (int)($item['n'] ?? 0);
        $isNewDevice = (int)($item['u'] ?? 0) === 1;

        $result = counterUpsert($projectId, $day, $metric, $dims, $platform, $appVersion, $country, $n, $isNewDevice, $cap);
        if ($result['applied']) { $accepted++; } else { $quarantined++; }
    }

    jsonResponse(202, ['ok' => true, 'accepted' => $accepted, 'quarantined' => $quarantined]);
}
