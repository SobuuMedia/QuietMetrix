<?php

require_once __DIR__ . '/../experimentValidation.php';
require_once __DIR__ . '/../experimentCounterAnalyze.php';

/**
 * A/B testing experiment routes. Mirrors servers/ktor/.../routes/ExperimentRoutes.kt --
 * dashboard CRUD + results (session or an analytics:read-scoped PAT for reads, session
 * admin/developer for writes, same pattern as routes/funnels.php) and the SDK-facing config
 * fetch (X-QM-Api-Key authenticated, like /api/v1/counters and /api/v1/funnels/register).
 *
 * Unlike Ktor (which enforces a free-plan cap only on the CLOUD profile), this backend is
 * exclusively the self-host deployment target, so no plan-based limit is enforced here at
 * all -- `limit` in the list response is always null, matching the project's "self-hosted and
 * unlimited" positioning.
 */

/** GET /api/v1/projects/{id}/experiments → all of the project's experiments, newest first. */
function handleExperimentsList(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $stmt = getDb()->prepare(
        'SELECT experiment_key, name, screen, status, selected_variant, traffic_percent, variants, countries, created_at, started_at, ended_at
           FROM experiments WHERE project_id = ? ORDER BY created_at DESC'
    );
    $stmt->execute([$projectId]);
    jsonResponse(200, [
        'experiments' => array_map('rowToExperimentResponse', $stmt->fetchAll()),
        'limit' => null,
    ]);
}

/**
 * POST /api/v1/projects/{id}/experiments { key, name, screen?, traffic_percent?, variants?,
 * countries? } → creates a `draft` experiment. Admin or developer only.
 */
function handleExperimentsCreate(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);
    requireRole($session, ['admin', 'developer']);

    $body = getJsonBody();
    $definition = [
        'key' => $body['key'] ?? '',
        'name' => $body['name'] ?? '',
        'screen' => isset($body['screen']) ? trim((string)$body['screen']) : null,
        'traffic_percent' => $body['traffic_percent'] ?? 100,
        'variants' => $body['variants'] ?? [['name' => 'a', 'weight' => 50], ['name' => 'b', 'weight' => 50]],
        'countries' => experimentNormalizeCountries($body['countries'] ?? null),
    ];
    if ($definition['screen'] === '') $definition['screen'] = null;

    $errors = experimentValidate($definition);
    if (!empty($errors)) {
        errorResponse(400, 'schema_violation', implode('; ', $errors));
        return;
    }

    $stmt = getDb()->prepare('SELECT 1 FROM experiments WHERE project_id = ? AND experiment_key = ? LIMIT 1');
    $stmt->execute([$projectId, $definition['key']]);
    if ($stmt->fetchColumn() !== false) {
        errorResponse(409, 'experiment_exists', 'An experiment with this key already exists');
        return;
    }

    $id = uuid4();
    $now = now();
    getDb()->prepare(
        'INSERT INTO experiments (id, project_id, experiment_key, name, screen, status, traffic_percent, variants, countries, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    )->execute([
        $id, $projectId, $definition['key'], $definition['name'], $definition['screen'], 'draft', $definition['traffic_percent'],
        json_encode($definition['variants'], JSON_UNESCAPED_UNICODE),
        $definition['countries'] === null ? null : json_encode($definition['countries'], JSON_UNESCAPED_UNICODE),
        $now, $now,
    ]);

    jsonResponse(201, findExperimentResponse($projectId, $definition['key']));
}

/**
 * PATCH /api/v1/projects/{id}/experiments/{key} { name?, screen?, traffic_percent?, status?,
 * variants?, countries? } → partial update, gated by experimentCanTransition() (status) and
 * experimentEditableFields() (everything else). Admin or developer only.
 */
