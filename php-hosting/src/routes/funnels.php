<?php

require_once __DIR__ . '/../funnelAnalyze.php';

/**
 * GET /api/v1/projects/{id}/funnels/{funnelKey}/results ?range=7d&breakdown=platform&trend=1
 * → step-by-step conversion, drop-off, time-to-convert, and optional breakdown/trend.
 */
function handleFunnelResults(string $projectId, string $funnelKey): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);

    $funnel = findFunnelRow($projectId, $funnelKey);
    if ($funnel === null) {
        errorResponse(404, 'not_found', 'Funnel not found');
        return;
    }
    $steps = json_decode($funnel['steps'], true) ?? [];
    $windowSecondsVal = (int)$funnel['window_seconds'];
    $eventNames = array_values(array_unique(array_map(fn($s) => $s['event'], $steps)));
    if (empty($eventNames)) {
        jsonResponse(200, funnelResultsEnvelope($funnel, $windowSecondsVal, $steps, gmdate('Y-m-d\TH:i:s\Z'), gmdate('Y-m-d\TH:i:s\Z'),
            funnelAnalyze($steps, $windowSecondsVal, [], gmdate('Y-m-d\TH:i:s\Z'), gmdate('Y-m-d\TH:i:s\Z'), null, false,
                $funnel['count_mode'], $funnel['identity_scope'], $funnel['correlation_property']), false));
        return;
    }

    $rangeSeconds = windowSeconds(365);
    $now = time();
    $fromIso = gmdate('Y-m-d\TH:i:s\Z', $now - $rangeSeconds);
    $toIso = gmdate('Y-m-d\TH:i:s\Z', $now);
    // Widened upper bound: a completion just after the requested range still counts for an
    // actor who entered inside it (funnelAnalyze's entry-time filter excludes anyone whose
    // ENTRY falls outside [from, to)).
    $widenedToIso = gmdate('Y-m-d\TH:i:s\Z', $now + $windowSecondsVal);

    $cap = 50000;
    $placeholders = implode(',', array_fill(0, count($eventNames), '?'));
    $stmt = getDb()->prepare(
        "SELECT session_id, install_hash, event_name, screen, props, ts, country, platform, device_class, language
           FROM events
          WHERE project_id = ? AND event_name IN ($placeholders) AND ts >= ? AND ts < ?
          ORDER BY ts ASC
          LIMIT $cap"
    );
    $stmt->execute(array_merge([$projectId], $eventNames, [$fromIso, $widenedToIso]));
    $dbRows = $stmt->fetchAll();
    $truncated = count($dbRows) >= $cap;

    $rows = [];
    foreach ($dbRows as $r) {
        $actor = actorKey($r['install_hash'], $r['session_id']);
        if ($actor === null) continue;
        $rows[] = [
            'actor_key' => $actor,
            'install_hash' => $r['install_hash'],
            'session_id' => $r['session_id'],
            'event_name' => $r['event_name'],
            'ts' => $r['ts'],
            'screen' => $r['screen'],
            'props' => is_string($r['props']) ? (json_decode($r['props'], true) ?? []) : [],
            'country' => $r['country'],
            'platform' => $r['platform'],
            'device_class' => $r['device_class'],
            'language' => $r['language'],
        ];
    }

    $breakdownDimension = $_GET['breakdown'] ?? null;
    if (!in_array($breakdownDimension, ['country', 'platform', 'device_class', 'language'], true)) {
        $breakdownDimension = null;
    }
    $withTrend = ($_GET['trend'] ?? null) === '1';

    $analyzed = funnelAnalyze($steps, $windowSecondsVal, $rows, $fromIso, $toIso, $breakdownDimension, $withTrend,
        $funnel['count_mode'], $funnel['identity_scope'], $funnel['correlation_property']);

    jsonResponse(200, funnelResultsEnvelope($funnel, $windowSecondsVal, $steps, $fromIso, $toIso, $analyzed, $truncated));
}

function funnelResultsEnvelope(array $funnel, int $windowSeconds, array $steps, string $fromIso, string $toIso, array $analyzed, bool $truncated): array {
    return [
        'funnel' => [
            'funnel_key' => $funnel['funnel_key'],
            'name' => $funnel['name'],
            'window_seconds' => $windowSeconds,
            'steps' => funnelStepsForWire($steps),
            'count_mode' => $funnel['count_mode'],
            'identity_scope' => $funnel['identity_scope'],
            'correlation_property' => $funnel['correlation_property'],
        ],
        'range' => ['from' => $fromIso, 'to' => $toIso],
        'counted_by' => $analyzed['counted_by'],
        'entered' => $analyzed['entered'],
        'converted' => $analyzed['converted'],
        'overall_conversion' => $analyzed['overall_conversion'],
        'median_total_ms' => $analyzed['median_total_ms'],
        'steps' => $analyzed['steps'],
        'breakdown' => $analyzed['breakdown'],
        'trend' => $analyzed['trend'],
        'truncated' => $truncated,
    ];
}

