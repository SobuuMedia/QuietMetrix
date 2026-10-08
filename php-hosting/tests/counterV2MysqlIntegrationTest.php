<?php

ob_start();

/** Opt-in MySQL route proof. Set QM_V2_MYSQL_HOST/DB/USER/PASS to a disposable test database. */
$host = getenv('QM_V2_MYSQL_HOST');
if (!$host) { echo "skipped: set QM_V2_MYSQL_HOST for v2 MySQL integration\n"; exit(0); }
$port = getenv('QM_V2_MYSQL_PORT') ?: '3306';
$database = getenv('QM_V2_MYSQL_DB') ?: 'quietmetrix_v2_test';
$user = getenv('QM_V2_MYSQL_USER') ?: 'root';
$pass = getenv('QM_V2_MYSQL_PASS') ?: '';
$pdo = new PDO("mysql:host={$host};port={$port};dbname={$database};charset=utf8mb4", $user, $pass, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]);
$pdo->exec('CREATE TABLE IF NOT EXISTS projects (id VARCHAR(36) NOT NULL PRIMARY KEY) ENGINE=InnoDB');
$pdo->exec('CREATE TABLE IF NOT EXISTS counter_cells_v2 (
 project_id VARCHAR(36) NOT NULL, day VARCHAR(10) NOT NULL, hour SMALLINT NOT NULL DEFAULT -1,
 metric VARCHAR(64) NOT NULL, platform VARCHAR(20) NOT NULL DEFAULT \'unknown\', sdk_version VARCHAR(32) NOT NULL DEFAULT \'unknown\', app_version VARCHAR(32) NOT NULL DEFAULT \'\',
 cardinality_hash CHAR(64) NOT NULL DEFAULT \'\',
 country CHAR(2) NOT NULL DEFAULT \'  \', dims_hash CHAR(64) NOT NULL, dims TEXT NOT NULL, n BIGINT NOT NULL DEFAULT 0,
 segment_kind VARCHAR(20) NOT NULL DEFAULT \'global\', segment_value VARCHAR(128) NOT NULL DEFAULT \'all\',
 contributors BIGINT NOT NULL DEFAULT 0, updated_at VARCHAR(32) NOT NULL,
 PRIMARY KEY(project_id,day,hour,metric,platform,app_version,country,dims_hash,segment_kind,segment_value)) ENGINE=InnoDB');
$cellColumns = array_column($pdo->query('SHOW COLUMNS FROM counter_cells_v2')->fetchAll(), 'Field');
if (!in_array('cardinality_hash', $cellColumns, true)) $pdo->exec("ALTER TABLE counter_cells_v2 ADD cardinality_hash CHAR(64) NOT NULL DEFAULT ''");
if (!in_array('segment_kind', $cellColumns, true)) {
    $pdo->exec("ALTER TABLE counter_cells_v2 ADD segment_kind VARCHAR(20) NOT NULL DEFAULT 'global', ADD segment_value VARCHAR(128) NOT NULL DEFAULT 'all'");
    $pdo->exec('ALTER TABLE counter_cells_v2 DROP PRIMARY KEY, ADD PRIMARY KEY(project_id,day,hour,metric,platform,app_version,country,dims_hash,segment_kind,segment_value)');
}
$pdo->exec('CREATE TABLE IF NOT EXISTS counter_batch_receipts_v2 (
 project_id VARCHAR(36) NOT NULL, batch_id VARCHAR(64) NOT NULL, payload_hash CHAR(64) NOT NULL, claim_token CHAR(32) NOT NULL,
 accepted INT NOT NULL DEFAULT 0, created_at VARCHAR(32) NOT NULL, expires_at VARCHAR(32) NOT NULL, PRIMARY KEY(project_id,batch_id)) ENGINE=InnoDB');
$pdo->exec('CREATE TABLE IF NOT EXISTS funnels (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, project_id VARCHAR(36) NOT NULL, funnel_key VARCHAR(64) NOT NULL,
 name VARCHAR(255) NOT NULL, description TEXT NULL, steps JSON NOT NULL, window_seconds BIGINT NOT NULL,
 source VARCHAR(20) NOT NULL, locked TINYINT NOT NULL DEFAULT 0, count_mode VARCHAR(20) NOT NULL,
 identity_scope VARCHAR(32) NOT NULL, correlation_property VARCHAR(128) NULL, revision INT NOT NULL DEFAULT 0,
 archived_at VARCHAR(32) NULL, created_at VARCHAR(32) NOT NULL, updated_at VARCHAR(32) NOT NULL) ENGINE=InnoDB');