function handleExperimentUpdate(string $projectId, string $experimentKey): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);
    requireRole($session, ['admin', 'developer']);

    $existing = findExperimentRow($projectId, $experimentKey);
    if ($existing === null) {
        errorResponse(404, 'not_found', 'Experiment not found');
        return;
    }

    $body = getJsonBody();

    if (in_array($existing['status'], ['shipped', 'closed'], true)) {
        errorResponse(409, 'not_editable', 'Resolved experiments are immutable');
        return;
    }

    if (array_key_exists('status', $body) && !experimentCanTransition($existing['status'], (string)$body['status'])) {
        errorResponse(409, 'invalid_transition', "Cannot move from {$existing['status']} to {$body['status']}");
        return;
    }

    if (!experimentSelectionValid($existing, $body)) {
        errorResponse(400, 'schema_violation', 'Shipping requires a declared selected_variant; other transitions cannot select one');
        return;
    }
    $editable = experimentEditableFields($existing['status']);
    $requestedEdits = array_values(array_intersect(
        ['name', 'screen', 'traffic_percent', 'variants', 'countries'],
        array_keys($body),
    ));
    $disallowed = array_diff($requestedEdits, $editable);
    if (!empty($disallowed)) {
        errorResponse(409, 'not_editable', 'Cannot edit ' . implode(', ', $disallowed) . " while {$existing['status']}");
        return;
    }

    $mergedCountries = array_key_exists('countries', $body)
        ? experimentNormalizeCountries($body['countries'])
        : (json_decode($existing['countries'] ?? 'null', true) ?: null);
    $merged = [
        'key' => $experimentKey,
        'name' => array_key_exists('name', $body) ? $body['name'] : $existing['name'],
        'screen' => array_key_exists('screen', $body) ? (trim((string)$body['screen']) ?: null) : $existing['screen'],
        'traffic_percent' => array_key_exists('traffic_percent', $body) ? $body['traffic_percent'] : (int)$existing['traffic_percent'],
        'variants' => array_key_exists('variants', $body) ? $body['variants'] : json_decode($existing['variants'], true),
        'countries' => $mergedCountries,
    ];
    $errors = experimentValidate($merged);
    if (!empty($errors)) {
        errorResponse(400, 'schema_violation', implode('; ', $errors));
        return;
    }

    $sets = ['updated_at = ?'];
    $params = [now()];
    if (array_key_exists('name', $body)) { $sets[] = 'name = ?'; $params[] = $merged['name']; }
    if (array_key_exists('screen', $body)) { $sets[] = 'screen = ?'; $params[] = $merged['screen']; }
    if (array_key_exists('traffic_percent', $body)) { $sets[] = 'traffic_percent = ?'; $params[] = $merged['traffic_percent']; }
    if (array_key_exists('variants', $body)) { $sets[] = 'variants = ?'; $params[] = json_encode($merged['variants'], JSON_UNESCAPED_UNICODE); }
    if (array_key_exists('countries', $body)) {
        $sets[] = 'countries = ?';
        $params[] = $merged['countries'] === null ? null : json_encode($merged['countries'], JSON_UNESCAPED_UNICODE);
    }
    if (isset($body['selected_variant'])) { $sets[] = 'selected_variant = ?'; $params[] = $body['selected_variant']; }
    if (array_key_exists('status', $body)) {
        $sets[] = 'status = ?';
        $params[] = $body['status'];
        if ($body['status'] === 'active' && $existing['started_at'] === null) { $sets[] = 'started_at = ?'; $params[] = now(); }
        if (in_array($body['status'], ['ended', 'shipped', 'closed'], true) && $existing['ended_at'] === null) { $sets[] = 'ended_at = ?'; $params[] = now(); }
    }
    $params[] = $projectId;
    $params[] = $experimentKey;

    $params[] = $existing['status'];
    $update = getDb()->prepare('UPDATE experiments SET ' . implode(', ', $sets) . ' WHERE project_id = ? AND experiment_key = ? AND status = ?');
    $update->execute($params);
    if ($update->rowCount() === 0) {
        // MySQL can report zero changed rows for a legitimate same-value PATCH.
        $current = findExperimentRow($projectId, $experimentKey);
        if ($current === null || $current['status'] !== $existing['status']) {
            errorResponse(409, 'invalid_transition', 'Experiment changed; refresh before retrying');
            return;
        }
    }

    jsonResponse(200, findExperimentResponse($projectId, $experimentKey));
}

/** DELETE /api/v1/projects/{id}/experiments/{key} → only a `draft` experiment may be deleted. */
function handleExperimentDelete(string $projectId, string $experimentKey): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);
    requireRole($session, ['admin', 'developer']);

    $existing = findExperimentRow($projectId, $experimentKey);
    if ($existing === null) {
        errorResponse(404, 'not_found', 'Experiment not found');
        return;
    }
    if ($existing['status'] !== 'draft') {
        errorResponse(409, 'not_editable', 'Only a draft experiment can be deleted; end it instead');
        return;
    }

    getDb()->prepare('DELETE FROM experiments WHERE project_id = ? AND experiment_key = ?')
        ->execute([$projectId, $experimentKey]);
    jsonResponse(200, ['deleted' => true]);
}

/**
 * GET /api/v1/projects/{id}/experiments/{key}/results ?range=7d → per-variant impressions/
 * interactions/rate plus a two-proportion significance comparison. Mirrors
 * servers/ktor/.../routes/ExperimentRoutes.kt's results handler.
 */
function handleExperimentResults(string $projectId, string $experimentKey): void {
    requireAnalyticsAccess($projectId);

    $existing = findExperimentRow($projectId, $experimentKey);
    if ($existing === null) {
        errorResponse(404, 'not_found', 'Experiment not found');
        return;
    }

    $seconds = windowSeconds(365);
    $now = time();
    $fromIso = gmdate('Y-m-d\TH:i:s\Z', $now - $seconds);
    $toIso = gmdate('Y-m-d\TH:i:s\Z', $now);
    $fromDay = gmdate('Y-m-d', $now - $seconds);
    $toDay = gmdate('Y-m-d', $now);

    $cells = counterReadCells($projectId, 'experiment', $fromDay, $toDay);
    $experimentForAnalysis = [
        'experiment_key' => $existing['experiment_key'],
        'variants' => json_decode($existing['variants'], true),
    ];
    $analyzed = experimentCounterAnalyze($experimentForAnalysis, $cells);

    jsonResponse(200, [
        'experiment' => rowToExperimentResponse($existing),
        'range' => ['from' => $fromIso, 'to' => $toIso],
        'unit' => 'sessions',
        'variants' => $analyzed['variants'],
        'comparison' => $analyzed['comparison'],
    ]);
}

