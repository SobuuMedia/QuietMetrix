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
            'SELECT id, name, description, plan_id, api_key_last4, created_at
               FROM projects ORDER BY created_at DESC'
        );
        $stmt->execute();
    } else {
        $stmt = getDb()->prepare(
            'SELECT id, name, description, plan_id, api_key_last4, created_at FROM projects WHERE owner_user_id = ?
             UNION
             SELECT p.id, p.name, p.description, p.plan_id, p.api_key_last4, p.created_at FROM projects p
               INNER JOIN project_members m ON m.project_id = p.id
               WHERE m.user_id = ?
             ORDER BY created_at DESC'
        );
        $stmt->execute([$userId, $userId]);
    }
    jsonResponse(200, ['projects' => $stmt->fetchAll()]);
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
    $installSalt = bin2hex(random_bytes(32));

    getDb()->prepare(
        'INSERT INTO projects (id, name, description, owner_user_id, api_key_hash, api_key_last4, install_salt, plan_id, idempotency_key, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)'
    )->execute([$id, $name, $description, $session['sub'], tokenHash($apiKey), substr($apiKey, -4), $installSalt, $idempotencyKey !== '' ? $idempotencyKey : null, now()]);

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
    $installSalt = bin2hex(random_bytes(32));
    getDb()->prepare(
        'UPDATE projects SET api_key_hash = ?, api_key_last4 = ?, install_salt = ? WHERE id = ?'
    )->execute([tokenHash($apiKey), substr($apiKey, -4), $installSalt, $projectId]);

    // Discard old install rows — their hashes cannot be recomputed against the new salt,
    // and key rotation is an abuse-response action. See docs/security/publishable-api-key.md.
    getDb()->prepare('DELETE FROM install_meta WHERE project_id = ?')->execute([$projectId]);

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
function handleProjectsDelete(string $projectId): void {
    $session = requireSession();
    requireAdmin($session);
    $stmt = getDb()->prepare('SELECT id FROM projects WHERE id = ?');
    $stmt->execute([$projectId]);
    if ($stmt->fetch() === false) {
        errorResponse(404, 'not_found', 'Project not found');
        return;
    }
    getDb()->prepare('DELETE FROM projects WHERE id = ?')->execute([$projectId]);
    jsonResponse(200, ['ok' => true]);
}