/** GET /api/v1/projects/{id}/funnels → list active (non-archived) funnels. */
function handleFunnelsList(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);

    $stmt = getDb()->prepare(
        'SELECT funnel_key, name, description, steps, window_seconds, source, locked, count_mode, identity_scope, correlation_property, created_at, updated_at
           FROM funnels WHERE project_id = ? AND archived_at IS NULL ORDER BY created_at'
    );
    $stmt->execute([$projectId]);
    jsonResponse(200, ['funnels' => array_map('rowToFunnelResponse', $stmt->fetchAll())]);
}

/**
 * POST /api/v1/projects/{id}/funnels { funnel_key, name, description?, steps, window_seconds? }
 * → creates a funnel. Admin or developer only.
 */
function handleFunnelsCreate(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);
    requireRole($session, ['admin', 'developer']);

    $body = getJsonBody();
    $definition = [
        'funnel_key' => $body['funnel_key'] ?? '',
        'name' => $body['name'] ?? '',
        'steps' => $body['steps'] ?? [],
        'window_seconds' => $body['window_seconds'] ?? FUNNEL_DEFAULT_WINDOW_SECONDS,
        'count_mode' => $body['count_mode'] ?? 'actor',
        'identity_scope' => $body['identity_scope'] ?? 'install_or_session',
        'correlation_property' => $body['correlation_property'] ?? null,
    ];
    $description = isset($body['description']) ? trim((string)$body['description']) : null;
    if ($description === '') $description = null;

    $existingCount = countActiveFunnels($projectId);
    $errors = validateFunnelDefinition($definition, $existingCount);
    if (!empty($errors)) {
        errorResponse(400, 'schema_violation', implode('; ', $errors));
        return;
    }

    $stmt = getDb()->prepare('SELECT 1 FROM funnels WHERE project_id = ? AND funnel_key = ? AND archived_at IS NULL LIMIT 1');
    $stmt->execute([$projectId, $definition['funnel_key']]);
    if ($stmt->fetchColumn() !== false) {
        errorResponse(409, 'funnel_exists', 'A funnel with this key already exists');
        return;
    }

    $id = uuid4();
    $now = now();
    getDb()->prepare(
        'INSERT INTO funnels
            (id, project_id, funnel_key, name, description, steps, window_seconds, source, locked, count_mode, identity_scope, correlation_property, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)' 
    )->execute([
        $id, $projectId, $definition['funnel_key'], $definition['name'], $description,
        json_encode(funnelStepsForWire($definition['steps']), JSON_UNESCAPED_UNICODE), $definition['window_seconds'],
        'dashboard', 0, $definition['count_mode'], $definition['identity_scope'], $definition['correlation_property'], $now, $now,
    ]);

    jsonResponse(201, findFunnelResponse($projectId, $definition['funnel_key']));
}

/**
 * PATCH /api/v1/projects/{id}/funnels/{funnelKey} { name?, description?, steps?, window_seconds? }
 * → partial update. Always locks the funnel (source stays whatever it was; a locked funnel
 * is skipped by SDK auto-registration so an analyst's edit is never silently overwritten).
 */
function handleFunnelUpdate(string $projectId, string $funnelKey): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);
    requireRole($session, ['admin', 'developer']);

    $existing = findFunnelRow($projectId, $funnelKey);
    if ($existing === null) {
        errorResponse(404, 'not_found', 'Funnel not found');
        return;
    }

    $body = getJsonBody();
    $name = array_key_exists('name', $body) ? $body['name'] : $existing['name'];
    $description = array_key_exists('description', $body) ? $body['description'] : $existing['description'];
    if ($description === '') $description = null;
    $steps = array_key_exists('steps', $body) ? $body['steps'] : json_decode($existing['steps'], true);
    $windowSeconds = array_key_exists('window_seconds', $body) ? $body['window_seconds'] : (int)$existing['window_seconds'];

    $definition = ['funnel_key' => $funnelKey, 'name' => $name, 'steps' => $steps, 'window_seconds' => $windowSeconds,
        'count_mode' => $existing['count_mode'], 'identity_scope' => $existing['identity_scope'], 'correlation_property' => $existing['correlation_property']];
    // Excludes the funnel being edited from its own count.
    $errors = validateFunnelDefinition($definition, countActiveFunnels($projectId) - 1);
    if (!empty($errors)) {
        errorResponse(400, 'schema_violation', implode('; ', $errors));
        return;
    }

    getDb()->prepare(
        'UPDATE funnels SET name = ?, description = ?, steps = ?, window_seconds = ?, locked = 1, updated_at = ?
          WHERE project_id = ? AND funnel_key = ? AND archived_at IS NULL'
    )->execute([
        $name, $description, json_encode(funnelStepsForWire($steps), JSON_UNESCAPED_UNICODE), $windowSeconds, now(),
        $projectId, $funnelKey,
    ]);

    jsonResponse(200, findFunnelResponse($projectId, $funnelKey));
}

