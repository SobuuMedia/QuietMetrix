<?php

/** POST /api/v1/track — single event. */
function handleTrack(): void {
    $projectId = requireApiKey();
    enforceLimit("track:{$projectId}", RATE_LIMIT_RPS, RATE_LIMIT_BURST);
    $event     = getJsonBody();
    $err = validateEvent($event);
    if ($err !== null) {
        errorResponse(400, 'schema_violation', $err);
        return;
    }
    insertEvent($projectId, $event);
    jsonResponse(202, ['ok' => true, 'queued' => 1]);
}

/** POST /api/v1/track/batch — up to 100 events in one call. */
function handleTrackBatch(): void {
    $projectId = requireApiKey();
    enforceLimit("track:{$projectId}", RATE_LIMIT_RPS, RATE_LIMIT_BURST);
    $body      = getJsonBody();
    $events    = $body['events'] ?? null;
    if (!is_array($events)) {
        errorResponse(400, 'invalid_request', 'Body must be { "events": [ ... ] }');
        return;
    }
    if (count($events) === 0) {
        errorResponse(400, 'invalid_request', 'events array is empty');
        return;
    }
    if (count($events) > 100) {
        errorResponse(413, 'payload_too_large', 'Maximum 100 events per batch');
        return;
    }
    foreach ($events as $i => $event) {
        $err = validateEvent($event);
        if ($err !== null) {
            errorResponse(400, 'schema_violation', "events[$i]: $err");
            return;
        }
    }
    $db = getDb();
    $db->beginTransaction();
    try {
        foreach ($events as $event) insertEvent($projectId, $event);
        $db->commit();
    } catch (Throwable $e) {
        $db->rollBack();
        throw $e;
    }
    jsonResponse(202, ['ok' => true, 'queued' => count($events)]);
}

/** Returns null if valid, or a short human-readable error message. */
function validateEvent($event): ?string {
    if (!is_array($event))                                return 'must be a JSON object';
    if (!isset($event['event']) || !is_string($event['event']))  return 'event is required (string)';
    if ($event['event'] === '' || strlen($event['event']) > 255) return 'event must be 1–255 chars';
    if (preg_match('/[\\x00-\\x1f<>"\\\']/', $event['event'])) return 'event name contains disallowed characters';
    if (isset($event['screen']) && !is_string($event['screen'])) return 'screen must be a string';
    if (isset($event['screen']) && strlen($event['screen']) > 255) return 'screen ≤ 255 chars';
    if (isset($event['sid'])    && !is_string($event['sid']))    return 'sid must be a string';
    if (isset($event['ts'])     && !is_string($event['ts']))     return 'ts must be an ISO-8601 string';
    if (isset($event['props'])  && !is_array($event['props']))   return 'props must be a JSON object';
    if (isset($event['props'])  && count($event['props']) > 50)  return 'props exceeds maximum of 50 keys';
    if (isset($event['props'])) {
        foreach ($event['props'] as $k => $v) {
            if (is_string($v) && strlen($v) > 4096) {
                return "prop '\$k' exceeds maximum of 4096 bytes";
            }
        }
    }
    if (isset($event['was_offline']) && !is_bool($event['was_offline'])) return 'was_offline must be boolean';
    if (isset($event['ctx'])) {
        if (!is_array($event['ctx'])) return 'ctx must be a JSON object';
        if (isset($event['ctx']['country']) && !is_string($event['ctx']['country'])) {
            return 'ctx.country must be a string';
        }
    }
    return null;
}

/** ISO-3166 alpha-2 region from a client-supplied value, or null. */
function normalizeCountry($value): ?string {
    if (!is_string($value) || $value === '') return null;
    return strtoupper(substr($value, 0, 2));
}

/**
 * Device class for an event. Prefers the user-agent (web), else falls back to the SDK platform
 * (native mobile/desktop SDKs carry no browser UA). Mirrors the Ktor EventNormalizer classifier.
 * Returns null when neither signal resolves.
 */
function deriveDeviceClass(?string $ua, ?string $platform): ?string {
    if (is_string($ua) && $ua !== '') {
        if (preg_match('/bot|crawler|spider|slurp|mediapartners/i', $ua))          return 'bot';
        if (preg_match('/mobile|android|iphone|ipod|windows phone/i', $ua))        return 'mobile';
        if (preg_match('/ipad|tablet|kindle|silk|(android(?!.*mobile))/i', $ua))   return 'tablet';
        return 'desktop';
    }
    switch (strtolower((string)$platform)) {
        case 'android':
        case 'ios':
            return 'mobile';
        case 'macos':
        case 'windows':
        case 'linux':
        case 'jvm':
            return 'desktop';
        default:
            return null;
    }
}

/** Positive integer milliseconds from a client-supplied duration_ms prop, or null. */
function normalizeDuration($value): ?int {
    if (!is_int($value) && !(is_float($value) && floor($value) == $value)) return null;
    $ms = (int)$value;
    return $ms > 0 ? $ms : null;
}

function insertEvent(string $projectId, array $event): void {
    $sdk = $event['sdk'] ?? [];
    $ctx = $event['ctx'] ?? [];

    getDb()->prepare(
        'INSERT INTO events
            (project_id, event_name, screen, props, session_id, ts, received_at,
             was_offline, country, device_class, language, platform, sdk_version, duration_ms)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)'
    )->execute([
        $projectId,
        $event['event'],
        $event['screen']     ?? null,
        isset($event['props']) ? json_encode($event['props'], JSON_UNESCAPED_UNICODE) : null,
        $event['sid']        ?? null,
        $event['ts']         ?? now(),
        now(),
        !empty($event['was_offline']) ? 1 : 0,
        normalizeCountry($ctx['country'] ?? null),   // client device-locale region
        deriveDeviceClass(                           // UA if present, else SDK platform
            is_string($ctx['ua'] ?? null) ? $ctx['ua'] : null,
            is_string($sdk['platform'] ?? null) ? $sdk['platform'] : null,
        ),
        is_string($ctx['language'] ?? null) ? substr($ctx['language'], 0, 10) : null,
        is_string($sdk['platform'] ?? null) ? substr($sdk['platform'], 0, 20) : null,
        is_string($sdk['version']  ?? null) ? substr($sdk['version'],  0, 20) : null,
        normalizeDuration($event['props']['duration_ms'] ?? null),  // time-on-screen, screen_view events
    ]);
}
