<?php

// helpers.php has no dependency on config.php's constants, so it's safe to load
// first -- needed here for configFilePath(), which resolves which config file to
// require (normally config.php; QM_CONFIG_FILE can point local/e2e testing at a
// distinctly-named file instead, see tests/configFilePathTest.php).
require_once __DIR__ . '/src/helpers.php';
$configFile = configFilePath();

// Prevent absolute path disclosure if the config file is missing.
// The global exception handler is registered further down, so we
// catch this explicitly first.
if (!file_exists(__DIR__ . '/' . $configFile)) {
    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode([
        'error'   => 'misconfigured',
        'message' => "$configFile is missing. Copy config.example.php to $configFile and fill in the values.",
    ]);
    exit;
}

require_once __DIR__ . '/' . $configFile;
// Shared hosts may enable display_errors globally. Never disclose PHP warnings,
// filesystem paths, or database details in production API responses.
if (!defined('DEBUG') || !DEBUG) ini_set('display_errors', '0');
require_once __DIR__ . '/src/db.php';
require_once __DIR__ . '/src/jwt.php';
require_once __DIR__ . '/src/accessTokens.php';
require_once __DIR__ . '/src/auth.php';
require_once __DIR__ . '/src/rateLimit.php';
require_once __DIR__ . '/src/counterRegistry.php';
require_once __DIR__ . '/src/counters.php';
require_once __DIR__ . '/src/funnelValidation.php';
require_once __DIR__ . '/src/funnelSteps.php';
require_once __DIR__ . '/src/funnelCounterAnalyze.php';
require_once __DIR__ . '/src/retentionCounterAnalyze.php';
require_once __DIR__ . '/src/overviewCounterAnalyze.php';
require_once __DIR__ . '/src/sessionCounterAnalyze.php';
require_once __DIR__ . '/src/valueCounterAnalyze.php';
require_once __DIR__ . '/src/activationCounterAnalyze.php';
require_once __DIR__ . '/src/searchCounterAnalyze.php';
require_once __DIR__ . '/src/frictionCounterAnalyze.php';
require_once __DIR__ . '/src/askAnalyticsAnalyze.php';
require_once __DIR__ . '/src/ai.php';
require_once __DIR__ . '/src/analyticsWindow.php';
require_once __DIR__ . '/src/bootstrap.php';
require_once __DIR__ . '/src/maintenance.php';

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
if (strlen(JWT_SECRET) < 32 || str_starts_with(JWT_SECRET, 'change_me')) {
    jsonResponse(500, ['error'=>'misconfigured','message'=>'Set a strong random JWT_SECRET of at least 32 characters']); exit;
}
if (cloudMode()) {
    $public = parse_url(defined('APP_BASE_URL') ? APP_BASE_URL : '');
    $retention = getenv('QM_RETENTION_DAYS'); $budget = getenv('QM_COUNTER_REQUESTS_PER_DAY');
    if (!$public || ($public['scheme'] ?? '') !== 'https' || empty($public['host']) || isset($public['user']) || isset($public['query']) ||
        ALLOWED_ORIGIN === '*' || !RATE_LIMIT_ENABLED || !INGEST_IP_ENABLED || DEBUG ||
        ($retention !== false && $retention !== '' && (int)$retention <= 0) ||
        ($budget !== false && $budget !== '' && (int)$budget <= 0)) {
        jsonResponse(500, ['error'=>'misconfigured','message'=>'Hosted mode requires HTTPS public URL, explicit CORS, enabled limits, retention and production settings']); exit;
    }
}

// ---------------------------------------------------------------------------
// First-run install (creates schema + admin user idempotently)
// ---------------------------------------------------------------------------
ensureInstalled();
dataMaintenance(getDb());

// ---------------------------------------------------------------------------
// Security headers + CORS
// ---------------------------------------------------------------------------
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
if (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') {
    header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
}

$cspConnectSrc = defined('CSP_CONNECT_SRC') ? CSP_CONNECT_SRC : "'self'";
header("Content-Security-Policy: default-src 'self'; "
     . "script-src 'self' 'wasm-unsafe-eval'; "
     . "style-src 'self' 'unsafe-inline'; "
     . "img-src 'self' data:; "
     . "connect-src $cspConnectSrc; "
     . "frame-ancestors 'none'; "
     . "base-uri 'self'");
$corsOrigin = resolveCorsOrigin($_SERVER['HTTP_ORIGIN'] ?? null, ALLOWED_ORIGIN, allowedOriginsCsv());
if ($corsOrigin !== null) {
    header('Access-Control-Allow-Origin: ' . $corsOrigin);
    if ($corsOrigin !== '*') {
        header('Vary: Origin');
    }
}
header('Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS');
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