/** DELETE /api/v1/projects/{id}/funnels/{funnelKey} → soft-delete (archive). */
function handleFunnelDelete(string $projectId, string $funnelKey): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);
    requireRole($session, ['admin', 'developer']);

    $stmt = getDb()->prepare(
        'UPDATE funnels SET archived_at = ? WHERE project_id = ? AND funnel_key = ? AND archived_at IS NULL'
    );
    $stmt->execute([now(), $projectId, $funnelKey]);
    if ($stmt->rowCount() === 0) {
        errorResponse(404, 'not_found', 'Funnel not found');
        return;
    }
    jsonResponse(200, ['deleted' => true]);
}

/**
 * POST /api/v1/funnels/register { funnels: [ { funnel_key, name, description?, steps,
 * window_seconds? }, ... ] } → SDK auto-registration, X-QM-Api-Key authenticated (the
 * project is derived from the key, matching /track). Per funnel: skip silently if a
 * dashboard edit has locked it; otherwise upsert by key with source='sdk'. One invalid or
 * over-cap definition in the batch is rejected on its own — it does not fail the rest.
 */
function handleFunnelsRegister(): void {
    $projectId = requireApiKey();
    enforceLimit("funnels:register:{$projectId}", 1, 10);

    $body = getJsonBody();
    $funnels = $body['funnels'] ?? null;
    if (!is_array($funnels)) {
        errorResponse(400, 'invalid_request', 'Body must be { "funnels": [ ... ] }');
        return;
    }
    $namespace = $body['namespace'] ?? null;
    $revision = $body['revision'] ?? null;
    if ($namespace !== null && $revision !== null && !acceptFunnelManifestRevision($projectId, $namespace, $revision)) {
        jsonResponse(200, ['registered' => [], 'skipped_locked' => [], 'rejected' => [], 'ignored_stale_manifest' => true]);
        return;
    }

    $registered = [];
    $skippedLocked = [];
    $rejected = [];

    foreach ($funnels as $incoming) {
        if (!is_array($incoming)) continue;
        $funnelKey = $incoming['funnel_key'] ?? '';
        if (!is_string($funnelKey) || $funnelKey === '') continue;

        $existing = findFunnelRow($projectId, $funnelKey);
        $archived = $existing === null ? findArchivedFunnelRow($projectId, $funnelKey) : null;
        if ($existing !== null && (bool)$existing['locked']) {
            $skippedLocked[] = $funnelKey;
            continue;
        }

        $name = $incoming['name'] ?? '';
        $description = isset($incoming['description']) ? trim((string)$incoming['description']) : null;
        if ($description === '') $description = null;
        $steps = $incoming['steps'] ?? [];
        $windowSeconds = $incoming['window_seconds'] ?? FUNNEL_DEFAULT_WINDOW_SECONDS;
        $countMode = $incoming['count_mode'] ?? 'actor';
        $identityScope = $incoming['identity_scope'] ?? 'install_or_session';
        $correlationProperty = $incoming['correlation_property'] ?? null;

        $definition = ['funnel_key' => $funnelKey, 'name' => $name, 'steps' => $steps, 'window_seconds' => $windowSeconds,
            'count_mode' => $countMode, 'identity_scope' => $identityScope, 'correlation_property' => $correlationProperty];
        $existingCount = countActiveFunnels($projectId) - ($existing !== null ? 1 : 0);
        $errors = validateFunnelDefinition($definition, $existingCount);
        if (!empty($errors)) {
            $rejected[$funnelKey] = implode('; ', $errors);
            continue;
        }

        $stepsJson = json_encode(funnelStepsForWire($steps), JSON_UNESCAPED_UNICODE);
        $now = now();
        if ($existing === null && $archived === null) {
            getDb()->prepare(
                'INSERT INTO funnels
                    (id, project_id, funnel_key, name, description, steps, window_seconds, source, locked, count_mode, identity_scope, correlation_property, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)' 
            )->execute([uuid4(), $projectId, $funnelKey, $name, $description, $stepsJson, $windowSeconds, 'sdk', 0,
                $countMode, $identityScope, $correlationProperty, $now, $now]);
        } elseif ($archived !== null) {
            // The unique project/key row remains after deletion. Restore it rather than trying
            // an insert which would otherwise make SDK registration fail forever.
            getDb()->prepare(
                'UPDATE funnels SET name = ?, description = ?, steps = ?, window_seconds = ?, source = ?, locked = 0,
                    count_mode = ?, identity_scope = ?, correlation_property = ?, archived_at = NULL, updated_at = ?
                  WHERE project_id = ? AND funnel_key = ? AND archived_at IS NOT NULL'
            )->execute([$name, $description, $stepsJson, $windowSeconds, 'sdk', $countMode, $identityScope,
                $correlationProperty, $now, $projectId, $funnelKey]);
        } else {
            getDb()->prepare(
                'UPDATE funnels SET name = ?, description = ?, steps = ?, window_seconds = ?, source = ?, locked = 0,
                    count_mode = ?, identity_scope = ?, correlation_property = ?, updated_at = ?
                  WHERE project_id = ? AND funnel_key = ? AND archived_at IS NULL'
            )->execute([$name, $description, $stepsJson, $windowSeconds, 'sdk', $countMode, $identityScope,
                $correlationProperty, $now, $projectId, $funnelKey]);
        }
        $registered[] = $funnelKey;
    }

    jsonResponse(200, ['registered' => $registered, 'skipped_locked' => $skippedLocked, 'rejected' => $rejected, 'ignored_stale_manifest' => false]);
}

