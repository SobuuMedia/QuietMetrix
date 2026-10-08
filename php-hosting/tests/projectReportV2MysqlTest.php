<?php

/** Opt-in typed-report query proof. Uses a disposable MySQL schema. */
define('COUNTER_KEY_REGEX', '/^[a-z0-9_]{1,32}$/');
define('COUNTER_VALUE_REGEX', '/^[A-Za-z0-9_.:\/-]{1,128}$/');
$host = getenv('QM_V2_MYSQL_HOST');
if (!$host) { echo "skipped: set QM_V2_MYSQL_HOST for v2 report MySQL integration\n"; exit(0); }
$port = getenv('QM_V2_MYSQL_PORT') ?: '3306';
$database = getenv('QM_V2_MYSQL_DB') ?: 'quietmetrix_v2_test';
$user = getenv('QM_V2_MYSQL_USER') ?: 'root';
$pass = getenv('QM_V2_MYSQL_PASS') ?: '';
$pdo = new PDO("mysql:host={$host};port={$port};dbname={$database};charset=utf8mb4", $user, $pass, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]);
$pdo->exec('CREATE TABLE IF NOT EXISTS counters (project_id VARCHAR(36), day VARCHAR(10)) ENGINE=InnoDB');
$pdo->exec('CREATE TABLE IF NOT EXISTS counter_cells_v2 (
 project_id VARCHAR(36), day VARCHAR(10), hour SMALLINT, metric VARCHAR(64), platform VARCHAR(20), sdk_version VARCHAR(32), app_version VARCHAR(32),
 country CHAR(2), dims_hash CHAR(64), dims TEXT, n BIGINT, contributors BIGINT, updated_at VARCHAR(32),
 PRIMARY KEY(project_id,day,hour,metric,platform,app_version,country,dims_hash)) ENGINE=InnoDB');
$project = 'qm-report-v2-test';
$pdo->prepare('DELETE FROM counter_cells_v2 WHERE project_id=?')->execute([$project]);
$today = new DateTimeImmutable('today', new DateTimeZone('UTC'));
$insert = $pdo->prepare('INSERT INTO counter_cells_v2 (project_id,day,hour,metric,platform,sdk_version,app_version,country,dims_hash,dims,n,contributors,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)');
for ($index = 0; $index < 5; $index++) {
    $day = $today->modify("-{$index} days")->format('Y-m-d');
    $dims = json_encode(['days' => '7', 'end_day' => $today->format('Y-m-d'), 'scope_key' => 'all', 'scope_kind' => 'project']);
    $insert->execute([$project,$day,-1,'unique_window_v2','android','2.0','', '  ',str_pad((string)$index,64,'0',STR_PAD_LEFT),$dims,1,1,$today->format('Y-m-d') . 'T12:00:00Z']);
}
$previousEnd = $today->modify('-7 days');
for ($index = 0; $index < 5; $index++) {
    $day = $previousEnd->modify("-{$index} days")->format('Y-m-d');
    $dims = json_encode(['days' => '7', 'end_day' => $previousEnd->format('Y-m-d'), 'scope_key' => 'all', 'scope_kind' => 'project']);
    $insert->execute([$project,$day,-1,'unique_window_v2','android','2.0','', '  ',str_pad((string)($index + 10),64,'0',STR_PAD_LEFT),$dims,1,1,$today->format('Y-m-d') . 'T12:00:00Z']);
}
$dims = json_encode(['days' => '1', 'end_day' => $today->format('Y-m-d'), 'scope_key' => 'all', 'scope_kind' => 'project']);
$insert->execute([$project,$today->format('Y-m-d'),-1,'unique_window_v2','android','2.0','', '  ',str_repeat('f',64),$dims,99,99,$today->format('Y-m-d') . 'T12:00:00Z']);
foreach ([
    ['visit_start_v2', '{}', 8],
    ['event_v2', '{"name":"open"}', 12],
    ['visit_duration_ms_v2', '{}', 300000],
    ['visit_complete_v2', '{}', 5],
] as $index => [$metric, $metricDims, $count]) {
    $insert->execute([$project,$today->format('Y-m-d'),-1,$metric,'android','2.0','', '  ',str_repeat(dechex($index + 1),64),$metricDims,$count,5,$today->format('Y-m-d') . 'T12:00:00Z']);
}
$entryDay = $today->modify('-5 days');
for ($index = 0; $index < 5; $index++) {
    foreach ([
        ['retention_entry_v2', $entryDay, ['entry_day' => $entryDay->format('Y-m-d')]],
        ['retention_return_v2', $entryDay->modify('+1 day'), ['entry_day' => $entryDay->format('Y-m-d'), 'age_day' => '1']],
        ['screen_view_v2', $today, ['screen' => 'Home']],
    ] as $offset => [$metric, $day, $dimsArray]) {
        $insert->execute([$project,$day->format('Y-m-d'),-1,$metric,'android','2.0',"ret-$index", '  ',hash('sha256', "ret-$index-$offset"),json_encode($dimsArray),1,1,$today->format('Y-m-d') . 'T12:00:00Z']);
    }
}
$previousEntryDay = $today->modify('-12 days');
for ($index = 0; $index < 5; $index++) {
    foreach ([
        ['retention_entry_v2', $previousEntryDay, ['entry_day' => $previousEntryDay->format('Y-m-d')]],
        ['retention_return_v2', $previousEntryDay->modify('+1 day'), ['entry_day' => $previousEntryDay->format('Y-m-d'), 'age_day' => '1']],
    ] as $offset => [$metric, $day, $dimsArray]) {
        $insert->execute([$project,$day->format('Y-m-d'),-1,$metric,'android','2.0',"pre-$index", '  ',hash('sha256', "pre-$index-$offset"),json_encode($dimsArray),1,1,$today->format('Y-m-d') . 'T12:00:00Z']);
    }
}

