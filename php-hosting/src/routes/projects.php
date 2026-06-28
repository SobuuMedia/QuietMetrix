<?php

/**
 * GET /api/v1/projects → list projects the caller can see.
 * Admins see every project; developers and reviewers see only the projects they
 * own or are assigned to (project_members).
 */
function handleProjectsList(): void {
    $session = requireSession();
    $userId  = $session['sub'];

    if (($session['role'] ?? '') === 'admin') {
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
 */
function handleProjectsCreate(): void {
    $session = requireSession();
    requireAdmin($session);   // only admins may create apps
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

    $id      = uuid4();
    $apiKey  = randomToken();

    getDb()->prepare(
        'INSERT INTO projects (id, name, description, owner_user_id, api_key_hash, api_key_last4, plan_id, created_at)
         VALUES (?, ?, ?, ?, ?, ?, NULL, ?)'
    )->execute([$id, $name, $description, $session['sub'], tokenHash($apiKey), substr($apiKey, -4), now()]);

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