$project = 'qm-counter-v2-test';
$pdo->prepare('INSERT INTO projects(id) VALUES (?) ON DUPLICATE KEY UPDATE id=id')->execute([$project]);
$pdo->prepare('DELETE FROM counter_cells_v2 WHERE project_id=?')->execute([$project]);
$pdo->prepare('DELETE FROM counter_batch_receipts_v2 WHERE project_id=?')->execute([$project]);

define('COUNTER_KEY_REGEX', '/^[a-z0-9_]{1,32}$/');
define('COUNTER_VALUE_REGEX', '/^[A-Za-z0-9_.:\/-]{1,128}$/');
define('COUNTERS_MAX_DISTINCT_CELLS_PER_METRIC', 500);
$GLOBALS['qm_v2_db'] = $pdo;
$GLOBALS['qm_v2_project'] = $project;
$GLOBALS['qm_v2_body'] = null;
$GLOBALS['qm_v2_response'] = null;
$_SERVER['REMOTE_ADDR'] = '127.0.0.1';
function checkOrigin(): void {}
function requireApiKey(): string { return $GLOBALS['qm_v2_project']; }
function enforceLimit(string $key, int $rps, int $burst): void {}
function clientIp(): string { return '127.0.0.1'; }
function getJsonBody(): array { return $GLOBALS['qm_v2_body']; }
function getDb(): PDO { return $GLOBALS['qm_v2_db']; }
function jsonResponse(int $status, $data): void { $GLOBALS['qm_v2_response'] = [$status, $data]; }
function errorResponse(int $status, string $code, string $message = ''): void { jsonResponse($status, ['error' => $code, 'message' => $message]); }
require_once __DIR__ . '/../src/counterRegistryV2.php';
require_once __DIR__ . '/../src/counters.php';
require_once __DIR__ . '/../src/funnelSteps.php';
require_once __DIR__ . '/../src/routes/countersV2.php';
require_once __DIR__ . '/../src/routes/funnels.php';

$failures = 0;
function mysqlCheck(string $label, bool $ok): void {
    global $failures;
    if ($ok) echo "  ok: {$label}\n";
    else { $failures++; echo "FAIL: {$label}\n"; }
}
function sendV2(string $batchId, array $items, ?int $hour = 8): array {
    $GLOBALS['qm_v2_body'] = ['batch_id' => $batchId, 'day' => gmdate('Y-m-d'), 'hour' => $hour,
        'sdk' => ['platform' => 'android', 'version' => '2.0'], 'app' => ['version' => '1.0'], 'counters' => $items];
    $GLOBALS['qm_v2_response'] = null;
    handleCountersV2();
    return $GLOBALS['qm_v2_response'];
}
$one = [['m' => 'screen_view_v2', 'd' => ['screen' => 'home'], 'n' => 3, 'contributors' => 1]];
$first = sendV2('batch_first_000001', $one);
$replay = sendV2('batch_first_000001', $one);
$rows = (int)$pdo->query("SELECT COUNT(*) FROM counter_cells_v2 WHERE project_id='qm-counter-v2-test'")->fetchColumn();
$sum = (int)$pdo->query("SELECT SUM(n) FROM counter_cells_v2 WHERE project_id='qm-counter-v2-test'")->fetchColumn();
mysqlCheck('first batch accepted', $first[0] === 202 && $first[1]['replayed'] === false);
mysqlCheck('same batch replay acknowledged', $replay[0] === 202 && $replay[1]['replayed'] === true);
mysqlCheck('replay increments exactly once', $rows === 1 && $sum === 3);
$conflict = sendV2('batch_first_000001', [['m' => 'screen_view_v2', 'd' => ['screen' => 'search'], 'n' => 1, 'contributors' => 0]]);
mysqlCheck('changed payload conflicts', $conflict[0] === 409);
$invalid = sendV2('batch_invalid_00001', [$one[0], ['m' => 'unknown', 'd' => [], 'n' => 1, 'contributors' => 0]]);
$afterInvalid = (int)$pdo->query("SELECT COUNT(*) FROM counter_cells_v2 WHERE project_id='qm-counter-v2-test'")->fetchColumn();
mysqlCheck('invalid sibling rejects whole batch without a partial cell', $invalid[0] === 400 && $afterInvalid === 1);
$today = gmdate('Y-m-d');
$yesterday = gmdate('Y-m-d', strtotime('-1 day', strtotime($today)));
$GLOBALS['qm_v2_body'] = ['schema'=>2, 'batch_id'=>'schema2_batch_00001', 'sdk'=>['platform'=>'android','version'=>'2.1'],
    'items'=>[
        ['day'=>$yesterday,'hour'=>7,'metric'=>'screen_view_v2','segment'=>['kind'=>'global','value'=>'all'],'dimensions'=>['screen'=>'home'],'n'=>2],
        ['day'=>$today,'hour'=>null,'metric'=>'screen_view_v2','segment'=>['kind'=>'platform','value'=>'android'],'dimensions'=>['screen'=>'home'],'n'=>1],
    ]];
