<?php

/**
 * GET /api/v1/projects → list projects the caller can see.
 * Admins see every project; developers and reviewers see only the projects they
 * own or are assigned to (project_members). Also accepts a personal access token
 * with a projects:create or projects:read scope — see docs/agents/setup.md.
 */
function handleProjectsList(): void {
    $session = requireSessionOrToken();
    if (!sessionCanReadProjects($session)) {
        errorResponse(403, 'forbidden', 'This token cannot list projects');
        return;
    }
    $userId = $session['sub'];

    if (sessionIsAdmin($session)) {
        $stmt = getDb()->prepare(
            'SELECT id, name, description, icon_key, declared_platforms, plan_id, api_key_last4, created_at, CAST(owner_user_id AS CHAR) AS owner_user_id
               FROM projects' . (cloudMode() && !$session['platformOperator'] ? ' WHERE owner_user_id IN (SELECT id FROM users WHERE tenant_id=?)' : '') . ' ORDER BY created_at DESC'
        );
        $stmt->execute(cloudMode() && !$session['platformOperator'] ? [$session['tenantId']] : []);
    } else {
        $stmt = getDb()->prepare(
            'SELECT id, name, description, icon_key, declared_platforms, plan_id, api_key_last4, created_at, CAST(owner_user_id AS CHAR) AS owner_user_id FROM projects WHERE owner_user_id = ?
             UNION
             SELECT p.id, p.name, p.description, p.icon_key, p.declared_platforms, p.plan_id, p.api_key_last4, p.created_at, CAST(p.owner_user_id AS CHAR) AS owner_user_id FROM projects p
               INNER JOIN project_members m ON m.project_id = p.id
               WHERE m.user_id = ?
             ORDER BY created_at DESC'
        );
        $stmt->execute([$userId, $userId]);
    }
    $projects = $stmt->fetchAll();
    foreach ($projects as &$project) {
        $project['declared_platforms'] = is_array($project['declared_platforms'] ?? null)
            ? $project['declared_platforms']
            : (json_decode((string)($project['declared_platforms'] ?? '[]'), true) ?: []);
    }
    unset($project);
    attachProjectActivitySummaries($projects);
    jsonResponse(200, ['projects' => $projects]);
}

