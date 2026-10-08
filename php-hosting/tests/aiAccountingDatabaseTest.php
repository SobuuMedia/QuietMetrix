<?php
$testHost=getenv('BOOTSTRAP_TEST_HOST');
if ($testHost===false || $testHost==='') { echo "skipped: set BOOTSTRAP_TEST_HOST to run (needs a real MySQL)\n"; exit(0); }
define('DB_HOST',$testHost); define('DB_NAME',getenv('BOOTSTRAP_TEST_NAME') ?: 'qm_ai_test'); define('DB_USER',getenv('BOOTSTRAP_TEST_USER') ?: 'root'); define('DB_PASS',getenv('BOOTSTRAP_TEST_PASS') ?: ''); define('DB_CHARSET','utf8mb4');
require_once __DIR__.'/../src/db.php'; require_once __DIR__.'/../src/ai.php';
/** Database expectations cover policy without requiring live AI credentials. */
function dbExpect(bool $ok): void { if (!$ok) throw new RuntimeException('AI ledger assertion failed'); }
$db=getDb();
foreach (explode(';',file_get_contents(__DIR__.'/../ai-schema.sql')) as $statement) if (trim($statement)!=='') $db->exec($statement);
// Only AI tables in an explicitly selected test database are reset.
foreach (['ai_summary_cache','ai_operations','ai_monthly_usage','ai_assignments','ai_plans'] as $table) $db->exec('DELETE FROM '.$table);
$config=aiConfig(['QM_PROFILE'=>'cloud','QM_AI_MODEL'=>'fixture','QM_AI_API_KEY'=>'fixture','QM_AI_INPUT_MICRO_USD_PER_MILLION'=>'100000','QM_AI_OUTPUT_MICRO_USD_PER_MILLION'=>'200000','QM_AI_CACHE_MICRO_USD_PER_MILLION'=>'50000']);
aiQuery($db,'INSERT INTO ai_plans VALUES(?,?,?,?)',['small',1,3,1000000000]);
aiQuery($db,'INSERT INTO ai_assignments VALUES(?,?,?,?)',['owner','small',1,null]);
$children=[]; $dir=sys_get_temp_dir().'/qm-ai-'.bin2hex(random_bytes(6)); mkdir($dir);
for ($i=0;$i<24;$i++) {
    $process=proc_open([PHP_BINARY,__DIR__.'/aiReservationWorker.php',(string)$i],[1=>['file',$dir.'/'.$i,'w'],2=>['file',$dir.'/'.$i.'.err','w']],$pipes);
    $children[]=$process;
}
foreach ($children as $process) dbExpect(proc_close($process)===0);
$success=0;for($i=0;$i<24;$i++) { $success+=trim(file_get_contents($dir.'/'.$i))==='reserved' ? 1 : 0; unlink($dir.'/'.$i);unlink($dir.'/'.$i.'.err'); }rmdir($dir);
dbExpect($success===3);
$usage=aiQuery($db,'SELECT * FROM ai_monthly_usage WHERE account_id=?',['owner'])->fetch(); dbExpect((int)$usage['generations']===3);
$configEnv=['QM_PROFILE'=>'cloud','QM_AI_MODEL'=>'fixture','QM_AI_API_KEY'=>'fixture','QM_AI_INPUT_MICRO_USD_PER_MILLION'=>'100000','QM_AI_OUTPUT_MICRO_USD_PER_MILLION'=>'200000','QM_AI_CACHE_MICRO_USD_PER_MILLION'=>'50000'];
foreach($configEnv as $k=>$v) putenv($k.'='.$v);
dbExpect(aiCapabilities('owner')['reason']==='ai_allowance_exhausted');
aiQuery($db,'INSERT INTO ai_plans VALUES(?,?,?,?)',['large',1,10,1000000000]);aiQuery($db,'UPDATE ai_assignments SET plan_id=? WHERE account_id=?',['large','owner']);
dbExpect(aiCapabilities('owner')['available']);
$id=hash('sha256','retry');aiReserve('owner','p',$id,'same',null,$config);
try { aiReserve('owner','p',$id,'same',null,$config); throw new LogicException('Expected pending'); } catch(RuntimeException $e) { dbExpect($e->getMessage()==='ai_operation_pending'); }
$answer=['answer'=>'observed','sources'=>['sessions']];aiFinish($id,$answer,15,null);dbExpect(aiReserve('owner','p',$id,'same',null,$config)===$answer);
try { aiReserve('owner','p',$id,'different',null,$config); throw new LogicException('Expected conflict'); } catch(RuntimeException $e) { dbExpect($e->getMessage()==='ai_operation_conflict'); }
$failed=hash('sha256','failed');aiReserve('owner','p',$failed,'failure',null,$config);aiFinish($failed,null,42,null);
$before=(int)aiQuery($db,'SELECT cost FROM ai_monthly_usage WHERE account_id=?',['owner'])->fetchColumn();
$unknown=hash('sha256','unknown');aiReserve('owner','p',$unknown,'unknown',null,$config);aiFinish($unknown,null,null,null);
dbExpect((int)aiQuery($db,'SELECT cost FROM ai_monthly_usage WHERE account_id=?',['owner'])->fetchColumn()===$before+aiMaximumCost($config));
$cached=hash('sha256','cached');aiReserve('owner','p',$cached,'summary','cache-key',$config);aiFinish($cached,$answer,0,'cache-key');
aiQuery($db,'UPDATE ai_assignments SET plan_id=? WHERE account_id=?',['small','owner']);dbExpect(aiCached('owner','cache-key',$config)===$answer);dbExpect(!aiCapabilities('owner')['available']);
aiQuery($db,'UPDATE ai_assignments SET expires_at=? WHERE account_id=?',[time()-1,'owner']);dbExpect(!aiCapabilities('owner')['enabled']);dbExpect(aiCached('owner','cache-key',$config)===null);
aiQuery($db,'INSERT INTO ai_assignments VALUES(?,?,?,?)',['calendar','small',1,null]);
aiReserve('calendar','p',hash('sha256','dec'),'dec',null,$config,strtotime('2026-12-31T23:59:59Z'));
aiReserve('calendar','p',hash('sha256','jan'),'jan',null,$config,strtotime('2027-01-01T00:00:00Z'));
aiFinish(hash('sha256','dec'),null,7,null);
dbExpect((int)aiQuery($db,'SELECT generations FROM ai_monthly_usage WHERE account_id=? AND month=?',['calendar','2026-12-01'])->fetchColumn()===0);
dbExpect((int)aiQuery($db,'SELECT generations FROM ai_monthly_usage WHERE account_id=? AND month=?',['calendar','2027-01-01'])->fetchColumn()===1);
aiQuery($db,'INSERT INTO ai_assignments VALUES(?,?,?,?)',['recover','large',1,null]);
aiReserve('recover','p',hash('sha256','old'),'old',null,$config,time()-180);aiReserve('recover','p',hash('sha256','new'),'new',null,$config);
dbExpect((int)aiQuery($db,'SELECT generations FROM ai_monthly_usage WHERE account_id=?',['recover'])->fetchColumn()===1);
dbExpect((int)aiQuery($db,'SELECT cost FROM ai_monthly_usage WHERE account_id=?',['recover'])->fetchColumn()===2*aiMaximumCost($config));
$self=$config;$self['cloud']=false;for($i=0;$i<25;$i++)aiReserve('self','p',hash('sha256','self'.$i),(string)$i,null,$self);
dbExpect((int)aiQuery($db,'SELECT generations FROM ai_monthly_usage WHERE account_id=?',['self'])->fetchColumn()===25);
echo "MySQL concurrency and AI accounting passed\n";