/** Active (non-archived) funnel count for a project. */
function countActiveFunnels(string $projectId): int {
    $stmt = getDb()->prepare('SELECT COUNT(*) FROM funnels WHERE project_id = ? AND archived_at IS NULL');
    $stmt->execute([$projectId]);
    return (int)$stmt->fetchColumn();
}

function findFunnelRow(string $projectId, string $funnelKey): ?array {
    $stmt = getDb()->prepare(
        'SELECT funnel_key, name, description, steps, window_seconds, source, locked, count_mode, identity_scope, correlation_property, created_at, updated_at
           FROM funnels WHERE project_id = ? AND funnel_key = ? AND archived_at IS NULL LIMIT 1'
    );
    $stmt->execute([$projectId, $funnelKey]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}

function findArchivedFunnelRow(string $projectId, string $funnelKey): ?array {
    $stmt = getDb()->prepare(
        'SELECT funnel_key FROM funnels WHERE project_id = ? AND funnel_key = ? AND archived_at IS NOT NULL LIMIT 1'
    );
    $stmt->execute([$projectId, $funnelKey]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}

/** Returns false for equal/older revisions, preventing old app releases from clobbering code-owned funnels. */
function acceptFunnelManifestRevision(string $projectId, $namespace, $revision): bool {
    if (!is_string($namespace) || $namespace === '' || !is_int($revision)) return true;
    $db = getDb();
    $stmt = $db->prepare('SELECT revision FROM funnel_manifests WHERE project_id = ? AND namespace = ? LIMIT 1');
    $stmt->execute([$projectId, $namespace]);
    $current = $stmt->fetchColumn();
    if ($current !== false && $revision <= (int)$current) return false;
    if ($current === false) {
        $db->prepare('INSERT INTO funnel_manifests (project_id, namespace, revision, updated_at) VALUES (?, ?, ?, ?)')
            ->execute([$projectId, $namespace, $revision, now()]);
    } else {
        $db->prepare('UPDATE funnel_manifests SET revision = ?, updated_at = ? WHERE project_id = ? AND namespace = ?')
            ->execute([$revision, now(), $projectId, $namespace]);
    }
    return true;
}

function findFunnelResponse(string $projectId, string $funnelKey): ?array {
    $row = findFunnelRow($projectId, $funnelKey);
    return $row === null ? null : rowToFunnelResponse($row);
}

function rowToFunnelResponse(array $row): array {
    return [
        'funnel_key' => $row['funnel_key'],
        'name' => $row['name'],
        'description' => $row['description'],
        'steps' => funnelStepsForWire(json_decode($row['steps'], true) ?? []),
        'window_seconds' => (int)$row['window_seconds'],
        'source' => $row['source'],
        'locked' => (bool)$row['locked'],
        'count_mode' => $row['count_mode'],
        'identity_scope' => $row['identity_scope'],
        'correlation_property' => $row['correlation_property'],
        'created_at' => $row['created_at'],
        'updated_at' => $row['updated_at'],
    ];
}
