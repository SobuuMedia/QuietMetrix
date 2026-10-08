<?php
require_once __DIR__.'/../src/routes/ai.php';
require_once __DIR__.'/../src/counters.php';
require_once __DIR__.'/../src/analyticsWindow.php';
/** SQLite fixtures exercise the actual owner/membership SQL without HTTP or provider calls. */
function getDb(): PDO {
    static $db;
    if (!$db) { $db=new PDO('sqlite::memory:');$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE,PDO::FETCH_ASSOC);
        $db->exec('CREATE TABLE projects(id TEXT,owner_user_id TEXT); CREATE TABLE project_members(project_id TEXT,user_id TEXT);');
        $db->exec("INSERT INTO projects VALUES('p1','owner'),('p2','other'); INSERT INTO project_members VALUES('p1','member');"); }
    return $db;
}
/** Fixture principal supplies the same fields as a verified dashboard session. */
function requireSessionOrToken(): array { return $GLOBALS['aiTestSession']; }
/** Scope validation remains an independent authorization precondition. */
function sessionCanReadAnalytics(array $session): bool { return !isset($session['scopes']) || in_array('analytics:read',$session['scopes'],true); }
/** Converts a route denial into an assertable fixture exception. */
function errorResponse(int $status,string $code,string $message): void { throw new RuntimeException($status.' '.$code); }
/** Fixture assertion. */
function authExpect(bool $condition): void { if (!$condition) throw new LogicException('AI authorization failed'); }
putenv('QM_PROFILE=cloud');
foreach(['owner','member'] as $user) { $GLOBALS['aiTestSession']=['sub'=>$user,'role'=>'reviewer'];authExpect(aiAuthorizedAccount('p1')==='owner'); }
foreach(['admin','developer','reviewer'] as $role) {
    $GLOBALS['aiTestSession']=['sub'=>'other','role'=>$role];
    try { aiAuthorizedAccount('p1');throw new LogicException('Foreign project accessible'); } catch(RuntimeException $e) { authExpect($e->getMessage()==='403 forbidden'); }
}
$GLOBALS['aiTestSession']=['sub'=>'owner','scopes'=>['projects:read']];
try { aiAuthorizedAccount('p1');throw new LogicException('Missing scope accessible'); } catch(RuntimeException $e) { authExpect($e->getMessage()==='403 forbidden'); }
$GLOBALS['aiTestSession']=['sub'=>'owner','scopes'=>['analytics:read']];authExpect(aiAuthorizedAccount('p1')==='owner');
putenv('QM_PROFILE=selfhost');$GLOBALS['aiTestSession']=['sub'=>'other','role'=>'admin'];authExpect(aiAuthorizedAccount('p1')==='owner');
echo "AI account authorization fixtures passed\n";

$db=getDb();
$db->exec('CREATE TABLE counters(project_id TEXT,metric TEXT,day TEXT,dims_hash TEXT,dims TEXT,n INTEGER,devices INTEGER);');
$insert=$db->prepare('INSERT INTO counters VALUES(?,?,?,?,?,?,?)');
$insert->execute(['p1','session','2026-10-04','hidden',json_encode(['bucket'=>'0_10s']),999,4]);
$insert->execute(['p1','session','2026-10-04','visible',json_encode(['bucket'=>'10_30s']),5,5]);
$insert->execute(['p2','session','2026-10-04','foreign',json_encode(['bucket'=>'30_60s']),1000,5]);
$window=['from_day'=>'2026-10-04','to_day'=>'2026-10-04'];
$visible=aiReportableCells('p1','session',$window);
authExpect($visible===[['dims'=>['bucket'=>'10_30s'],'n'=>5]]);
authExpect(!str_contains(json_encode($visible),'devices') && !str_contains(json_encode($visible),'999'));
$insert->execute(['p1','retention','2026-10-04','oldcohort',json_encode(['cohort'=>'2026-09-01','day'=>'30']),5,5]);
authExpect(aiReportableCells('p1','retention',$window)===[]);
echo "AI database privacy fixtures passed\n";