aiQuery($db,'INSERT INTO ai_assignments VALUES(?,?,?,?)',['ai-owner','large',1,null]);
$snapshot=['anchor'=>gmdate('Y-m-d'),'sessions'=>['current'=>['total'=>20]]];
$request=['question'=>'sessions','operation_id'=>'fixture-ask','range'=>'7d','language'=>'en'];
$calls=0; $followup='';
$tool='{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"1","type":"function","function":{"name":"sessions","arguments":"{\"project_id\":\"other\"}"}},{"id":"2","type":"function","function":{"name":"sql","arguments":"{}"}}]}}],"usage":{"prompt_tokens":100,"completion_tokens":20}}';
$summary='{"choices":[{"message":{"role":"assistant","content":"Observed 20 sessions."}}],"usage":{"prompt_tokens":100,"completion_tokens":20}}';
$transport=static function($url,$headers,$body,$seconds) use (&$calls,&$followup,$tool,$summary): array { $calls++;if($calls===1)return[200,$tool];$followup=json_encode($body);return[200,$summary]; };
$answer=aiGenerate('ai-owner','p',$request,false,$snapshot,'computed',$transport);
dbExpect($calls===2 && $answer['sources']===[] && str_contains($followup,'unsupported_tool_or_arguments') && !str_contains($followup,'"total":20'));
dbExpect(aiGenerate('ai-owner','p',$request,false,$snapshot,'computed',$transport)===$answer && $calls===2);
$forbiddenCalls=0;
$forbiddenTransport=static function() use (&$forbiddenCalls): array { $forbiddenCalls++;throw new LogicException('Provider must not be called'); };
try { aiGenerate('unassigned','p',$request,false,$snapshot,'computed',$forbiddenTransport); throw new LogicException('Unassigned accepted'); } catch(RuntimeException $e) { dbExpect($e->getMessage()==='ai_unavailable'); }
dbExpect(aiGenerate('unassigned','p',$request,true,$snapshot,'computed',$forbiddenTransport)['mode']==='computed' && $forbiddenCalls===0);
$fallCalls=0;
$fallbackTransport=static function($url,$headers,$body,$seconds) use (&$fallCalls,$summary): array { $fallCalls++;if($fallCalls===1)return[400,'tools unsupported'];dbExpect(!isset($body['tools']) && str_contains(json_encode($body),'Reportable aggregate snapshot'));return[200,$summary]; };
$request['operation_id']='fallback-op';$answer=aiGenerate('ai-owner','p',$request,false,$snapshot,'computed',$fallbackTransport);dbExpect($fallCalls===2 && $answer['sources']===['sessions']);
$roundCalls=0;
$roundTool=str_replace('{\"project_id\":\"other\"}','{}',$tool);
$roundTransport=static function() use (&$roundCalls,$roundTool): array { $roundCalls++;return[200,$roundTool]; };
$request['operation_id']='round-limit';
try { aiGenerate('ai-owner','p',$request,false,$snapshot,'computed',$roundTransport);throw new LogicException('Unbounded rounds'); } catch(RuntimeException $e) { dbExpect($e->getMessage()==='ai_provider_failed'); }
dbExpect($roundCalls===4);
$request['operation_id']='cached-insight';$summaryCalls=0;
$summaryTransport=static function() use (&$summaryCalls,$summary): array { $summaryCalls++;return[200,$summary]; };
dbExpect(aiGenerate('ai-owner','p',$request,true,$snapshot,'computed',$summaryTransport)['mode']==='ai');
dbExpect(aiGenerate('ai-owner','p',$request,true,$snapshot,'computed',$summaryTransport)['mode']==='ai' && $summaryCalls===1);
echo "PHP AI service fixtures passed\n";

