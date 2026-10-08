<?php
/** Rollout parity: explicit choices, six statuses and original split preservation. */
require_once __DIR__ . '/../src/routes/experiments.php';
function rolloutCheck(bool $ok, string $label): void {
    if (!$ok) { fwrite(STDERR, "FAIL: $label\n"); exit(1); }
}
foreach (['active', 'paused', 'ended'] as $from) {
    rolloutCheck(experimentCanTransition($from, 'shipped'), "$from ships");
    rolloutCheck(experimentCanTransition($from, 'closed'), "$from closes");
}
foreach (['shipped', 'closed'] as $from) {
    foreach (['draft', 'active', 'paused', 'ended', 'shipped', 'closed'] as $to) {
        rolloutCheck(!experimentCanTransition($from, $to), "$from immutable");
    }
}
$row = ['experiment_key' => 'cta', 'status' => 'shipped', 'selected_variant' => 'b',
    'traffic_percent' => 30, 'variants' => '[{"name":"a","weight":40},{"name":"b","weight":60}]', 'countries' => '["ES"]'];
$config = experimentConfigResponse($row);
rolloutCheck($config['traffic_percent'] === 100 && $config['countries'] === null, 'full rollout');
rolloutCheck(array_column($config['variants'], 'weight') === [0, 100], 'selected allocation');
rolloutCheck($row['traffic_percent'] === 30, 'original preserved');
rolloutCheck(experimentSelectionValid($row, ['status' => 'shipped', 'selected_variant' => 'a']), 'baseline allowed');
rolloutCheck(!experimentSelectionValid($row, ['status' => 'shipped']), 'missing rejected');
rolloutCheck(!experimentSelectionValid($row, ['status' => 'shipped', 'selected_variant' => 'unknown']), 'undeclared rejected');
rolloutCheck(!experimentSelectionValid($row, ['status' => 'closed', 'selected_variant' => 'a']), 'closed has no choice');
echo "Rollout parity passed\n";

/** Route harness uses real SQL in SQLite; authentication is stubbed outside this lifecycle test. */
function getDb(): PDO { return $GLOBALS['rolloutDb']; }
function requireSession(): array { return []; }
function requireProjectAccess($session, $project): void {}
function requireRole($session, $roles): void {}
function getJsonBody(): array { return $GLOBALS['rolloutBody']; }
function jsonResponse(int $status, $body): void { $GLOBALS['rolloutResponse'] = [$status, $body]; }
function errorResponse(int $status, string $code, string $message): void { $GLOBALS['rolloutResponse'] = [$status, $code]; }
function now(): string {
    if ($GLOBALS['rolloutRace'] ?? false) {
        $GLOBALS['rolloutRace'] = false;
        $target = $GLOBALS['rolloutRaceStatus'] ?? 'closed';
        getDb()->prepare("UPDATE experiments SET status = ?, selected_variant = ? WHERE experiment_key = 'cta'")
            ->execute([$target, $target === 'shipped' ? 'b' : null]);
    }
    return '2026-10-03T12:00:00';
}
$GLOBALS['rolloutDb'] = new PDO('sqlite::memory:');
getDb()->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
getDb()->exec("CREATE TABLE experiments (project_id TEXT, experiment_key TEXT, name TEXT, screen TEXT, status TEXT, selected_variant TEXT, traffic_percent INTEGER, variants TEXT, countries TEXT, mode TEXT DEFAULT 'legacy', placement_key TEXT, revision INTEGER DEFAULT 0, b_percent INTEGER, languages TEXT, match_mode TEXT DEFAULT 'all', goal_event TEXT, icon_key TEXT, created_at TEXT, updated_at TEXT, started_at TEXT, ended_at TEXT)");
getDb()->prepare('INSERT INTO experiments (project_id, experiment_key, name, screen, status, selected_variant, traffic_percent, variants, countries, created_at, updated_at, started_at, ended_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')->execute([
    'p', 'cta', 'CTA', null, 'active', null, 30, $row['variants'], $row['countries'], 'created', 'updated', 'started', null,
]);
$GLOBALS['rolloutBody'] = ['status' => 'shipped', 'selected_variant' => 'b'];
handleExperimentUpdate('p', 'cta');
rolloutCheck($GLOBALS['rolloutResponse'][0] === 200, 'route ships');
$stored = findExperimentRow('p', 'cta');
rolloutCheck($stored['selected_variant'] === 'b' && $stored['status'] === 'shipped', 'selection persisted with status');
rolloutCheck((int)$stored['traffic_percent'] === 30 && $stored['variants'] === $row['variants'], 'SQL preserves allocation');
$GLOBALS['rolloutBody'] = [];
handleExperimentUpdate('p', 'cta');
rolloutCheck($GLOBALS['rolloutResponse'][0] === 409, 'terminal empty patch rejected');
getDb()->exec("UPDATE experiments SET status = 'active', selected_variant = NULL WHERE experiment_key = 'cta'");
$GLOBALS['rolloutBody'] = ['status' => 'shipped', 'selected_variant' => 'b'];
$GLOBALS['rolloutRace'] = true;
handleExperimentUpdate('p', 'cta');
rolloutCheck($GLOBALS['rolloutResponse'][0] === 409, 'stale prior status rejected');
$stored = findExperimentRow('p', 'cta');
rolloutCheck($stored['status'] === 'closed' && $stored['selected_variant'] === null, 'race cannot persist partial choice');
echo "Rollout SQL route checks passed\n";
getDb()->exec("UPDATE experiments SET status = 'active', selected_variant = NULL WHERE experiment_key = 'cta'");
$GLOBALS['rolloutBody'] = ['name' => 'Renamed'];
$GLOBALS['rolloutRaceStatus'] = 'shipped';
$GLOBALS['rolloutRace'] = true;
handleExperimentUpdate('p', 'cta');
rolloutCheck($GLOBALS['rolloutResponse'][0] === 409, 'field-only stale prior status rejected');
rolloutCheck(findExperimentRow('p', 'cta')['name'] === 'CTA', 'racing rename cannot mutate terminal row');
rolloutCheck(findExperimentRow('p', 'cta')['status'] === 'shipped', 'concurrent shipment remains intact');
getDb()->exec("UPDATE experiments SET status = 'active' WHERE experiment_key = 'cta'");
// SQLite normally counts matched rows; emulate MySQL's unchanged-row zero count.
getDb()->exec("CREATE TEMP TRIGGER unchanged_update BEFORE UPDATE ON experiments WHEN NEW.name = OLD.name BEGIN SELECT RAISE(IGNORE); END");
$GLOBALS['rolloutBody'] = ['name' => 'CTA'];
handleExperimentUpdate('p', 'cta');
rolloutCheck($GLOBALS['rolloutResponse'][0] === 200, 'same-value zero-row patch succeeds when status unchanged');
getDb()->exec('DROP TRIGGER unchanged_update');
echo "Field-only race and zero-change checks passed\n";
