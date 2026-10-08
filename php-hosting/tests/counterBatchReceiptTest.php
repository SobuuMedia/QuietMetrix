<?php
/**
 * Receipt semantics for /api/v1/counters: replay, changed-payload conflict, and atomic validation.
 * Run with: php php-hosting/tests/counterBatchReceiptTest.php
 */

$fixtureDb = new PDO('sqlite::memory:');
$fixtureDb->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
function getDb(): PDO { global $fixtureDb; return $fixtureDb; }
function now(): string { return gmdate('Y-m-d\TH:i:s\Z'); }
require_once __DIR__ . '/../src/counterRegistry.php';
require_once __DIR__ . '/../src/counters.php';

$fixtureDb->exec('CREATE TABLE counters (project_id TEXT, day TEXT, metric TEXT, platform TEXT, app_version TEXT, country TEXT, dims_hash TEXT, dims TEXT, n INTEGER, devices INTEGER, updated_at TEXT, PRIMARY KEY(project_id,day,metric,platform,app_version,country,dims_hash))');
$fixtureDb->exec('CREATE TABLE counters_quarantine (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id TEXT, payload TEXT, quarantine_reason TEXT, quarantine_detail TEXT, quarantined_at TEXT)');
$fixtureDb->exec('CREATE TABLE counter_batch_receipts (project_id TEXT, batch_id TEXT, payload_hash TEXT, claim_token TEXT, accepted INTEGER, quarantined INTEGER, created_at TEXT, PRIMARY KEY(project_id,batch_id))');

$body = [
    'day' => '2026-09-04',
    'sdk' => ['platform' => 'android', 'version' => '1.0'],
    'app' => ['version' => '2.4', 'country' => null],
    'counters' => [['m' => 'event', 'd' => ['name' => 'open_book'], 'n' => 7, 'u' => 1]],
];
$hash = counterBatchPayloadHash($body);
if ($hash !== '4cfdd303c5dbfbd0cf513c6ceb5e24a4650c214d077a345e13dae9513a3aa5e7') {
    fwrite(STDERR, "FAIL: canonical batch hash changed; update both backend vectors together\n");
    exit(1);
}
function checkBatch(string $label, bool $expected, bool $actual): void {
    if ($expected !== $actual) { fwrite(STDERR, "FAIL: $label\n"); exit(1); }
    echo "ok: $label\n";
}

$first = counterApplyBatchOnce('p1', '0123456789abcdef', $hash, str_repeat('a', 32), '2026-09-04', 'android', '2.4', '', $body['counters']);
$replay = counterApplyBatchOnce('p1', '0123456789abcdef', $hash, str_repeat('b', 32), '2026-09-04', 'android', '2.4', '', $body['counters']);
checkBatch('first batch accepted once', !$first['conflict'] && !$first['replayed'] && $first['accepted'] === 1, true);
checkBatch('identical retry replays stored response', $replay['conflict'] === false && $replay['replayed'] === true && $replay['accepted'] === 1, true);
$count = (int)$fixtureDb->query('SELECT SUM(n) FROM counters')->fetchColumn();
checkBatch('retry does not double-count', $count === 7, true);

$changed = $body;
$changed['counters'][0]['n'] = 8;
$conflict = counterApplyBatchOnce('p1', '0123456789abcdef', counterBatchPayloadHash($changed), str_repeat('c', 32), '2026-09-04', 'android', '2.4', '', $changed['counters']);
checkBatch('changed payload under same id conflicts', $conflict['conflict'] === true, true);

$invalidItems = [
    ['m' => 'not_a_metric', 'd' => [], 'n' => 1, 'u' => 1],
    ['m' => 'event', 'd' => ['name' => 'valid_sibling'], 'n' => 10, 'u' => 1],
];
$invalidBody = $body;
$invalidBody['counters'] = $invalidItems;
$atomic = counterApplyBatchOnce('p1', 'abcdef0123456789', counterBatchPayloadHash($invalidBody), str_repeat('d', 32), '2026-09-04', 'android', '2.4', '', $invalidItems);
checkBatch('invalid batch accepted no cells and quarantined all items', $atomic['accepted'] === 0 && $atomic['quarantined'] === 2, true);
$validSibling = $fixtureDb->query("SELECT COUNT(*) FROM counters WHERE dims LIKE '%valid_sibling%'")->fetchColumn();
checkBatch('invalid batch leaves valid sibling unapplied', (int)$validSibling === 0, true);

$peopleDims = ['scope_kind' => 'project', 'scope_key' => 'all', 'days' => '7', 'end_day' => '2026-09-04'];
for ($i = 0; $i < 4; $i++) {
    counterUpsert('p1', '2026-09-04', 'unique_window_v2', $peopleDims, 'android', '2.4', '', 1, true);
}
checkBatch('fixed-window people count stays hidden below five witnesses', counterReadCells('p1', 'unique_window_v2', '2026-08-29', '2026-09-04') === [], true);
counterUpsert('p1', '2026-09-04', 'unique_window_v2', $peopleDims, 'android', '2.4', '', 1, true);
$peopleReport = counterReadCells('p1', 'unique_window_v2', '2026-08-29', '2026-09-04');
checkBatch('fixed-window people count appears at five witnesses', count($peopleReport) === 1 && $peopleReport[0]['n'] === 5, true);
checkBatch('fixed-window country projection reports Unknown rather than inferring location', counterReadUniqueWindowByFacet('p1', '2026-08-29', '2026-09-04', 7, '2026-09-04', 'country') === [['name' => 'unknown', 'count' => 5]], true);
checkBatch('fixed-window platform projection keeps its own gated count', counterReadUniqueWindowByFacet('p1', '2026-08-29', '2026-09-04', 7, '2026-09-04', 'platform') === [['name' => 'android', 'count' => 5]], true);