/**
 * GET /api/v1/experiments/config → SDK-facing, X-QM-Api-Key authenticated (project derived
 * from the key, like /api/v1/counters). Returns `active` tests and `shipped` rollouts, minimal fields --
 * no name, screen or timestamp ever leaves the server.
 */
function handleExperimentsConfig(): void {
    checkOrigin();
    $projectId = requireApiKey();

    $ip = clientIp() ?? 'unknown';
    if (defined('INGEST_IP_ENABLED') && INGEST_IP_ENABLED && $ip !== 'unknown') {
        $ipRps = defined('INGEST_IP_RPS') ? (int)INGEST_IP_RPS : 5;
        $ipBurst = defined('INGEST_IP_BURST') ? (int)INGEST_IP_BURST : 60;
        enforceLimit("experiments:config:ip:{$ip}", $ipRps, $ipBurst);
    }

    $stmt = getDb()->prepare(
        "SELECT experiment_key, status, selected_variant, traffic_percent, variants, countries
           FROM experiments WHERE project_id = ? AND status IN ('active', 'shipped')"
    );
    $stmt->execute([$projectId]);

    header('Cache-Control: public, max-age=300');
    jsonResponse(200, ['experiments' => array_map('experimentConfigResponse', $stmt->fetchAll())]);
}

/** Whether $from -> $to is an allowed status transition. Mirrors ExperimentStatus.kt exactly. */
function experimentCanTransition(string $from, string $to): bool {
    switch ($from) {
        case 'draft': return $to === 'active' || $to === 'ended';
        case 'active': return in_array($to, ['paused', 'ended', 'shipped', 'closed'], true);
        case 'paused': return in_array($to, ['active', 'ended', 'shipped', 'closed'], true);
        case 'ended': return in_array($to, ['shipped', 'closed'], true);
        default: return false;
    }
}

/** Which non-status fields may be edited while an experiment is in $status. Mirrors ExperimentStatus.kt. */
function experimentEditableFields(string $status): array {
    switch ($status) {
        case 'draft': return ['name', 'screen', 'traffic_percent', 'variants', 'countries'];
        case 'active':
        case 'paused': return ['name', 'screen', 'traffic_percent'];
        default: return [];
    }
}

function findExperimentRow(string $projectId, string $experimentKey): ?array {
    $stmt = getDb()->prepare(
        'SELECT experiment_key, name, screen, status, selected_variant, traffic_percent, variants, countries, created_at, started_at, ended_at
           FROM experiments WHERE project_id = ? AND experiment_key = ? LIMIT 1'
    );
    $stmt->execute([$projectId, $experimentKey]);
    $row = $stmt->fetch();
    return $row === false ? null : $row;
}

function findExperimentResponse(string $projectId, string $experimentKey): ?array {
    $row = findExperimentRow($projectId, $experimentKey);
    return $row === null ? null : rowToExperimentResponse($row);
}

function rowToExperimentResponse(array $row): array {
    return [
        'key' => $row['experiment_key'],
        'name' => $row['name'],
        'screen' => $row['screen'],
        'status' => $row['status'],
        'selected_variant' => $row['selected_variant'] ?? null,
        'traffic_percent' => (int)$row['traffic_percent'],
        'variants' => json_decode($row['variants'], true),
        'countries' => $row['countries'] !== null ? json_decode($row['countries'], true) : null,
        'created_at' => $row['created_at'],
        'started_at' => $row['started_at'],
        'ended_at' => $row['ended_at'],
    ];
}

/** Validates a rollout choice against the stored declared variants. */
function experimentSelectionValid(array $existing, array $body): bool {
    if (($body['status'] ?? null) !== 'shipped') return !isset($body['selected_variant']);
    return in_array($body['selected_variant'] ?? null, array_column(json_decode($existing['variants'], true), 'name'), true);
}

/** Projects rollout config without rewriting historical targeting or variant weights. */
function experimentConfigResponse(array $row): array {
    $shipped = $row['status'] === 'shipped';
    $variants = json_decode($row['variants'], true);
    if ($shipped) {
        foreach ($variants as &$variant) $variant['weight'] = $variant['name'] === $row['selected_variant'] ? 100 : 0;
        unset($variant);
    }
    return [
        'key' => $row['experiment_key'],
        'traffic_percent' => $shipped ? 100 : (int)$row['traffic_percent'],
        'variants' => $variants,
        'countries' => $shipped || $row['countries'] === null ? null : json_decode($row['countries'], true),
    ];
}
