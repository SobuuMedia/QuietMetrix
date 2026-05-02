<?php

/** GET /api/v1/projects → list projects the caller owns or is a member of. */
function handleProjectsList(): void {
    $session = requireSession();
    $userId  = $session['sub'];

    $stmt = getDb()->prepare(
        'SELECT id, name, plan_id, created_at FROM projects WHERE owner_user_id = ?
         UNION
         SELECT p.id, p.name, p.plan_id, p.created_at FROM projects p
           INNER JOIN project_members m ON m.project_id = p.id
           WHERE m.user_id = ?
         ORDER BY created_at DESC'
    );
    $stmt->execute([$userId, $userId]);
    jsonResponse(200, ['projects' => $stmt->fetchAll()]);
}

/**
 * POST /api/v1/projects { name } → creates a project and returns the freshly
 * generated API key. The key is shown ONLY at this response — the server
 * stores hashes only and cannot recover it later.
 */
function handleProjectsCreate(): void {
    $session = requireSession();
    $body    = getJsonBody();
    $name    = trim((string)($body['name'] ?? ''));
    if ($name === '' || strlen($name) > 255) {
        errorResponse(400, 'invalid_request', 'name is required (1–255 chars)');
        return;
    }

    $id      = uuid4();
    $apiKey  = randomToken();

    getDb()->prepare(
        'INSERT INTO projects (id, name, owner_user_id, api_key_hash, plan_id, created_at)
         VALUES (?, ?, ?, ?, NULL, ?)'
    )->execute([$id, $name, $session['sub'], tokenHash($apiKey), now()]);

    jsonResponse(201, [
        'id'       => $id,
        'name'     => $name,
        'api_key'  => $apiKey,    // returned once, in plaintext — store it now
    ]);
}

/** DELETE /api/v1/projects/{id} → owner only. */
function handleProjectsDelete(string $projectId): void {
    $session = requireSession();
    $stmt = getDb()->prepare('SELECT owner_user_id FROM projects WHERE id = ?');
    $stmt->execute([$projectId]);
    $row = $stmt->fetch();
    if ($row === false) {
        errorResponse(404, 'not_found', 'Project not found');
        return;
    }
    if ($row['owner_user_id'] !== $session['sub']) {
        errorResponse(403, 'forbidden', 'Only the owner can delete a project');
        return;
    }
    getDb()->prepare('DELETE FROM projects WHERE id = ?')->execute([$projectId]);
    jsonResponse(200, ['ok' => true]);
}