aiQuery($db,'INSERT INTO ai_plans VALUES(?,?,?,?)',['cost-concurrent',1,1000,3*aiMaximumCost($config)]);
aiQuery($db,'INSERT INTO ai_assignments VALUES(?,?,?,?)',['cost-concurrent','cost-concurrent',1,null]);
$dir=sys_get_temp_dir().'/qm-ai-cost-'.bin2hex(random_bytes(6));mkdir($dir);$workers=[];
for($i=0;$i<24;$i++)$workers[]=proc_open([PHP_BINARY,__DIR__.'/aiReservationWorker.php',(string)$i,'cost-concurrent'],[1=>['file',$dir.'/'.$i,'w'],2=>['file',$dir.'/'.$i.'.err','w']],$pipes);
foreach($workers as $worker)dbExpect(proc_close($worker)===0);
$count=0;for($i=0;$i<24;$i++){if(trim(file_get_contents($dir.'/'.$i))==='reserved')$count++;unlink($dir.'/'.$i);unlink($dir.'/'.$i.'.err');}rmdir($dir);dbExpect($count===3);
aiQuery($db,'INSERT INTO ai_plans VALUES(?,?,?,?)',['many',1,1000,1000000000]);aiQuery($db,'INSERT INTO ai_assignments VALUES(?,?,?,?)',['minute','many',1,null]);
for($i=0;$i<20;$i++)aiReserve('minute','p',hash('sha256','minute'.$i),(string)$i,null,$config);
try{aiReserve('minute','p',hash('sha256','minute21'),'21',null,$config);throw new LogicException('Minute limit exceeded');}catch(RuntimeException $e){dbExpect($e->getMessage()==='ai_rate_limited');}
aiQuery($db,'INSERT INTO ai_assignments VALUES(?,?,?,?)',['lease','many',1,null]);$lease=hash('sha256','lease1');aiReserve('lease','p',$lease,'f1','summary-lease',$config);
try{aiReserve('lease','p',hash('sha256','lease2'),'f2','summary-lease',$config);throw new LogicException('Duplicate lease accepted');}catch(RuntimeException $e){dbExpect($e->getMessage()==='ai_operation_pending');}
aiFinish($lease,$answer,0,'summary-lease');dbExpect(aiReserve('lease','p',hash('sha256','lease2'),'f2','summary-lease',$config)===$answer);
dbExpect((int)aiQuery($db,'SELECT generations FROM ai_monthly_usage WHERE account_id=?',['lease'])->fetchColumn()===1);
aiCleanup($db,time()+901);dbExpect((int)aiQuery($db,'SELECT COUNT(*) FROM ai_operations WHERE result IS NOT NULL')->fetchColumn()===0);
$db->exec('DROP TABLE ai_operations');$outageCalls=0;
$outageTransport=static function()use(&$outageCalls):array{$outageCalls++;throw new LogicException('Outage launched provider');};
$request['operation_id']='outage-fixture';
try{aiGenerate('ai-owner','p',$request,false,$snapshot,'computed',$outageTransport);throw new LogicException('Outage allowed AI');}catch(RuntimeException $e){dbExpect($e->getMessage()==='ai_accounting_unavailable');}
dbExpect(aiGenerate('ai-owner','p',$request,true,$snapshot,'computed',$outageTransport)['mode']==='computed'&&$outageCalls===0);
foreach(explode(';',file_get_contents(__DIR__.'/../ai-schema.sql'))as$statement)if(trim($statement)!=='')$db->exec($statement);
echo "MySQL cost concurrency, minute cap, leases, retention and outages passed\n";
aiQuery($db,'INSERT INTO ai_assignments VALUES(?,?,?,?)',['boundary-minute','many',1,null]);
$dir=sys_get_temp_dir().'/qm-ai-boundary-'.bin2hex(random_bytes(6));mkdir($dir);$workers=[];$boundary=strtotime('2026-10-31T23:59:59Z');
for($i=0;$i<30;$i++)$workers[]=proc_open([PHP_BINARY,__DIR__.'/aiReservationWorker.php',(string)$i,'boundary-minute',(string)($boundary+$i%2)],[1=>['file',$dir.'/'.$i,'w'],2=>['file',$dir.'/'.$i.'.err','w']],$pipes);
foreach($workers as $worker)dbExpect(proc_close($worker)===0);
$count=0;for($i=0;$i<30;$i++){if(trim(file_get_contents($dir.'/'.$i))==='reserved')$count++;unlink($dir.'/'.$i);unlink($dir.'/'.$i.'.err');}rmdir($dir);dbExpect($count===20);
echo "MySQL month-boundary account minute cap passed\n";