$GLOBALS['report_v2_db'] = $pdo;
$GLOBALS['report_v2_result'] = null;
function requireAnalyticsAccess(string $projectId): void {}
function getDb(): PDO { return $GLOBALS['report_v2_db']; }
function jsonResponse(int $status, $body): void { $GLOBALS['report_v2_result'] = [$status, $body]; }
function errorResponse(int $status, string $code, string $message = ''): void { jsonResponse($status, ['error' => $code, 'message' => $message]); }
require_once __DIR__ . '/../src/routes/dashboard.php';
$_GET = ['range' => '7d', 'anchor' => $today->format('Y-m-d'), 'platform' => 'all'];
handleProjectReportV2($project);
$report = $GLOBALS['report_v2_result'];
if ($report[0] !== 200 || $report[1]['overview']['people']['value'] !== 5 || $report[1]['overview']['people']['state'] !== 'reported') {
    fwrite(STDERR, "FAIL: exact-window People report did not return 5 available installations\n"); exit(1);
}
if (($report[1]['coverage']['source'] ?? null) !== 'v2' || ($report[1]['coverage']['resolution'] ?? null) !== 'daily') {
    fwrite(STDERR, "FAIL: report coverage source/resolution metadata is incorrect\n"); exit(1);
}
if (($report[1]['comparison']['people']['value'] ?? null) !== 5 || (array)($report[1]['comparison_charts'] ?? []) !== []) {
    fwrite(STDERR, "FAIL: Overview prior-period People must be returned while detailed comparisons remain off\n"); exit(1);
}
if (($report[1]['overview']['visits']['value'] ?? null) !== 8 || ($report[1]['overview']['events']['value'] ?? null) !== 12 || ($report[1]['overview']['visit_duration']['value'] ?? null) !== 60000) {
    fwrite(STDERR, "FAIL: typed activity measures or weighted visit duration are incorrect\n"); exit(1);
}
if (($report[1]['charts']['visits'][6]['measures']['visits']['value'] ?? null) !== 8 || ($report[1]['charts']['visit_duration'][6]['measures']['visit_duration']['value'] ?? null) !== 60000) {
    fwrite(STDERR, "FAIL: typed activity chart bins are incorrect\n"); exit(1);
}
if (abs((float)($report[1]['retention']['day_1']['rate'] ?? -1) - 1.0) > 0.000001 || ($report[1]['retention']['day_7']['state'] ?? null) !== 'not_mature' || ($report[1]['definitions']['screens'] ?? []) !== ['Home'] || ($report[1]['flow']['screen_views']['value'] ?? null) != 5) {
    fwrite(STDERR, "FAIL: mature retention, k-gated screen catalog, or typed flow measures are incorrect\n"); exit(1);
}
if (($report[1]['retention_cohorts'][0]['entry_day'] ?? null) !== $entryDay->format('Y-m-d') || abs((float)($report[1]['retention_cohorts'][0]['days']['1']['rate'] ?? -1) - 1.0) > 0.000001 || ($report[1]['retention_cohorts'][0]['days']['7']['state'] ?? null) !== 'not_mature') {
    fwrite(STDERR, "FAIL: per-cohort typed retention measures are incorrect\n"); exit(1);
}
if (count($report[1]['charts']['people'] ?? []) !== 7 || ($report[1]['charts']['people'][0]['start_day'] ?? null) !== $today->modify('-6 days')->format('Y-m-d')) {
    fwrite(STDERR, "FAIL: seven-day People chart bins were not returned with date boundaries\n"); exit(1);
}
$_GET['compare'] = 'true';
handleProjectReportV2($project);
$comparison = $GLOBALS['report_v2_result'][1]['comparison']['people'] ?? [];
if (($GLOBALS['report_v2_result'][0] ?? 0) !== 200 || ($comparison['value'] ?? null) !== 5) {
    fwrite(STDERR, "FAIL: previous-period report did not return the adjacent fixed-window value\n"); exit(1);
}
$previousRetention = $GLOBALS['report_v2_result'][1]['comparison_retention_cohorts'] ?? [];
if (($previousRetention[0]['entry_day'] ?? null) !== $previousEntryDay->format('Y-m-d') || abs((float)($previousRetention[0]['days']['1']['rate'] ?? -1) - 1.0) > 0.000001) {
    fwrite(STDERR, "FAIL: previous-period typed retention cohorts are not aligned to the prior period\n"); exit(1);
}
$comparisonBins = $GLOBALS['report_v2_result'][1]['comparison_charts']['people'] ?? [];
if (count($comparisonBins) !== 7 || ($comparisonBins[0]['start_day'] ?? null) !== $today->modify('-13 days')->format('Y-m-d')) {
    fwrite(STDERR, "FAIL: previous-period People chart bins do not cover the preceding seven days\n"); exit(1);
}
$_GET = ['range' => '90d', 'anchor' => $today->format('Y-m-d'), 'platform' => 'all'];
handleProjectReportV2($project);
$weekly = $GLOBALS['report_v2_result'][1]['charts']['people'] ?? [];
if (($GLOBALS['report_v2_result'][0] ?? 0) !== 200 || count($weekly) !== 13 || !array_key_exists('hour', $weekly[12] ?? []) || $weekly[12]['hour'] !== null || ($weekly[12]['end_day'] ?? null) !== $today->format('Y-m-d')) {
    fwrite(STDERR, "FAIL: ninety-day People chart did not return twelve full and one final short bin\n"); exit(1);
}
$pdo->prepare('DELETE FROM counter_cells_v2 WHERE project_id=?')->execute([$project]);
echo "projectReportV2MysqlTest passed\n";