/** Attach truthful ingest metadata without exposing platform/SDK coverage below k=5. */
function attachProjectActivitySummaries(array &$projects): void {
    if ($projects === []) return;
    $ids = array_values(array_filter(array_map(static fn($p) => (string)($p['id'] ?? ''), $projects), static fn($id) => $id !== ''));
    if ($ids === []) return;
    $marks = implode(',', array_fill(0, count($ids), '?'));
    $byId = [];
    foreach ($ids as $id) $byId[$id] = ['last' => null, 'coverage' => 0, 'legacy_coverage' => 0, 'platforms' => [], 'sdks' => []];
    try {
        $q = getDb()->prepare("SELECT project_id, MAX(updated_at) AS last_received_at FROM counter_cells_v2 WHERE project_id IN ($marks) GROUP BY project_id");
        $q->execute($ids);
        foreach ($q->fetchAll() as $row) if (isset($byId[(string)$row['project_id']])) {
            $key = (string)$row['project_id']; $byId[$key]['last'] = $row['last_received_at'];
        }
        $q = getDb()->prepare("SELECT project_id,dims,contributors FROM counter_cells_v2 WHERE metric='unique_window_v2' AND hour=-1 AND segment_kind='global' AND segment_value='all' AND project_id IN ($marks)");
        $q->execute($ids);
        $windows = [];
        foreach ($q->fetchAll() as $row) {
            $dims = json_decode((string)$row['dims'], true);
            if (!is_array($dims) || ($dims['scope_kind'] ?? null) !== 'project' || ($dims['scope_key'] ?? null) !== 'all' || !isset($dims['days'], $dims['end_day'])) continue;
            $key = (string)$row['project_id'];
            $window = implode('|', [(string)$dims['scope_kind'], (string)$dims['scope_key'], (string)$dims['days'], (string)$dims['end_day']]);
            $windows[$key][$window] = ($windows[$key][$window] ?? 0) + (int)$row['contributors'];
        }
        foreach ($windows as $key => $groups) $byId[$key]['coverage'] = max($groups);
        $q = getDb()->prepare("SELECT project_id, platform, sdk_version FROM counter_cells_v2 WHERE project_id IN ($marks) GROUP BY project_id, platform, sdk_version");
        $q->execute($ids);
        foreach ($q->fetchAll() as $row) if (isset($byId[(string)$row['project_id']])) {
            $key = (string)$row['project_id'];
            if (in_array((string)$row['platform'], ['android', 'ios', 'web', 'desktop'], true)) $byId[$key]['platforms'][] = (string)$row['platform'];
            if ($row['sdk_version'] !== null && $row['sdk_version'] !== 'unknown') $byId[$key]['sdks'][] = (string)$row['sdk_version'];
        }
    } catch (PDOException $ignored) {
        // Older PHP installations may not have run the additive v2 schema bootstrap yet.
    }
    try {
        $q = getDb()->prepare("SELECT project_id, MAX(updated_at) AS last_received_at, MAX(devices) AS coverage FROM counters WHERE project_id IN ($marks) GROUP BY project_id");
        $q->execute($ids);
        foreach ($q->fetchAll() as $row) if (isset($byId[(string)$row['project_id']])) {
            $key = (string)$row['project_id']; $byId[$key]['legacy_coverage'] = (int)$row['coverage'];
            if ($byId[$key]['last'] === null || strcmp((string)$row['last_received_at'], (string)$byId[$key]['last']) > 0) $byId[$key]['last'] = $row['last_received_at'];
        }
        $q = getDb()->prepare("SELECT project_id, platform, MAX(devices) AS coverage FROM counters WHERE project_id IN ($marks) GROUP BY project_id, platform");
        $q->execute($ids);
        foreach ($q->fetchAll() as $row) if ((int)$row['coverage'] >= 5 && in_array((string)$row['platform'], ['android', 'ios', 'web', 'desktop'], true) && isset($byId[(string)$row['project_id']])) {
            $byId[(string)$row['project_id']]['platforms'][] = (string)$row['platform'];
        }
    } catch (PDOException $ignored) {
        // Legacy counters are optional on a newly bootstrapped host.
    }
    $now = new DateTimeImmutable('now', new DateTimeZone('UTC'));
    foreach ($projects as &$project) {
        $key = (string)$project['id']; $summary = $byId[$key] ?? null;
        $last = $summary['last'] ?? null;
        $timestamp = is_string($last) && $last !== '' ? strtotime($last) : false;
        $project['last_received_at'] = $last;
        $reportable = $summary !== null && max($summary['coverage'], $summary['legacy_coverage']) >= 5;
        $project['platforms'] = $reportable ? array_values(array_unique($summary['platforms'])) : [];
        sort($project['platforms']);
        $project['sdk_versions'] = $reportable ? array_values(array_unique($summary['sdks'])) : [];
        sort($project['sdk_versions']);
        $project['activity_status'] = $timestamp === false ? 'waiting_for_first_data'
            : ($timestamp >= $now->getTimestamp() - 86400 ? 'receiving_data' : 'no_recent_data');
    }
    unset($project);
}

/**
 * POST /api/v1/projects { name, description? } → creates a project and returns the freshly
 * generated API key. The key is shown ONLY at this response — the server
 * stores hashes only and cannot recover it later.
 *
 * Accepts either an admin dashboard session or a personal access token carrying the
 * projects:create scope, so an agent/CLI can provision a project without a user's
 * password — see docs/agents/setup.md.
 */
