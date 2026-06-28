<?php

/**
 * Validate the Bearer JWT on the Authorization header. On failure responds 401
 * and exits. On success returns the decoded payload.
 *
 * IONOS (and most shared hosts) drop Authorization on FastCGI; the .htaccess
 * SetEnvIf line restores it as HTTP_AUTHORIZATION. We also fall back to
 * REDIRECT_HTTP_AUTHORIZATION and getallheaders() so the dashboard works even
 * if one of those layers is misconfigured.
 */
function requireSession(): array {
    $header = $_SERVER['HTTP_AUTHORIZATION']
           ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION']
           ?? '';
    if ($header === '' && function_exists('getallheaders')) {
        $all = getallheaders();
        $header = $all['Authorization'] ?? $all['authorization'] ?? '';
    }
    if (strpos($header, 'Bearer ') !== 0) {
        errorResponse(401, 'unauthorized', 'Missing or invalid Authorization header');
        exit;
    }
    $payload = jwt_decode(substr($header, 7), JWT_SECRET);
    if ($payload === null) {
        errorResponse(401, 'unauthorized', 'Invalid or expired token');
        exit;
    }
    return $payload;
}

/**
 * Validate an X-QM-Api-Key header against the projects table. On failure
 * responds 401 and exits. On success returns the project_id string.
 *
 * Comparison is a single indexed lookup against the SHA-256 hash of the key.
 * Tokens are 256-bit random so this is collision-resistant; no bcrypt needed
 * on the ingest hot path.
 */
function requireApiKey(): string {
    $apiKey = trim($_SERVER['HTTP_X_QM_API_KEY'] ?? '');
    if ($apiKey === '') {
        errorResponse(401, 'unauthorized', 'Missing X-QM-Api-Key header');
        exit;
    }
    $stmt = getDb()->prepare('SELECT id FROM projects WHERE api_key_hash = ? LIMIT 1');
    $stmt->execute([tokenHash($apiKey)]);
    $row = $stmt->fetch();
    if ($row === false) {
        errorResponse(401, 'unauthorized', 'Invalid API key');
        exit;
    }
    return $row['id'];
}

/**
 * Asserts the session user can read the given project. Owner or member both
 * pass; admins pass for any project. Responds 403 + exits otherwise.
 */
function requireProjectAccess(array $session, string $projectId): void {
    if (($session['role'] ?? '') === 'admin') {
        return;
    }
    $userId = $session['sub'] ?? '';
    $stmt = getDb()->prepare(
        'SELECT 1 FROM projects WHERE id = ? AND owner_user_id = ?
         UNION
         SELECT 1 FROM project_members WHERE project_id = ? AND user_id = ?
         LIMIT 1'
    );
    $stmt->execute([$projectId, $userId, $projectId, $userId]);
    if ($stmt->fetchColumn() === false) {
        errorResponse(403, 'forbidden', 'No access to this project');
        exit;
    }
}

/**
 * Asserts the session user's global role is one of $allowed. Responds 403 +
 * exits otherwise. Roles: admin | developer | reviewer.
 */
function requireRole(array $session, array $allowed): void {
    if (!in_array($session['role'] ?? '', $allowed, true)) {
        errorResponse(403, 'forbidden', 'Insufficient role for this action');
        exit;
    }
}

/** Convenience: assert the session user is an admin. */
function requireAdmin(array $session): void {
    requireRole($session, ['admin']);
}