if (str_starts_with($uri, '/api/') && $uri !== '/api/v1/health') enforceSharedLimit('public-ip:' . (clientIp() ?? 'unknown'), 120);

if (in_array($uri, ['/api/v1/counters','/api/v2/counters'], true)) {
    $budget = getenv('QM_COUNTER_REQUESTS_PER_DAY');
    $budget = $budget !== false && $budget !== '' ? (int)$budget : (cloudMode() ? 10000 : 0);
    if ($budget > 0) enforceSharedLimit('ingest-daily:' . requireApiKey(), $budget, 86400);
}

// Browser hitting the bare domain → send them to the dashboard.
if ($method === 'GET' && ($uri === '/' || $uri === '')) {
    $accept = $_SERVER['HTTP_ACCEPT'] ?? '';
    if (str_contains($accept, 'text/html')) {
        header('Location: ' . rtrim(BASE_PATH, '/') . '/dashboard/' . (isset($_SERVER['QUERY_STRING']) && $_SERVER['QUERY_STRING'] !== '' ? '?' . $_SERVER['QUERY_STRING'] : ''));
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
        'ai_enabled' => (bool)aiConfig()['enabled'],
        'selfhost' => !aiConfig()['cloud'],
        'capabilities' => ['counter_batch_receipts', 'unique_window_v2', 'counter_protocol_v2_hourly', 'counter_protocol_v2_schema2'],
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

if ($method === 'POST' && $uri === '/api/v1/auth/logout') {
    require_once __DIR__ . '/src/routes/auth.php'; handleLogout(); exit;
}

// ---- Counter ingest (api-key auth) ----

if ($method === 'POST' && $uri === '/api/v1/counters') {
    require_once __DIR__ . '/src/routes/counters.php';
    handleCounters();
    exit;
}
if ($method === 'POST' && $uri === '/api/v2/counters') {
    require_once __DIR__ . '/src/counterRegistryV2.php';
    require_once __DIR__ . '/src/routes/countersV2.php';
    handleCountersV2();
    exit;
}
if ($method === 'GET' && $uri === '/api/v1/sdk/capabilities') {
    $projectId = requireApiKey();
    jsonResponse(200, [
        'capabilities' => ['counter_batch_receipts', 'unique_window_v2', 'counter_protocol_v2_hourly', 'counter_protocol_v2_schema2', 'remote_funnels', 'experiment_config_v2'],
        'minimum_versions' => ['counter_protocol_v2_hourly' => '0.4.0', 'counter_protocol_v2_schema2' => '0.6.0', 'remote_funnels' => '0.4.0', 'experiment_config_v2' => '0.4.0'],
    ]);
    exit;
}
if ($method === 'POST' && $uri === '/api/v1/funnels/register') {
    require_once __DIR__ . '/src/routes/funnels.php';
    handleFunnelsRegister();
    exit;
}
if ($method === 'GET' && $uri === '/api/v2/sdk/funnels') {
    require_once __DIR__ . '/src/routes/funnels.php';
    handleFunnelsConfigV2();
    exit;
}
if ($method === 'GET' && $uri === '/api/v1/experiments/config') {
    require_once __DIR__ . '/src/routes/experiments.php';
    handleExperimentsConfig();
    exit;
}
if ($method === 'GET' && $uri === '/api/v2/sdk/experiments') {
    require_once __DIR__ . '/src/routes/experiments.php';
    handleExperimentsConfigV2();
    exit;
}

// ---- Access tokens (agents/CLIs; session auth only — see docs/agents/setup.md) ----

if ($method === 'POST' && $uri === '/api/v1/tokens') {
    require_once __DIR__ . '/src/routes/tokens.php';
    handleTokensCreate();
    exit;
}
if ($method === 'GET' && $uri === '/api/v1/tokens') {
    require_once __DIR__ . '/src/routes/tokens.php';
    handleTokensList();
    exit;
}
if ($method === 'DELETE' && preg_match('#^/api/v1/tokens/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/tokens.php';
    handleTokensDelete($m[1]);
    exit;
}

// ---- Projects (session or token auth — see docs/agents/setup.md) ----

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
if ($method === 'PATCH' && preg_match('#^/api/v1/projects/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectsUpdate($m[1]);
    exit;
}
if ($method === 'DELETE' && preg_match('#^/api/v1/projects/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectsDelete($m[1]);
    exit;
}
if ($method === 'POST' && preg_match('#^/api/v1/projects/([^/]+)/regenerate-key$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectRegenerateKey($m[1]);
    exit;
}

// ---- Project members (assign / unassign users) ----

if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/members$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectMembersList($m[1]);
    exit;
}
if ($method === 'POST' && preg_match('#^/api/v1/projects/([^/]+)/members$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectMemberAdd($m[1]);
    exit;
}
if ($method === 'DELETE' && preg_match('#^/api/v1/projects/([^/]+)/members/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/projects.php';
    handleProjectMemberRemove($m[1], $m[2]);
    exit;
}

// ---- Funnels (session auth) ----

if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/funnels/([^/]+)/results$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/funnels.php';
    handleFunnelResults($m[1], $m[2]);
    exit;
}
if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/funnels$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/funnels.php';
    handleFunnelsList($m[1]);
    exit;
}
if ($method === 'POST' && preg_match('#^/api/v1/projects/([^/]+)/funnels$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/funnels.php';
    handleFunnelsCreate($m[1]);
    exit;
}
if ($method === 'PATCH' && preg_match('#^/api/v1/projects/([^/]+)/funnels/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/funnels.php';
    handleFunnelUpdate($m[1], $m[2]);
    exit;
}
if ($method === 'DELETE' && preg_match('#^/api/v1/projects/([^/]+)/funnels/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/funnels.php';
    handleFunnelDelete($m[1], $m[2]);
    exit;
}

// ---- A/B testing experiments (session auth for CRUD; see handleExperimentsConfig() above
// for the SDK-facing, api-key-authenticated config fetch) ----

if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/experiments/([^/]+)/results$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/experiments.php';
    handleExperimentResults($m[1], $m[2]);
    exit;
}
if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/experiments$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/experiments.php';
    handleExperimentsList($m[1]);
    exit;
}
if ($method === 'POST' && preg_match('#^/api/v1/projects/([^/]+)/experiments$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/experiments.php';
    handleExperimentsCreate($m[1]);
    exit;
}
if ($method === 'PATCH' && preg_match('#^/api/v1/projects/([^/]+)/experiments/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/experiments.php';
    handleExperimentUpdate($m[1], $m[2]);
    exit;
}
if ($method === 'DELETE' && preg_match('#^/api/v1/projects/([^/]+)/experiments/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/experiments.php';
    handleExperimentDelete($m[1], $m[2]);
    exit;
}

// ---- Users + invitations ----

if ($method === 'GET' && $uri === '/api/v1/users') {
    require_once __DIR__ . '/src/routes/users.php';
    handleUsersList();
    exit;
}
if ($method === 'POST' && preg_match('#^/api/v1/users/([^/]+)/recover$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/users.php';
    handleUserRecovery($m[1]); exit;
}

if ($method === 'POST' && $uri === '/api/v1/users/invite') {
    require_once __DIR__ . '/src/routes/users.php';
    handleUserInvite();
    exit;
}
if ($method === 'PATCH' && preg_match('#^/api/v1/users/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/users.php';
    handleUserUpdateRole($m[1]);
    exit;
}
if ($method === 'DELETE' && preg_match('#^/api/v1/users/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/users.php';
    handleUserDelete($m[1]);
    exit;
}
if ($method === 'GET' && preg_match('#^/api/v1/invites/([^/]+)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/users.php';
    handleInviteGet($m[1]);
    exit;
}
if ($method === 'POST' && preg_match('#^/api/v1/invites/([^/]+)/accept$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/users.php';
    handleInviteAccept($m[1]);
    exit;
}

// Operator and project AI routes.
if (preg_match('#^/api/v1/_operator/ai/(plans|assignments|usage)(?:/([^/]+))?$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/ai.php';
    handleAiOperator($method, $m[1], $m[2] ?? null);
    exit;
}
if (preg_match('#^/api/v1/projects/([^/]+)/ai/(capabilities|insight|ask)$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/ai.php';
    if ($method !== ($m[2] === 'capabilities' ? 'GET' : 'POST')) { errorResponse(405, 'method_not_allowed', 'Unsupported method'); exit; }
    handleAi($m[1], $m[2]);
    exit;
}

// ---- Dashboard data ----

if ($method === 'GET' && preg_match('#^/api/v2/projects/([^/]+)/report$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    handleProjectReportV2($m[1]);
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
if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/search$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    handleProjectSearch($m[1]);
    exit;
}
if ($method === 'GET' && preg_match('#^/api/v1/projects/([^/]+)/friction$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    handleProjectFriction($m[1]);
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
if ($method === 'POST' && preg_match('#^/api/v1/projects/([^/]+)/ask$#', $uri, $m)) {
    require_once __DIR__ . '/src/routes/dashboard.php';
    require_once __DIR__ . '/src/routes/ai.php';
    handleAi($m[1], 'ask', true);
    exit;
}

// ---- Demo (DEBUG=true only) ----

if (DEBUG === true) {
    if ($method === 'GET' && $uri === '/api/v1/_demo/aggregates') {
        require_once __DIR__ . '/src/routes/demo.php';
        handleDemoAggregates();
        exit;
    }
}

// Fallthrough — unknown route
errorResponse(404, 'not_found', 'Unknown endpoint: ' . $method . ' ' . $uri);
