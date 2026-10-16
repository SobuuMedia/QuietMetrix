<?php
require_once __DIR__ . '/../src/counterRegistry.php';
require_once __DIR__ . '/../src/counters.php';
require_once __DIR__ . '/../src/routes/dashboard.php';

function projectionCheck(bool $ok, string $message): void {
    if (!$ok) { fwrite(STDERR, "  failed: {$message}\n"); exit(1); }
    echo "  ok: {$message}\n";
}

$base = ['metric'=>'event_v2','day'=>'2026-10-07','hour'=>-1,'dims_hash'=>str_repeat('a',64)];
$rows = [
    $base + ['platform'=>'android','segment_kind'=>'global','segment_value'=>'all','n'=>3],
    $base + ['platform'=>'android','segment_kind'=>'platform','segment_value'=>'android','n'=>3],
    ['metric'=>'event_v2','day'=>'2026-10-06','hour'=>-1,'dims_hash'=>str_repeat('b',64),'platform'=>'ios','segment_kind'=>'global','segment_value'=>'all','n'=>5],
];
projectionCheck(count(selectCounterProjectionRows($rows, 'all')) === 2, 'all-platform reads select only global rows');
$android = selectCounterProjectionRows($rows, 'android');
projectionCheck(count($android) === 1 && $android[0]['segment_kind'] === 'platform', 'platform reads select platform projection without summing global');
$ios = selectCounterProjectionRows($rows, 'ios');
projectionCheck(count($ios) === 1 && $ios[0]['segment_kind'] === 'global', 'old global row is retained as platform fallback');
$db = new PDO('sqlite::memory:');
$db->exec('CREATE TABLE counter_cells_v2 (project_id TEXT, metric TEXT, day TEXT, hour INTEGER, dims_hash TEXT, platform TEXT, segment_kind TEXT, segment_value TEXT, dims TEXT, n INTEGER, contributors INTEGER)');
$dims = ['name' => 'open']; $checked = counterV2Validate('event_v2', $dims); $today = new DateTimeImmutable('2026-10-07', new DateTimeZone('UTC'));
$witnessDims = json_encode(['target_metric'=>'event_v2','target_hash'=>$checked['hash'],'days'=>'7','end_day'=>'2026-10-07']);
$insert = $db->prepare('INSERT INTO counter_cells_v2 VALUES (?,?,?,?,?,?,?,?,?,?,?)');
$insert->execute(['1','witness_window_v2','2026-10-04',-1,str_repeat('c',64),'android','global','all',$witnessDims,5,0]);
$group = ['metric'=>'event_v2','day'=>'2026-10-07','hour'=>-1,'dims_hash'=>$checked['hash'],'platform'=>'android','segment_kind'=>'global','segment_value'=>'all','dims'=>json_encode($dims),'n'=>10,'contributors'=>0];
projectionCheck(reportMetricWitnessCountV2($db,'1','event_v2',$dims,$today->modify('-6 days'),$today,'all',[$group]) === 5, 'matching fixed-window witnesses are counted by exact target hash');
projectionCheck(reportMetricWitnessCountV2($db,'1','event_v2',['name'=>'close'],$today->modify('-6 days'),$today,'all',[$group]) === 0, 'witnesses for another metric group never unlock this group');
$legacy = [array_replace($group,['day'=>'2026-10-04','contributors'=>3]), array_replace($group,['day'=>'2026-10-05','contributors'=>3])];
projectionCheck(reportMetricWitnessCountV2($db,'2','event_v2',$dims,$today->modify('-6 days'),$today,'all',$legacy) === 3, 'legacy daily contributor counts are never summed across days');
$facetDb = new PDO('sqlite::memory:');
$facetDb->exec('CREATE TABLE counters (project_id TEXT, metric TEXT, day TEXT, dims TEXT, platform TEXT, country TEXT, n INTEGER, devices INTEGER)');
$facetDb->exec('CREATE TABLE counter_cells_v2 (project_id TEXT, metric TEXT, day TEXT, hour INTEGER, dims_hash TEXT, platform TEXT, country TEXT, app_version TEXT, segment_kind TEXT, segment_value TEXT, dims TEXT, n INTEGER, contributors INTEGER)');
$windowDims = json_encode(['scope_kind'=>'project','scope_key'=>'all','days'=>'7','end_day'=>'2026-10-07']);
$insertFacet = $facetDb->prepare('INSERT INTO counter_cells_v2 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)');
foreach ([['global','all'],['platform','android'],['country','FR'],['device_class','phone']] as [$kind,$value])
    $insertFacet->execute(['3','unique_window_v2','2026-10-07',-1,str_repeat('d',64),'android','FR','1.0',$kind,$value,$windowDims,5,5]);
$platformCounts = counterReadUniqueWindowByFacet('3','2026-10-01','2026-10-07',7,'2026-10-07','platform',5,$facetDb);
$countryCounts = counterReadUniqueWindowByFacet('3','2026-10-01','2026-10-07',7,'2026-10-07','country',5,$facetDb);
$deviceCounts = counterReadUniqueWindowByFacet('3','2026-10-01','2026-10-07',7,'2026-10-07','device_class',5,$facetDb);
projectionCheck($platformCounts === [['name'=>'android','count'=>5]], 'platform breakdown reads its projection once');
projectionCheck($countryCounts === [['name'=>'FR','count'=>5]], 'country breakdown uses app-provided country projection');
projectionCheck($deviceCounts === [['name'=>'phone','count'=>5]], 'device breakdown uses a coarse device-class projection');
echo "dashboardProjectionTest passed\n";

$selectedRows = [
 ['metric'=>'unique_window_v2','segment_kind'=>'global','segment_value'=>'all','dims'=>'{"scope_kind":"project","scope_key":"all"}','n'=>20],
 ['metric'=>'unique_window_v2','segment_kind'=>'country','segment_value'=>'DE','dims'=>'{"scope_kind":"project","scope_key":"all"}','n'=>6],
 ['metric'=>'unique_window_v2','segment_kind'=>'global','segment_value'=>'all','dims'=>'{"scope_kind":"screen","scope_key":"home"}','n'=>7],
];
projectionCheck(array_column(selectCounterProjectionRows($selectedRows,['country','DE']),'n') === [6], 'country report never includes global totals');
projectionCheck(array_column(selectCounterProjectionRows($selectedRows,['screen','home']),'n') === [7], 'screen report uses screen-unique scope only');
projectionCheck(selectCounterProjectionRows($selectedRows,['country','FR']) === [], 'absent country never falls back to global');
projectionCheck(!validReportFilterV2('screen','__exit__') && !validReportFilterV2('country','Germany'), 'invalid report selectors are rejected');
