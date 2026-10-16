<?php

/** Minimal publishable-key configuration for on-device funnel evaluation. */
function handleFunnelsConfigV2(): void {
    $projectId = requireApiKey();
    $ip = clientIp() ?? 'unknown';
    if (defined('INGEST_IP_ENABLED') && INGEST_IP_ENABLED && $ip !== 'unknown') {
        enforceLimit("funnels:config:ip:{$ip}", defined('INGEST_IP_RPS') ? (int)INGEST_IP_RPS : 5,
            defined('INGEST_IP_BURST') ? (int)INGEST_IP_BURST : 60);
    }
    header('Cache-Control: private, max-age=300');
    $stmt = getDb()->prepare("SELECT funnel_key, revision, steps, window_seconds, count_mode, identity_scope, correlation_property
        FROM funnels WHERE project_id=? AND archived_at IS NULL AND source='dashboard' AND revision>0 ORDER BY created_at");
    $stmt->execute([$projectId]);
    $funnels = [];
    foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $row) {
        $funnels[] = [
            'funnel_key' => $row['funnel_key'], 'revision' => (int)$row['revision'],
            'steps' => funnelStepsForWire(json_decode($row['steps'], true) ?? []),
            'window_seconds' => (int)$row['window_seconds'], 'count_mode' => $row['count_mode'],
            'identity_scope' => $row['identity_scope'], 'correlation_property' => $row['correlation_property'],
        ];
    }
    jsonResponse(200, ['namespace' => 'dashboard', 'cache_ttl_seconds' => 300, 'funnels' => $funnels]);
}

require_once __DIR__ . '/../funnelCounterAnalyze.php';

/**
 * GET /api/v1/projects/{id}/funnels/{funnelKey}/results ?range=7d
 * → step-by-step conversion and drop-off, aggregated from the funnel_step counter cells the
 * device itself already reduced its progress to. Mirrors
 * servers/ktor/.../routes/FunnelRoutes.kt's results handler -- keep the two in sync.
 *
 * breakdown/trend query params are still accepted (unrecognized query params are otherwise
 * ignored across this API) but no longer honored -- see funnelCounterAnalyze()'s doc comment
 * for what's gone versus deferred under aggregate-only ingest.
 */
