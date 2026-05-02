<?php

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/src/helpers.php';
require_once __DIR__ . '/src/db.php';
require_once __DIR__ . '/src/jwt.php';
require_once __DIR__ . '/src/auth.php';
require_once __DIR__ . '/src/rateLimit.php';
require_once __DIR__ . '/src/bootstrap.php';

// ---------------------------------------------------------------------------
// Global exception handler — never leak stack traces to clients
// ---------------------------------------------------------------------------
set_exception_handler(function (Throwable $e): void {
    error_log('[QuietMetrix] ' . get_class($e) . ': ' . $e->getMessage()
              . ' in ' . $e->getFile() . ':' . $e->getLine());
    if (!headers_sent()) {
        http_response_code(500);
        header('Content-Type: application/json');
    }
    $payload = ['error' => 'internal_error', 'message' => 'Internal server error'];
    if (defined('DEBUG') && DEBUG) {
        $payload['debug'] = [
            'exception' => get_class($e),
            'message'   => $e->getMessage(),
            'file'      => basename($e->getFile()),
            'line'      => $e->getLine(),
        ];
    }
    echo json_encode($payload);
    exit;
});

// ---------------------------------------------------------------------------
// Refuse to start with placeholder secrets
// ---------------------------------------------------------------------------
if (JWT_SECRET === 'change_me_to_a_long_random_string_at_least_32_chars') {
    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode([
        'error'   => 'misconfigured',
        'message' => 'JWT_SECRET must be changed in config.php (use: openssl rand -hex 32)',
    ]);
    exit;
}
if (ADMIN_PASSWORD === 'change_me') {
    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode([
        'error'   => 'misconfigured',
        'message' => 'ADMIN_PASSWORD must be changed in config.php',
    ]);
    exit;
}

// ---------------------------------------------------------------------------
// Refuse to start if rate limiting is enabled without APCu
// ---------------------------------------------------------------------------
if (defined('RATE_LIMIT_ENABLED') && RATE_LIMIT_ENABLED && !function_exists('apcu_fetch')) {
    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode([
        'error'   => 'misconfigured',
        'message' => 'RATE_LIMIT_ENABLED is true but APCu is not available. Install and enable the APCu PHP extension.',
    ]);
    exit;
}

// ---------------------------------------------------------------------------
// First-run install (creates schema + admin user idempotently)
// ---------------------------------------------------------------------------
ensureInstalled();

// ---------------------------------------------------------------------------
// Security headers + CORS
// ---------------------------------------------------------------------------
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header('Strict-Transport-Security: max-age=31536000; includeSubDomains');

header("Content-Security-Policy: default-src 'self'; "
     . "script-src 'self'; "
     . "style-src 'self' 'unsafe-inline'; "
     . "img-src 'self' data:; "
     . "connect-src 'self'; "
     . "frame-ancestors 'none'; "
     . "base-uri 'self'");
header('Access-Control-Allow-Origin: ' . ALLOWED_ORIGIN);
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Authorization, Content-Type, X-QM-Api-Key');
header('Access-Control-Max-Age: 86400');

if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// ---------------------------------------------------------------------------
// Routing
// ---------------------------------------------------------------------------
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$uri    = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';

// Strip BASE_PATH prefix for subdirectory deployments (e.g. '/quietmetrix')
if (BASE_PATH !== '' && strpos($uri, BASE_PATH) === 0) {
    $uri = substr($uri, strlen(BASE_PATH));
}
$uri = '/' . ltrim(rtrim($uri, '/'), '/');

// Browser hitting the bare domain → send them to the dashboard.
if ($method === 'GET' && ($uri === '/' || $uri === '')) {
    $accept = $_SERVER['HTTP_ACCEPT'] ?? '';
    if (str_contains($accept, 'text/html')) {
        header('Location: ' . rtrim(BASE_PATH, '/') . '/dashboard/');
        http_response_code(302);
        exit;
    }
    jsonResponse(200, ['ok' => true, 'service' => 'quietmetrix', 'version' => '0.2.0']);
    exit;
}

// ---- Static / informational endpoints ----

if ($method === 'GET' && $uri === '/api/v1/health') {
    $dbOk = false;
    try { getDb()->query('SELECT 1'); $dbOk = true; } catch (Throwable) {}
    jsonResponse($dbOk ? 200 : 503, [
        'ok'      => $dbOk,
        'version' => '0.2.0',
        'db'      => $dbOk ? 'connected' : 'disconnected',
    ]);
    exit;
}

// /api/v1/_meta — lets the dashboard discover server-side flags (debug, etc.)
// without exposing config. Public on purpose: it returns no secrets.
if ($method === 'GET' && $uri === '/api/v1/_meta') {
    jsonResponse(200, [
        'version' => '0.2.0',
        'debug'   => DEBUG === true,
    ]);
    exit;
}

// ---- Auth ----

if ($method === 'POST' && $uri === '/api/v1/auth/login') {
    require_once __DIR__ . '/src/routes/auth.php';
    handleLogin();
    exit;
}
if ($method === 'POST' && $uri === '/api/v1/auth/refresh') {
    require_once __DIR__ . '/src/routes/auth.php';
    handleRefresh();
    exit;
}

// ---- Track ingest (api-key auth) ----

if ($method === 'POST' && $uri === '/api/v1/track') {
    require_once __DIR__ . '/src/routes/track.php';
    handleTrack();
    exit;
}
if ($method === 'POST' && $uri === '/api/v1/track/batch') {
    require_once __DIR__ . '/src/routes/track.php';
    handleTrackBatch();
    exit;
}

// ---- Projects (session auth) ----

if ($method === 'GET' && $uri === '/api/v1/projects') {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectsList();
    exit;
}
if ($method === 'POST' && $uri === '/api/v1/projects') {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectsCreate();
    exit;
}
if ($method === 'DELETE' && preg_match('#^/api/v1/projects/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectsDelete($m[1]);
    exit;
}

// ---- Dashboard data ----

if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/events$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    handleProjectEvents($m[1]);
    exit;
}
if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/aggregates$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    handleProjectAggregates($m[1]);
    exit;
}
if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/transitions$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    handleProjectTransitions($m[1]);
    exit;
}
if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/sessions$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    handleProjectSessions($m[1]);
    exit;
}
if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/retention$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    handleProjectRetention($m[1]);
    exit;
}

// ---- Demo (DEBUG=true only) ----

if (DEBUG === true) {
    if ($method === 'GET' && $uri === '/api/v1/_demo/aggregates') {
        require_once __DIR__ . '/src/routes/demo.php';
        handleDemoAggregates();
        exit;
    }
    if ($method === 'GET' && $uri === '/api/v1/_demo/events') {
        require_once __DIR__ . '/src/routes/demo.php';
        handleDemoEvents();
        exit;
    }
}

// Fallthrough — unknown route
errorResponse(404, 'not_found', 'Unknown endpoint: ' . $method . ' ' . $uri);