function handleProjectsCreate(): void {
    $session = requireSessionOrToken();
    if (!sessionCanCreateProjects($session)) {
        errorResponse(403, 'forbidden', 'Only admins can create projects');
        return;
    }
    $body    = getJsonBody();
    $name    = trim((string)($body['name'] ?? ''));
    if ($name === '' || strlen($name) > 255) {
        errorResponse(400, 'invalid_request', 'name is required (1–255 chars)');
        return;
    }
    $description = isset($body['description']) ? trim((string)$body['description']) : null;
    if ($description !== null && strlen($description) > 1000) {
        errorResponse(400, 'invalid_request', 'description must be at most 1000 chars');
        return;
    }
    if ($description === '') {
        $description = null;
    }
    $iconKey = (string)($body['icon_key'] ?? 'book');
    $allowedIcons = ['book', 'shopping', 'music', 'fitness', 'travel', 'other'];
    $allowedPlatforms = ['android', 'ios', 'web', 'desktop'];
    $declaredPlatforms = $body['declared_platforms'] ?? [];
    if (!in_array($iconKey, $allowedIcons, true) || !is_array($declaredPlatforms) || count($declaredPlatforms) > 4 ||
        array_filter($declaredPlatforms, static fn($platform) => !is_string($platform) || !in_array($platform, $allowedPlatforms, true)) !== [] ||
        count(array_unique($declaredPlatforms)) !== count($declaredPlatforms)) {
        errorResponse(400, 'schema_violation', 'Invalid project icon or declared platform list');
        return;
    }

    // A retried request (e.g. an agent that never saw the first response) must not mint a
    // second project/key. The plaintext key is never stored, so a replay cannot re-show it —
    // it returns the earlier project's public info instead.
    $idempotencyKey = trim((string)($_SERVER['HTTP_IDEMPOTENCY_KEY'] ?? ''));
    if ($idempotencyKey !== '') {
        $stmt = getDb()->prepare(
            'SELECT id, name, api_key_last4 FROM projects
             WHERE owner_user_id = ? AND idempotency_key = ? LIMIT 1'
        );
        $stmt->execute([$session['sub'], $idempotencyKey]);
        $existing = $stmt->fetch();
        if ($existing !== false) {
            jsonResponse(200, [
                'id'            => $existing['id'],
                'name'          => $existing['name'],
                'api_key_last4' => $existing['api_key_last4'],
                'message'       => 'A project already exists for this Idempotency-Key. Its API key was shown once, at creation, and cannot be retrieved again — use POST /projects/{id}/regenerate-key for a new one.',
            ]);
            return;
        }
    }

    $id      = uuid4();
    $apiKey  = randomToken();

    $db=getDb(); $db->beginTransaction();
    try {
        if (cloudMode()) {
            $q=$db->prepare('SELECT id FROM users WHERE tenant_id=? ORDER BY id LIMIT 1 FOR UPDATE'); $q->execute([$session['tenantId']]); $q->fetch();
            $q=$db->prepare('SELECT COUNT(*) FROM projects p JOIN users u ON p.owner_user_id=u.id WHERE u.tenant_id=?'); $q->execute([$session['tenantId']]);
            $limit=['hobby'=>3,'startup'=>10,'business'=>50][$session['planId'] ?? 'free'] ?? 1;
            if ((int)$q->fetchColumn() >= $limit) { $db->rollBack(); errorResponse(403,'project_limit_reached','Organization project limit reached'); return; }
        }
    getDb()->prepare(
        'INSERT INTO projects (id, name, description, icon_key, declared_platforms, owner_user_id, api_key_hash, api_key_last4, plan_id, idempotency_key, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    )->execute([$id, $name, $description, $iconKey, json_encode(array_values($declaredPlatforms), JSON_THROW_ON_ERROR), $session['sub'], tokenHash($apiKey), substr($apiKey, -4), $session['planId'] ?? null, $idempotencyKey !== '' ? $idempotencyKey : null, now()]);
    $db->commit();
    } catch (Throwable $e) { $db->rollBack(); throw $e; }

    jsonResponse(201, [
        'id'            => $id,
        'name'          => $name,
        'description'   => $description,
        'api_key'       => $apiKey,    // returned once, in plaintext — store it now
        'api_key_last4' => substr($apiKey, -4),
    ]);
}

/**
 * POST /api/v1/projects/{id}/regenerate-key → issues a fresh API key for the
 * project, invalidating the old one. Admins and developers with access may do
 * this; reviewers may not. The new key is returned once, in plaintext.
 */
function handleProjectRegenerateKey(string $projectId): void {
    $session = requireSession();
    requireRole($session, ['admin', 'developer']);
    requireProjectAccess($session, $projectId);

    $stmt = getDb()->prepare('SELECT id FROM projects WHERE id = ? LIMIT 1');
    $stmt->execute([$projectId]);
    if ($stmt->fetch() === false) {
        errorResponse(404, 'not_found', 'Project not found');
        return;
    }

    $apiKey = randomToken();
    getDb()->prepare(
        'UPDATE projects SET api_key_hash = ?, api_key_last4 = ? WHERE id = ?'
    )->execute([tokenHash($apiKey), substr($apiKey, -4), $projectId]);

    jsonResponse(200, [
        'api_key'       => $apiKey,    // returned once, in plaintext
        'api_key_last4' => substr($apiKey, -4),
    ]);
}

/** GET /api/v1/projects/{id}/members → list assigned users (admin or has access). */
function handleProjectMembersList(string $projectId): void {
    $session = requireSession();
    requireProjectAccess($session, $projectId);

    $stmt = getDb()->prepare(
        'SELECT u.id AS user_id, u.email, u.role AS user_role, m.role AS member_role
           FROM project_members m INNER JOIN users u ON u.id = m.user_id
          WHERE m.project_id = ? ORDER BY u.email'
    );
    $stmt->execute([$projectId]);
    jsonResponse(200, ['members' => $stmt->fetchAll()]);
}

/** POST /api/v1/projects/{id}/members { email | user_id } → assign a user (admin only). */
function handleProjectMemberAdd(string $projectId): void {
    $session = requireSession();
    requireAdmin($session);

    $stmt = getDb()->prepare('SELECT id FROM projects WHERE id = ? LIMIT 1');
    $stmt->execute([$projectId]);
    if ($stmt->fetch() === false) {
        errorResponse(404, 'not_found', 'Project not found');
        return;
    }

    $body  = getJsonBody();
    $email = strtolower(trim((string)($body['email'] ?? '')));
    $userId = (string)($body['user_id'] ?? '');
    if ($userId === '' && $email !== '') {
        $u = getDb()->prepare('SELECT id FROM users WHERE email = ? LIMIT 1');
        $u->execute([$email]);
        $row = $u->fetch();
        $userId = $row === false ? '' : $row['id'];
    }
    if ($userId === '') {
        errorResponse(400, 'invalid_request', 'A valid email or user_id is required');
        return;
    }

    if (cloudMode()) {
        $q = getDb()->prepare('SELECT 1 FROM projects p JOIN users owner ON owner.id=p.owner_user_id JOIN users member ON member.id=? WHERE p.id=? AND owner.tenant_id=member.tenant_id');
        $q->execute([$userId,$projectId]);
        if ($q->fetchColumn() === false) { errorResponse(404, 'not_found', 'User not found in organization'); return; }
    }

    // Idempotent assign (ignore if already a member).
    getDb()->prepare(
        'INSERT IGNORE INTO project_members (project_id, user_id, role, added_at) VALUES (?, ?, ?, ?)'
    )->execute([$projectId, $userId, 'viewer', now()]);
    jsonResponse(201, ['ok' => true, 'user_id' => $userId]);
}

/** DELETE /api/v1/projects/{id}/members/{userId} → unassign a user (admin only). */
function handleProjectMemberRemove(string $projectId, string $userId): void {
    $session = requireSession();
    requireAdmin($session);

    getDb()->prepare('DELETE FROM project_members WHERE project_id = ? AND user_id = ?')
        ->execute([$projectId, $userId]);
    jsonResponse(200, ['ok' => true]);
}

/** DELETE /api/v1/projects/{id} → admin only. */
/** PATCH /api/v1/projects/{id} { name } updates a project label for admins. */
function handleProjectsUpdate(string $projectId): void {
    $session = requireSessionOrToken();
    if (!sessionIsAdmin($session)) {
        errorResponse(403, 'forbidden', 'Only admins can update projects');
        return;
    }
    if (!preg_match('/^(?:proj_)?[1-9][0-9]*$/', $projectId)) {
        errorResponse(400, 'invalid_request', 'Invalid project id');
        return;
    }
    $id = (int)str_replace('proj_', '', $projectId);
    $body = getJsonBody();
    $name = trim((string)($body['name'] ?? ''));
    if ($name === '' || strlen($name) > 255) {
        errorResponse(400, 'invalid_request', 'name is required (1–255 chars)');
        return;
    }
    $db = getDb();
    $stmt = $db->prepare('UPDATE projects SET name = ? WHERE id = ? AND deleted_at IS NULL');
    $stmt->execute([$name, $id]);
    $read = $db->prepare('SELECT id, name FROM projects WHERE id = ? AND deleted_at IS NULL');
    $read->execute([$id]);
    $project = $read->fetch();
    if (!$project) {
        errorResponse(404, 'not_found', 'Project not found');
        return;
    }
    jsonResponse(200, ['ok' => true, 'project_id' => (string)$project['id'], 'name' => $project['name']]);
}

function handleProjectsDelete(string $projectId): void {
    $session = requireSession();
    requireAdmin($session);
    $stmt = getDb()->prepare('SELECT id FROM projects WHERE id = ?');
    $stmt->execute([$projectId]);
    if ($stmt->fetch() === false) {
        errorResponse(404, 'not_found', 'Project not found');
        return;
    }
    $db = getDb();
    $db->beginTransaction();
    try {
        $lock=$db->prepare('SELECT id FROM projects WHERE id=? FOR UPDATE'); $lock->execute([$projectId]); $lock->fetch();
        $db->prepare('DELETE FROM ai_summary_cache WHERE operation_id IN (SELECT operation_id FROM ai_operations WHERE project_id=?)')->execute([$projectId]);
        $db->prepare('DELETE FROM ai_operations WHERE project_id=?')->execute([$projectId]);
        $db->prepare('DELETE FROM projects WHERE id = ?')->execute([$projectId]);
        $db->commit();
    } catch (Throwable $e) { $db->rollBack(); throw $e; }
    jsonResponse(200, ['ok' => true]);
}