function handleFunnelResults(string $projectId, string $funnelKey): void {
    requireAnalyticsAccess($projectId);

    $funnel = findFunnelRow($projectId, $funnelKey);
    if ($funnel === null) {
        errorResponse(404, 'not_found', 'Funnel not found');
        return;
    }
    if (isset($_GET['revision'])) {
        $revision = filter_var($_GET['revision'], FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
        if ($revision === false) { errorResponse(400, 'bad_request', 'Invalid revision'); return; }
        $funnel = findFunnelRevision($projectId, $funnelKey, $revision);
        if ($funnel === null) { errorResponse(404, 'not_found', 'Revision not found'); return; }
    }
    $steps = json_decode($funnel['steps'], true) ?? [];
    $windowSecondsVal = (int)$funnel['window_seconds'];

    $rangeSeconds = windowSeconds(365);
    $window = requestAnalyticsWindow($rangeSeconds);
    $fromIso = $window['from'];
    $toIso = $window['to'];
    $fromDay = $window['from_day'];
    $toDay = $window['to_day'];

    if (($funnel['source'] ?? '') === 'dashboard' && (int)($funnel['revision'] ?? 0) > 0) {
        $cells = readDashboardFunnelV2Steps($projectId, $funnelKey, (int)$funnel['revision'], $fromDay, $toDay);
    } else {
        $allCells = counterReadCells($projectId, 'funnel_step', $fromDay, $toDay);
        $cells = [];
        foreach ($allCells as $c) {
            if (($c['dims']['f'] ?? null) !== $funnelKey || (string)($c['dims']['rev'] ?? '') !== (string)($funnel['revision'] ?? 0)) continue;
            $step = (int)($c['dims']['step'] ?? 0);
            if ($step <= 0) continue;
            $cells[] = ['step' => $step, 'n' => $c['n']];
        }
    }

    $analyzed = funnelCounterAnalyze($steps, $cells, $funnel['count_mode']);

    // No row cap under an aggregate read -- counters are already the rolled-up result, not a
    // set of rows that can overflow.
    jsonResponse(200, funnelResultsEnvelope($funnel, $windowSecondsVal, $steps, $fromIso, $toIso, $analyzed, false));
}

/** Reads dashboard funnel completions by entry cohort and immutable definition revision. */
function readDashboardFunnelV2Steps(string $projectId, string $funnelKey, int $revision, string $fromDay, string $toDay): array {
    require_once __DIR__ . '/dashboard.php';
    $through = (new DateTimeImmutable($toDay, new DateTimeZone('UTC')))->modify('+90 days')->format('Y-m-d');
    $stmt = getDb()->prepare("SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2
        WHERE project_id=? AND metric='funnel_step_v2' AND hour=-1 AND day BETWEEN ? AND ?");
    $stmt->execute([$projectId, $fromDay, $through]);
    $rows = selectCounterProjectionRows($stmt->fetchAll(PDO::FETCH_ASSOC), 'all'); $groups = [];
    foreach ($rows as $row) {
        $dims = json_decode($row['dims'], true) ?? [];
        if (($dims['namespace'] ?? '') !== 'dashboard' || ($dims['f'] ?? '') !== $funnelKey ||
            (int)($dims['revision'] ?? 0) !== $revision || !isset($dims['entry_day']) ||
            $dims['entry_day'] < $fromDay || $dims['entry_day'] > $toDay) continue;
        $step = (int)($dims['step'] ?? 0);
        if ($step < 1 || $step > 10) continue;
        $checked = counterV2Validate('funnel_step_v2', $dims);
        if (!($checked['valid'] ?? false)) continue;
        $groups[$step][$checked['hash']]['dims'] = $dims;
        $groups[$step][$checked['hash']]['rows'][] = $row;
    }
    $cells = [];
    $start = new DateTimeImmutable($fromDay, new DateTimeZone('UTC')); $end = new DateTimeImmutable($toDay, new DateTimeZone('UTC'));
    foreach ($groups as $step => $cohorts) {
        $count = 0;
        foreach ($cohorts as $cohort) {
            $entry = new DateTimeImmutable($cohort['dims']['entry_day'], new DateTimeZone('UTC'));
            $eligible = reportMetricWitnessCountV2(getDb(), $projectId, 'funnel_step_v2', $cohort['dims'], $entry, $entry,
                'all', $cohort['rows'], $entry, 1, $entry, new DateTimeImmutable($through, new DateTimeZone('UTC')));
            if ($eligible >= 5) $count += array_sum(array_map(static fn($r) => (int)$r['n'], $cohort['rows']));
        }
        if ($count > 0) $cells[] = ['step' => (int)$step, 'n' => $count];
    }
    return $cells;
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
            'revision' => (int)($funnel['revision'] ?? 0),
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
    requireAnalyticsAccess($projectId);

    $stmt = getDb()->prepare(
        'SELECT funnel_key, name, description, steps, window_seconds, source, locked, count_mode, identity_scope, correlation_property, revision, created_at, updated_at
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
    $db = getDb();
    $db->beginTransaction();
    try {
    $db->prepare(
        'INSERT INTO funnels
            (id, project_id, funnel_key, name, description, steps, window_seconds, source, locked, count_mode, identity_scope, correlation_property, revision, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    )->execute([
        $id, $projectId, $definition['funnel_key'], $definition['name'], $description,
        json_encode(funnelStepsForWire($definition['steps']), JSON_UNESCAPED_UNICODE), $definition['window_seconds'],
        'dashboard', 0, $definition['count_mode'], $definition['identity_scope'], $definition['correlation_property'], 1, $now, $now,
    ]);
    insertFunnelRevisionSnapshot($projectId, $definition, 1);
    $db->commit();
    } catch (Throwable $error) {
        if ($db->inTransaction()) $db->rollBack();
        throw $error;
    }

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
        'count_mode' => $body['count_mode'] ?? $existing['count_mode'], 'identity_scope' => $body['identity_scope'] ?? $existing['identity_scope'], 'correlation_property' => ($body['count_mode'] ?? '') === 'actor' ? null : ($body['correlation_property'] ?? $existing['correlation_property'])];
    // Excludes the funnel being edited from its own count.
    $errors = validateFunnelDefinition($definition, countActiveFunnels($projectId) - 1);
    if (!empty($errors)) {
        errorResponse(400, 'schema_violation', implode('; ', $errors));
        return;
    }

    $normalizedSteps = funnelStepsForWire($steps);
    $semanticChanged = funnelSemanticSteps($normalizedSteps) !== funnelSemanticSteps(json_decode($existing['steps'], true) ?? []) ||
        (int)$windowSeconds !== (int)$existing['window_seconds'] || $definition['count_mode'] !== $existing['count_mode'] || $definition['identity_scope'] !== $existing['identity_scope'] || $definition['correlation_property'] !== $existing['correlation_property'];
    $nextRevision = (int)$existing['revision'] + ($semanticChanged ? 1 : 0);
    $db = getDb();
    $db->beginTransaction();
    try {
    $db->prepare(
        'UPDATE funnels SET name = ?, description = ?, steps = ?, window_seconds = ?, count_mode = ?, identity_scope = ?, correlation_property = ?, revision = ?, locked = 1, updated_at = ?
          WHERE project_id = ? AND funnel_key = ? AND archived_at IS NULL'
    )->execute([
        $name, $description, json_encode($normalizedSteps, JSON_UNESCAPED_UNICODE), $windowSeconds, $definition['count_mode'], $definition['identity_scope'], $definition['correlation_property'], $nextRevision, now(),
        $projectId, $funnelKey,
    ]);
    if ($semanticChanged) insertFunnelRevisionSnapshot($projectId, $definition, $nextRevision);
    $db->commit();
    } catch (Throwable $error) {
        if ($db->inTransaction()) $db->rollBack();
        throw $error;
    }

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
                    (id, project_id, funnel_key, name, description, steps, window_seconds, source, locked, count_mode, identity_scope, correlation_property, revision, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
            )->execute([uuid4(), $projectId, $funnelKey, $name, $description, $stepsJson, $windowSeconds, 'sdk', 0,
                $countMode, $identityScope, $correlationProperty, 1, $now, $now]);
            insertFunnelRevisionSnapshot($projectId, $definition, 1);
        } elseif ($archived !== null) {
            // The unique project/key row remains after deletion. Restore it rather than trying
            // an insert which would otherwise make SDK registration fail forever.
            $revision = (int)$archived['revision'] + 1;
            getDb()->prepare(
                'UPDATE funnels SET name = ?, description = ?, steps = ?, window_seconds = ?, source = ?, locked = 0,
                    count_mode = ?, identity_scope = ?, correlation_property = ?, revision = ?, archived_at = NULL, updated_at = ?
                  WHERE project_id = ? AND funnel_key = ? AND archived_at IS NOT NULL'
            )->execute([$name, $description, $stepsJson, $windowSeconds, 'sdk', $countMode, $identityScope,
                $correlationProperty, $revision, $now, $projectId, $funnelKey]);
            insertFunnelRevisionSnapshot($projectId, $definition, $revision);
        } else {
            $semanticChanged = funnelSemanticSteps(json_decode($existing['steps'], true) ?? []) !== funnelSemanticSteps(funnelStepsForWire($steps)) ||
                (int)$existing['window_seconds'] !== (int)$windowSeconds || $existing['count_mode'] !== $countMode ||
                $existing['identity_scope'] !== $identityScope || $existing['correlation_property'] !== $correlationProperty;
            $revision = (int)$existing['revision'] + ($semanticChanged ? 1 : 0);
            getDb()->prepare(
                'UPDATE funnels SET name = ?, description = ?, steps = ?, window_seconds = ?, source = ?, locked = 0,
                    count_mode = ?, identity_scope = ?, correlation_property = ?, revision = ?, updated_at = ?
                  WHERE project_id = ? AND funnel_key = ? AND archived_at IS NULL'
            )->execute([$name, $description, $stepsJson, $windowSeconds, 'sdk', $countMode, $identityScope,
                $correlationProperty, $revision, $now, $projectId, $funnelKey]);
            if ($semanticChanged) insertFunnelRevisionSnapshot($projectId, $definition, $revision);
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
        'SELECT funnel_key, name, description, steps, window_seconds, source, locked, count_mode, identity_scope, correlation_property, revision, created_at, updated_at
           FROM funnels WHERE project_id = ? AND funnel_key = ? AND archived_at IS NULL LIMIT 1'
    );
    $stmt->execute([$projectId, $funnelKey]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}

function findArchivedFunnelRow(string $projectId, string $funnelKey): ?array {
    $stmt = getDb()->prepare(
        'SELECT funnel_key, revision FROM funnels WHERE project_id = ? AND funnel_key = ? AND archived_at IS NOT NULL LIMIT 1'
    );
    $stmt->execute([$projectId, $funnelKey]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}

/** Stores only the immutable evaluation semantics; labels/descriptions stay editable metadata. */
function insertFunnelRevisionSnapshot(string $projectId, array $definition, int $revision): void {
    getDb()->prepare(
        'INSERT INTO funnel_definition_revisions (project_id,funnel_key,revision,steps,window_seconds,count_mode,identity_scope,correlation_property,created_at) VALUES (?,?,?,?,?,?,?,?,?)'
    )->execute([
        $projectId, $definition['funnel_key'], $revision,
        json_encode(funnelStepsForWire($definition['steps']), JSON_UNESCAPED_UNICODE),
        (int)$definition['window_seconds'], $definition['count_mode'], $definition['identity_scope'],
        $definition['correlation_property'], now(),
    ]);
}

/** Stable semantic comparison: ordered steps, but property-map insertion order is irrelevant. */
function funnelSemanticSteps(array $steps): string {
    $normalized = [];
    foreach (funnelStepsForWire($steps) as $step) {
        $props = is_array($step['props'] ?? null) ? $step['props'] : [];
        ksort($props);
        $normalized[] = [
            'key' => $step['key'] ?? '', 'event' => $step['event'] ?? '',
            'name' => $step['name'] ?? null, 'screen' => $step['screen'] ?? null, 'props' => $props,
        ];
    }
    return json_encode($normalized, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
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
        'revision' => (int)($row['revision'] ?? 0),
        'created_at' => $row['created_at'],
        'updated_at' => $row['updated_at'],
    ];
}

/** Historical results use immutable step labels/window/counting semantics. */
function findFunnelRevision(string $projectId, string $key, int $revision): ?array {
    $current = findFunnelRow($projectId, $key);
    if ($current === null) return null;
    $stmt = getDb()->prepare('SELECT revision,steps,window_seconds,count_mode,identity_scope,correlation_property FROM funnel_definition_revisions WHERE project_id=? AND funnel_key=? AND revision=?');
    $stmt->execute([$projectId, $key, $revision]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    return $row ? array_merge($current, $row) : ((int)$current['revision'] === $revision ? $current : null);
}
