<?php
require_once __DIR__ . '/errorDiagnostics.php';

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
    if ($payload === null || !validAccessClaims($payload)) {
        errorResponse(401, 'unauthorized', 'Invalid or expired token');
        exit;
    }
    return validateCurrentSession($payload);
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

/**
 * Like requireSession(), but also accepts a personal access token
 * (`Authorization: Bearer qm_pat_…`) so an agent/CLI can call the API without a
 * user's password — see docs/agents/setup.md. On failure responds 401 and exits.
 *
 * Returns a session-shaped array either way: `['sub' => userId, 'role' => role, ...]`
 * for a dashboard JWT, or `['sub' => userId, 'scopes' => [...]]` (no 'role' key) for a
 * PAT. Use sessionIsAdmin()/sessionCanCreateProjects()/sessionCanReadProjects() rather
 * than reading 'role' directly, since a PAT session has none.
 */
function requireSessionOrToken(): array {
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
    $credential = substr($header, 7);

    if (str_starts_with($credential, 'qm_pat_')) {
        $validated = validateAccessToken($credential);
        if ($validated === null) {
            errorResponse(401, 'unauthorized', 'Invalid or expired token');
            exit;
        }
        return attachTenantScope(['sub' => $validated['userId'], 'scopes' => $validated['scopes']]);
    }

    $payload = jwt_decode($credential, JWT_SECRET);
    if ($payload === null || !validAccessClaims($payload)) {
        errorResponse(401, 'unauthorized', 'Invalid or expired token');
        exit;
    }
    return validateCurrentSession($payload);
}

/** True if [$session] (from requireSessionOrToken()) is an admin dashboard session. A PAT is never admin. */
function sessionIsAdmin(array $session): bool {
    return !isset($session['scopes']) && ($session['role'] ?? '') === 'admin';
}

/** Mirrors the "only admins can create projects" rule, extended to a scoped PAT. */
function sessionCanCreateProjects(array $session): bool {
    if (isset($session['scopes'])) {
        return in_array('projects:create', $session['scopes'], true);
    }
    return ($session['role'] ?? '') === 'admin';
}

/** Listing was previously open to any authenticated session; a PAT needs an explicit scope. */
function sessionCanReadProjects(array $session): bool {
    if (isset($session['scopes'])) {
        return in_array('projects:create', $session['scopes'], true)
            || in_array('projects:read', $session['scopes'], true);
    }
    return true;
}

/** Gates read access to event/funnel analytics — a PAT needs the explicit analytics:read scope. */
function sessionCanReadAnalytics(array $session): bool {
    if (isset($session['scopes'])) {
        return in_array('analytics:read', $session['scopes'], true);
    }
    return true;
}

/**
 * Asserts the caller (dashboard session or PAT) can read analytics for [projectId].
 * Combines the analytics:read scope check with the existing owner/member/admin gate, since
 * requireProjectAccess() already reads sub/role generically — see requireSessionOrToken().
 * Shared by routes/dashboard.php and routes/funnels.php, so it lives here (always loaded)
 * rather than in either route file (only require_once'd on a matching request).
 */
function requireAnalyticsAccess(string $projectId): array {
    $session = requireSessionOrToken();
    if (!sessionCanReadAnalytics($session)) {
        errorResponse(403, 'forbidden', 'This token cannot read analytics');
        exit;
    }
    requireProjectAccess($session, $projectId);
    enableOperatorErrorDiagnostics($session);
    return $session;
}

/** Safe rolling compatibility for existing role-bearing access tokens only. */
function validAccessClaims(array $payload): bool {
    return in_array($payload['type'] ?? 'access', ['access'], true)
        && in_array($payload['role'] ?? '', ['admin', 'developer', 'reviewer'], true)
        && isset($payload['sub']);
}

function validateCurrentSession(array $payload): array {
    $stmt = getDb()->prepare('SELECT role, status FROM users WHERE id = ?');
    $stmt->execute([$payload['sub']]);
    $user = $stmt->fetch();
    $valid = $user && $user['status'] === 'active' && $user['role'] === $payload['role'];
    if (($payload['type'] ?? null) === 'access') {
        $stmt = getDb()->prepare('SELECT 1 FROM auth_sessions WHERE id=? AND user_id=? AND expires_at>?');
        $stmt->execute([$payload['sessionId'] ?? '', $payload['sub'], time()]);
        $valid = $valid && $stmt->fetchColumn() !== false;
    }
    if (!$valid) { errorResponse(401, 'unauthorized', 'Session expired or account changed'); exit; }
    return attachTenantScope($payload);
}

/** Database-owned organization metadata applies to sessions and scoped tokens alike. */
function cloudMode(): bool { return strtolower(getenv('QM_PROFILE') ?: 'selfhost') === 'cloud'; }
function attachTenantScope(array $session): array {
    $q = getDb()->prepare('SELECT tenant_id, platform_operator, status, plan_id, role FROM users WHERE id=?');
    $q->execute([$session['sub']]); $user = $q->fetch();
    if (!$user || $user['status'] !== 'active') { errorResponse(401, 'unauthorized', 'Account unavailable'); exit; }
    $session['tenantId'] = $user['tenant_id'];
    $session['planId'] = $user['plan_id'];
    $session['platformOperator'] = (bool)$user['platform_operator'];
    if (isset($session['scopes']) && $user['role'] !== 'admin') $session['scopes'] = array_values(array_diff($session['scopes'], ['projects:create']));
    if (cloudMode() && !$session['platformOperator']) {
         $path = rawurldecode(parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH) ?: '');
        if (defined('BASE_PATH') && BASE_PATH !== '' && str_starts_with($path, BASE_PATH . '/')) $path = substr($path, strlen(BASE_PATH));
        $parts = explode('/', trim($path, '/'));
        if (count($parts) >= 4 && $parts[0] === 'api' && in_array($parts[1], ['v1','v2'], true)) {
            $sql = $parts[2] === 'projects' ? 'SELECT u.tenant_id FROM projects p JOIN users u ON u.id=p.owner_user_id WHERE p.id=?'
                 : ($parts[2] === 'users' ? 'SELECT tenant_id FROM users WHERE id=?' : null);
            if ($sql !== null) {
                $q = getDb()->prepare($sql); $q->execute([$parts[3]]); $tenant = $q->fetchColumn();
                if ($tenant !== false && $tenant !== $session['tenantId']) { errorResponse(404, 'not_found', 'Resource not found'); exit; }
            }
        }
    }
    return $session;
}
