<?php

/** Stage 5 — origin/referer check for non-mobile traffic. */
function checkOrigin(): void {
    $origin = $_SERVER['HTTP_ORIGIN'] ?? ($_SERVER['HTTP_REFERER'] ?? null);
    if ($origin === null) return; // mobile / no origin — skip

    if (!defined('ALLOWED_ORIGINS')) return;
    $configured = constant('ALLOWED_ORIGINS');
    if (is_array($configured)) {
        $allowed = $configured;
    } elseif (is_string($configured)) {
        // Older shared-hosting configs used serialize([...]); current configs use a
        // comma-separated string. Support both during an in-place upgrade.
        $legacy = @unserialize($configured);
        $allowed = is_array($legacy) ? $legacy : explode(',', $configured);
    } else {
        return;
    }
    $allowed = array_values(array_filter(array_map(
        static fn($value) => is_string($value) ? trim($value) : '',
        $allowed,
    ), static fn($value) => $value !== ''));
    if (empty($allowed)) return;
    if (!in_array($origin, $allowed, true)) {
        errorResponse(403, 'origin_forbidden', 'Origin not allowed');
        exit;
    }
}

/** POST /api/v1/track — single event. */
function handleTrack(): void {
    header('X-QM-Track-Version: 2026-08-12.3');
    try {
        handleTrackRequest();
    } catch (Throwable $e) {
        trackingFailure('track_unclassified', $e);
    }
}

/** Implementation split so the public handler can safely classify every route-level failure. */
function handleTrackRequest(): void {
    try {
        checkOrigin();
    } catch (Throwable $e) {
        trackingFailure('origin_check', $e);
        return;
    }
    try {
        $projectId = requireApiKey();
    } catch (Throwable $e) {
        trackingFailure('api_key_lookup', $e);
        return;
    }
    $rawApiKey = $_SERVER['HTTP_X_QM_API_KEY'] ?? '';
    // Existing shared-hosting config.php files predate the rate-limit constants. Never let
    // their absence turn every otherwise valid tracking request into a PHP 500.
    $rateLimitRps = defined('RATE_LIMIT_RPS') ? (int)RATE_LIMIT_RPS : 10;
    $rateLimitBurst = defined('RATE_LIMIT_BURST') ? (int)RATE_LIMIT_BURST : 60;
    try {
        enforceLimit("track:{$projectId}", $rateLimitRps, $rateLimitBurst);
        $ip = clientIp() ?? 'unknown';
        if (defined('INGEST_IP_ENABLED') && INGEST_IP_ENABLED && $ip !== 'unknown') {
            $ipRps = defined('INGEST_IP_RPS') ? (int)INGEST_IP_RPS : 5;
            $ipBurst = defined('INGEST_IP_BURST') ? (int)INGEST_IP_BURST : 60;
            enforceLimit("track:ip:{$ip}", $ipRps, $ipBurst);
        }
    } catch (Throwable $e) {
        trackingFailure('rate_limit', $e);
        return;
    }
    try {
        $event = getJsonBody();
        $err = validateEvent($event);
    } catch (Throwable $e) {
        trackingFailure('request_validation', $e);
        return;
    }
    if ($err !== null) {
        errorResponse(400, 'schema_violation', $err);
        return;
    }
    // Stage 3 — strict schema event-name allowlist
    try {
        $strict = getStrictSchema($projectId);
    } catch (Throwable $e) {
        trackingFailure('strict_schema', $e);
        return;
    }
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
    // Funnel/analytics identity hash — a SEPARATE salt/namespace from the abuse-defense hash
    // computed below: analytics_salt is never rotated on key regeneration, so funnel history
    // survives a rotation even though install_meta rows do not.
    $analyticsHash = null;
    if (is_string($anonymousId) && $anonymousId !== '') {
        try {
            $aSalt = ensureAnalyticsSalt($projectId);
        } catch (Throwable $e) {
            trackingFailure('analytics_identity', $e);
            return;
        }
        if ($aSalt !== null) $analyticsHash = hashInstallId($aSalt, $anonymousId);
    }
    try {
        insertEvent($projectId, $event, $analyticsHash);
    } catch (Throwable $e) {
        trackingFailure('event_insert', $e);
        return;
    }
    // Stage 4 — audit log
    $aHash = null;
    if (is_string($anonymousId)) {
        $salt = getInstallSalt($projectId);
        if ($salt !== null) $aHash = hashInstallId($salt, $anonymousId);
    }
    if (function_exists('auditLog')) {
        try {
            auditLog($projectId, $rawApiKey, $ip, $aHash, $event['event'], 'accepted', null);
        } catch (Throwable $e) {
            trackingFailure('audit_log', $e);
            return;
        }
    }
    jsonResponse(202, ['ok' => true, 'queued' => 1]);
}

