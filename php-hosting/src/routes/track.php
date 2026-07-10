<?php

/** Stage 5 — origin/referer check for non-mobile traffic. */
function checkOrigin(): void {
    if (!defined('ALLOWED_ORIGINS')) return;
    $allowed = array_map('trim', explode(',', ALLOWED_ORIGINS));
    if (empty($allowed) || (count($allowed) === 1 && $allowed[0] === '')) return;
    $origin = $_SERVER['HTTP_ORIGIN'] ?? ($_SERVER['HTTP_REFERER'] ?? null);
    if ($origin === null) return; // mobile / no origin — skip
    if (!in_array($origin, $allowed, true)) {
        errorResponse(403, 'origin_forbidden', 'Origin not allowed');
        exit;
    }
}

/** POST /api/v1/track — single event. */
function handleTrack(): void {
    checkOrigin();
    $projectId = requireApiKey();
    $rawApiKey = $_SERVER['HTTP_X_QM_API_KEY'] ?? '';
    $apiKeyLast4 = strlen($rawApiKey) >= 4 ? substr($rawApiKey, -4) : null;
    enforceLimit("track:{$projectId}", RATE_LIMIT_RPS, RATE_LIMIT_BURST);
    $ip = clientIp() ?? 'unknown';
    if (defined('INGEST_IP_ENABLED') && INGEST_IP_ENABLED && $ip !== 'unknown') {
        enforceLimit("track:ip:{$ip}", INGEST_IP_RPS, INGEST_IP_BURST);
    }
    $event     = getJsonBody();
    $err = validateEvent($event);
    if ($err !== null) {
        errorResponse(400, 'schema_violation', $err);
        return;
    }
    // Stage 3 — strict schema event-name allowlist
    $strict = getStrictSchema($projectId);
    if ($strict['enabled'] && !empty($strict['allowed'])) {
        $strictErr = validateEventStrict($event, $strict['allowed']);
        if ($strictErr !== null) {
            errorResponse(422, 'unknown_event', $strictErr);
            return;
        }
    }
    // Per-install (anonymousId) abuse defense — Stage 2. The raw id is never stored
    // (salt-hashed in install_meta). See docs/security/publishable-api-key.md.
    $anonymousId = is_array($event['ctx'] ?? null) ? ($event['ctx']['anonymous_id'] ?? null) : null;
    if (is_string($anonymousId)) {
        $installErr = enforceInstallLimits($projectId, $anonymousId);
        if ($installErr !== null) return;  // enforceInstallLimits already responded
    }
    insertEvent($projectId, $event);
    // Stage 4 — audit log
    $aHash = null;
    if (is_string($anonymousId)) {
        $salt = getInstallSalt($projectId);
        if ($salt !== null) $aHash = hashInstallId($salt, $anonymousId);
    }
    if (function_exists('auditLog')) {
        auditLog($projectId, $rawApiKey, $ip, $aHash, $event['event'], 'accepted', null);
    }
    jsonResponse(202, ['ok' => true, 'queued' => 1]);
}

/** POST /api/v1/track/batch — up to 100 events in one call. */
function handleTrackBatch(): void {
    checkOrigin();
    $projectId = requireApiKey();
    $rawApiKey = $_SERVER['HTTP_X_QM_API_KEY'] ?? '';
    enforceLimit("track:{$projectId}", RATE_LIMIT_RPS, RATE_LIMIT_BURST);
    $ip = clientIp() ?? 'unknown';
    if (defined('INGEST_IP_ENABLED') && INGEST_IP_ENABLED && $ip !== 'unknown') {
        enforceLimit("track:ip:{$ip}", INGEST_IP_RPS, INGEST_IP_BURST);
    }
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
    // Stage 3 — strict schema event-name allowlist
    $strict = getStrictSchema($projectId);
    if ($strict['enabled'] && !empty($strict['allowed'])) {
        foreach ($events as $i => $event) {
            $strictErr = validateEventStrict($event, $strict['allowed']);
            if ($strictErr !== null) {
                errorResponse(400, 'schema_violation', "events[$i]: $strictErr");
                return;
            }
        }
    }
    // Per-install (anonymousId) abuse defense — Stage 2. Apply per distinct install in
    // the batch (salt-hashed; raw id never stored). All-or-nothing.
    if (defined('INGEST_INSTALL_ENABLED') && INGEST_INSTALL_ENABLED) {
        $counts = [];
        foreach ($events as $ev) {
            $aid = is_array($ev['ctx'] ?? null) ? ($ev['ctx']['anonymous_id'] ?? null) : null;
            if (is_string($aid) && $aid !== '') {
                $counts[$aid] = ($counts[$aid] ?? 0) + 1;
            }
        }
        $salt = getInstallSalt($projectId);
        if ($salt !== null) {
            foreach ($counts as $aid => $n) {
                $hash = hashInstallId($salt, $aid);
                $install = upsertInstall($projectId, $hash, $n);
                if (!empty($install['revoked'])) {
                    auditLog($projectId, $rawApiKey, $ip, $hash, 'batch', 'quarantine', 'auto-revoked ramp-up');
                    errorResponse(403, 'install_revoked', 'Install has been revoked due to anomalous activity.');
                    return;
                }
                $burst = defined('INGEST_INSTALL_BURST') ? (int)INGEST_INSTALL_BURST : 30;
                $rps   = defined('INGEST_INSTALL_RPS')   ? (int)INGEST_INSTALL_RPS   : 1;
                enforceLimit("track:install:$projectId:$hash", $rps, $burst);
            }
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
    auditLog($projectId, $rawApiKey, $ip, null, 'batch(' . count($events) . ')', 'accepted', null);
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

/** Stage 3 — per-project event-name allowlist. Returns null if allowed, or an error string. */
function validateEventStrict(array $event, array $allowedEvents): ?string {
    if (empty($allowedEvents)) return null;
    return in_array($event['event'], $allowedEvents, true)
        ? null
        : "unknown_event: {$event['event']}";
}

/** Fetch strict-schema config for a project. Returns ['enabled' => bool, 'allowed' => array]. */
function getStrictSchema(string $projectId): array {
    $stmt = getDb()->prepare('SELECT strict_schema, allowed_events FROM projects WHERE id = ? LIMIT 1');
    $stmt->execute([$projectId]);
    $row = $stmt->fetch();
    if ($row === false) return ['enabled' => false, 'allowed' => []];
    $allowed = [];
    if (!empty($row['allowed_events'])) {
        $decoded = json_decode($row['allowed_events'], true);
        if (is_array($decoded)) $allowed = $decoded;
    }
    return ['enabled' => (bool)($row['strict_schema'] ?? false), 'allowed' => $allowed];
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
        normalizeCountry($ctx['country'] ?? null),
        deriveDeviceClass(
            is_string($ctx['ua'] ?? null) ? $ctx['ua'] : null,
            is_string($sdk['platform'] ?? null) ? $sdk['platform'] : null,
        ),
        is_string($ctx['language'] ?? null) ? substr($ctx['language'], 0, 10) : null,
        is_string($sdk['platform'] ?? null) ? substr($sdk['platform'], 0, 20) : null,
        is_string($sdk['version']  ?? null) ? substr($sdk['version'],  0, 20) : null,
        normalizeDuration($event['props']['duration_ms'] ?? null),
    ]);
}
