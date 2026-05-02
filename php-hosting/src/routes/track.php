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
    if (isset($event['was_offline']) && !is_bool($event['was_offline'])) return 'was_offline must be boolean';
    return null;
}

function insertEvent(string $projectId, array $event): void {
    $sdk = $event['sdk'] ?? [];
    $ctx = $event['ctx'] ?? [];

    getDb()->prepare(
        'INSERT INTO events
            (project_id, event_name, screen, props, session_id, ts, received_at,
             was_offline, country, device_class, language, platform, sdk_version)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)'
    )->execute([
        $projectId,
        $event['event'],
        $event['screen']     ?? null,
        isset($event['props']) ? json_encode($event['props'], JSON_UNESCAPED_UNICODE) : null,
        $event['sid']        ?? null,
        $event['ts']         ?? now(),
        now(),
        !empty($event['was_offline']) ? 1 : 0,
        null,                            // country derived from IP — TODO: GeoIP lookup
        null,                            // device_class derived from UA — TODO
        is_string($ctx['language'] ?? null) ? substr($ctx['language'], 0, 10) : null,
        is_string($sdk['platform'] ?? null) ? substr($sdk['platform'], 0, 20) : null,
        is_string($sdk['version']  ?? null) ? substr($sdk['version'],  0, 20) : null,
    ]);
}