/**
 * Exposes only the failed ingestion stage to a request that has already passed API-key
 * authentication. The exception remains server-side in error_log; this lets operators of
 * shared hosting diagnose a broken schema without enabling globally unsafe PHP debug output.
 */
function trackingFailure(string $stage, Throwable $e): void {
    error_log("[QuietMetrix] tracking stage {$stage}: " . get_class($e) . ': ' . $e->getMessage());
    jsonResponse(500, [
        'error' => 'internal_error',
        'message' => 'Tracking failed',
        'stage' => $stage,
    ]);
}

/** POST /api/v1/track/batch — up to 100 events in one call. */
function handleTrackBatch(): void {
    header('X-QM-Track-Version: 2026-08-12.3');
    try {
        handleTrackBatchRequest();
    } catch (Throwable $e) {
        trackingFailure('batch_unclassified', $e);
    }
}

/** Batch implementation split for the same full-route diagnostic boundary as single ingest. */
function handleTrackBatchRequest(): void {
    checkOrigin();
    $projectId = requireApiKey();
    $rawApiKey = $_SERVER['HTTP_X_QM_API_KEY'] ?? '';
    $rateLimitRps = defined('RATE_LIMIT_RPS') ? (int)RATE_LIMIT_RPS : 10;
    $rateLimitBurst = defined('RATE_LIMIT_BURST') ? (int)RATE_LIMIT_BURST : 60;
    enforceLimit("track:{$projectId}", $rateLimitRps, $rateLimitBurst);
    $ip = clientIp() ?? 'unknown';
    if (defined('INGEST_IP_ENABLED') && INGEST_IP_ENABLED && $ip !== 'unknown') {
        $ipRps = defined('INGEST_IP_RPS') ? (int)INGEST_IP_RPS : 5;
        $ipBurst = defined('INGEST_IP_BURST') ? (int)INGEST_IP_BURST : 60;
        enforceLimit("track:ip:{$ip}", $ipRps, $ipBurst);
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
    // Funnel/analytics identity hash — a SEPARATE salt/namespace from the abuse-defense hashes
    // computed above: analytics_salt is never rotated on key regeneration, so funnel history
    // survives a rotation even though install_meta rows do not.
    $analyticsHashByAnonymousId = [];
    $distinctAnonymousIds = [];
    foreach ($events as $ev) {
        $aid = is_array($ev['ctx'] ?? null) ? ($ev['ctx']['anonymous_id'] ?? null) : null;
        if (is_string($aid) && $aid !== '') $distinctAnonymousIds[$aid] = true;
    }
    if (!empty($distinctAnonymousIds)) {
        $aSalt = ensureAnalyticsSalt($projectId);
        if ($aSalt !== null) {
            foreach (array_keys($distinctAnonymousIds) as $aid) {
                $analyticsHashByAnonymousId[$aid] = hashInstallId($aSalt, $aid);
            }
        }
    }

    $db = getDb();
    $db->beginTransaction();
    try {
        foreach ($events as $event) {
            $aid = is_array($event['ctx'] ?? null) ? ($event['ctx']['anonymous_id'] ?? null) : null;
            $analyticsHash = is_string($aid) ? ($analyticsHashByAnonymousId[$aid] ?? null) : null;
            insertEvent($projectId, $event, $analyticsHash);
        }
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

/**
 * [$installHash] is the analytics-salt hash pre-computed by the caller from
 * `event.ctx.anonymous_id`. The raw id is never read here and never lands in the events
 * table — only the hash does. See docs/security/publishable-api-key.md — Privacy note.
 */
function insertEvent(string $projectId, array $event, ?string $installHash = null): void {
    $sdk = $event['sdk'] ?? [];
    $ctx = $event['ctx'] ?? [];

    getDb()->prepare(
        'INSERT INTO events
            (project_id, event_name, screen, props, session_id, ts, received_at,
             was_offline, country, device_class, language, platform, sdk_version, duration_ms,
             install_hash)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)'
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
        $installHash,
    ]);
}