$GLOBALS['qm_v2_response'] = null; handleCountersV2(); $schema2First=$GLOBALS['qm_v2_response'];
$GLOBALS['qm_v2_response'] = null; handleCountersV2(); $schema2Replay=$GLOBALS['qm_v2_response'];
$segmentRows = $pdo->prepare("SELECT COUNT(*) FROM counter_cells_v2 WHERE project_id=? AND ((day=? AND hour=7 AND segment_kind='global' AND segment_value='all') OR (day=? AND hour=-1 AND segment_kind='platform' AND segment_value='android'))");
$segmentRows->execute([$project,$yesterday,$today]);
mysqlCheck('schema-2 per-item day/hour/segment rows and idempotent replay', $schema2First[0]===202 && $schema2Replay[1]['replayed']===true && (int)$segmentRows->fetchColumn()===2);
$badSegmentBody=$GLOBALS['qm_v2_body']; $badSegmentBody['batch_id']='schema2_invalid_0001'; $badSegmentBody['items'][1]['segment']=['kind'=>'language','value'=>'fr'];
$GLOBALS['qm_v2_body']=$badSegmentBody; $GLOBALS['qm_v2_response']=null; handleCountersV2();
$schema2AfterBad=(int)$pdo->query("SELECT COUNT(*) FROM counter_cells_v2 WHERE project_id='qm-counter-v2-test'")->fetchColumn();
mysqlCheck('schema-2 invalid segment rejects all sibling writes', $GLOBALS['qm_v2_response'][0]===400 && $schema2AfterBad===3);
$pdo->prepare('DELETE FROM counter_cells_v2 WHERE project_id=?')->execute([$project]);
$pdo->prepare('DELETE FROM counter_batch_receipts_v2 WHERE project_id=?')->execute([$project]);
$today = gmdate('Y-m-d');
$entry = gmdate('Y-m-d', strtotime('-1 day', strtotime($today)));
$insertFunnelCell = $pdo->prepare('INSERT INTO counter_cells_v2(project_id,day,hour,metric,platform,sdk_version,app_version,country,dims_hash,dims,n,contributors,updated_at) VALUES(?,?, -1, \'funnel_step_v2\', \'android\', \'2.0\', ?, \'  \', ?, ?, 1, 1, ?)');
foreach (range(1, 5) as $i) {
    $dims = ['namespace' => 'dashboard', 'f' => 'signup', 'revision' => '3', 'step' => '1', 'entry_day' => $entry];
    $insertFunnelCell->execute([$project, $today, "app-{$i}", hash('sha256', "step1-{$i}"), json_encode($dims), $today . 'T00:00:00Z']);
}
foreach (range(1, 4) as $i) {
    $dims = ['namespace' => 'dashboard', 'f' => 'signup', 'revision' => '3', 'step' => '2', 'entry_day' => $entry];
    $insertFunnelCell->execute([$project, $today, "app-step2-{$i}", hash('sha256', "step2-{$i}"), json_encode($dims), $today . 'T00:00:00Z']);
}
$oldRevision = ['namespace' => 'dashboard', 'f' => 'signup', 'revision' => '2', 'step' => '2', 'entry_day' => $entry];
$insertFunnelCell->execute([$project, $today, 'app-oldrev', hash('sha256', 'old-rev'), json_encode($oldRevision), $today . 'T00:00:00Z']);
$funnelCells = readDashboardFunnelV2Steps($project, 'signup', 3, $entry, $entry);
mysqlCheck('dashboard funnel query pins revision and hides steps below five contributors', $funnelCells === [['step' => 1, 'n' => 5]]);
$insertExperimentCell = $pdo->prepare('INSERT INTO counter_cells_v2(project_id,day,hour,metric,platform,sdk_version,app_version,country,dims_hash,dims,n,contributors,updated_at) VALUES(?,?,-1,?, ?,\'2.0\',?,\'  \',?,?,1,1,?)');
foreach (array_merge(
    array_fill(0, 2, ['a', 'android', 3]), array_fill(0, 3, ['a', 'ios', 3]),
    array_fill(0, 4, ['b', 'android', 3]), [['a', 'android', 2]],
) as [$variant, $platform, $revision]) {
    $dims = ['exp' => 'login_help', 'revision' => (string)$revision, 'variant' => $variant];
    $app = 'e' . bin2hex(random_bytes(6));
    $insertExperimentCell->execute([$project, $today, 'experiment_exposure_v2', $platform, $app, hash('sha256', $app), json_encode($dims), $today . 'T00:00:00Z']);
}
$experimentCells = counterReadExperimentCellsV2($project, 'experiment_exposure_v2', $today, $today, 'login_help', 3);
mysqlCheck('experiment report aggregates platforms and gates variants at five contributors',
    $experimentCells === [['dims' => ['exp' => 'login_help', 'rev' => '3', 'variant' => 'a'], 'n' => 5, 'devices' => 5]]);
$pdo->prepare('DELETE FROM funnels WHERE project_id=?')->execute([$project]);
$pdo->prepare('INSERT INTO funnels(project_id,funnel_key,name,steps,window_seconds,source,locked,count_mode,identity_scope,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)')
    ->execute([$project, 'signup', 'Signup', json_encode([
        ['key' => 'start', 'event' => 'signup_started'], ['key' => 'done', 'event' => 'signup_done'],
    ]), 604800, 'dashboard', 1, 'actor', 'install_or_session', 3, $today, $today]);
handleFunnelsConfigV2();
$configResponse = $GLOBALS['qm_v2_response'];
mysqlCheck('SDK config contains only minimal active dashboard definition and revision',
    $configResponse[0] === 200 && $configResponse[1]['namespace'] === 'dashboard' &&
    count($configResponse[1]['funnels']) === 1 && $configResponse[1]['funnels'][0]['revision'] === 3 &&
    !array_key_exists('description', $configResponse[1]['funnels'][0]));
$pdo->prepare('DELETE FROM funnels WHERE project_id=?')->execute([$project]);
$pdo->prepare('DELETE FROM counter_cells_v2 WHERE project_id=?')->execute([$project]);
$transitionInsert = $pdo->prepare('INSERT INTO counter_cells_v2(project_id,day,hour,metric,platform,sdk_version,app_version,country,dims_hash,dims,n,contributors,updated_at) VALUES(?,?,-1,\'screen_transition_v2\', ?,\'2.0\', ?,\'  \', ?, ?, 1, ?, ?)');
foreach (array_merge(array_fill(0, 5, 'android'), ['ios','ios']) as $index => $platform) {
    $dims = json_encode(['from' => 'home', 'to' => 'details']);
    $transitionInsert->execute([$project, $today, $platform, "app-$index", str_pad((string)($index + 1), 64, '0', STR_PAD_LEFT), $dims, 1, $today]);
}
$androidFlow = counterReadTransitionsV2($project, $today, $today, 'android');
$iosFlow = counterReadTransitionsV2($project, $today, $today, 'ios');
mysqlCheck('transition v2 platform filter gates contributors independently and blocks legacy fallback when suppressed',
    $androidFlow['present'] && count($androidFlow['cells']) === 1 && $androidFlow['cells'][0]['n'] === 5 &&
    $iosFlow['present'] && $iosFlow['cells'] === []);
$pdo->prepare('DELETE FROM counter_cells_v2 WHERE project_id=?')->execute([$project]);
// Rejecting a dimension-budget overflow must roll back its receipt as well as cells.
$insert=$pdo->prepare('INSERT INTO counter_cells_v2(project_id,day,metric,dims_hash,cardinality_hash,dims,n,contributors,updated_at) VALUES(?,?,?,?,?,?,1,1,?)');
for($i=0;$i<500;$i++) {
    $dims=['name'=>'budget_'.$i];
    $insert->execute([$project,$today,'event_v2',hash('sha256','budget_'.$i),counterV2CardinalityHash('event_v2',$dims),json_encode($dims),nowForFixture()]);
}
function nowForFixture(): string { return gmdate('Y-m-d\\TH:i:s\\Z'); }
$overflow=sendV2('overflow_batch_00001',[['m'=>'event_v2','d'=>['name'=>'budget_extra'],'n'=>1,'contributors'=>1]]);
$retry=sendV2('overflow_batch_00001',[['m'=>'event_v2','d'=>['name'=>'budget_extra'],'n'=>1,'contributors'=>1]]);
$q=$pdo->prepare('SELECT COUNT(*) FROM counter_batch_receipts_v2 WHERE project_id=? AND batch_id=?');$q->execute([$project,'overflow_batch_00001']);
mysqlCheck('dimension overflow and its retry reject without a successful receipt', $overflow[0]===400 && $retry[0]===400 && (int)$q->fetchColumn()===0);
$pdo->prepare('DELETE FROM counter_cells_v2 WHERE project_id=?')->execute([$project]);
if ($failures) exit(1);
echo "counterV2MysqlIntegrationTest passed\n";
