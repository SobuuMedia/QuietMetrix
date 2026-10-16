<?php
require_once __DIR__ . '/../ai.php';
require_once __DIR__ . '/dashboard.php';
require_once __DIR__ . '/funnels.php';
require_once __DIR__ . '/experiments.php';

/** AI project authorization never lets a hosted customer admin cross account boundaries. */
function aiAuthorizedAccount(string $projectId): string {
    $session = requireSessionOrToken();
    if (!sessionCanReadAnalytics($session)) { errorResponse(403,'forbidden','This token cannot read analytics'); exit; }
    $db = getDb(); $project = aiQuery($db,'SELECT owner_user_id FROM projects WHERE id=?',[$projectId])->fetch();
    if (!$project) { errorResponse(404,'not_found','Project not found'); exit; }
    $owner = (string)$project['owner_user_id']; $user = (string)($session['sub'] ?? '');
    $member = aiQuery($db,'SELECT 1 FROM project_members WHERE project_id=? AND user_id=?',[$projectId,$user])->fetchColumn();
    $config=aiConfig();
    if ($owner !== $user && !$member && ($config['cloud'] || ($session['role'] ?? '') !== 'admin')) { errorResponse(403,'forbidden','No access to this project'); exit; }
    return $owner;
}

/** Reuses privacy-suppressed counters and filters retention cohorts to their reported window. */
function aiReportableCells(string $project, string $metric, array $window): array {
    $cells=counterReadCells($project,$metric,$window['from_day'],$window['to_day']); $result=[];
    foreach ($cells as $cell) {
        if (in_array($metric,['retention','activation'],true)) {
            $started=reportedCohortStart($cell['dims']['cohort'] ?? '');
            if ($started===null || $started<$window['from_day'] || $started>$window['to_day']) continue;
        }
        $result[]=['dims'=>$cell['dims'],'n'=>$cell['n']];
    }
    return $result;
}

/** Public AI endpoints expose only answers, aggregate sources and effective capabilities. */
function handleAi(string $project, string $action, bool $alias = false): void {
    $account=aiAuthorizedAccount($project);
    if ($action==='capabilities') { jsonResponse(200,aiCapabilities($account)); return; }
    try {
        $request=getJsonBody(); $range=$request['range'] ?? '7d';
        $ranges=['1h'=>3600,'1d'=>86400,'7d'=>604800,'30d'=>2592000,'90d'=>7776000];
        if (!is_string($range) || !isset($ranges[$range]) || isset($request['compare']) && !is_bool($request['compare'])) throw new InvalidArgumentException('Invalid date context');
        $selection = $request['context'] ?? [];
        if (!is_array($selection) || count($selection)>10) throw new InvalidArgumentException('Invalid selection');
        foreach ($selection as $key=>$value) if (!in_array($key,['screen','filter_kind','filter_value','node','funnel','revision','step','cohort','experiment'],true) || !is_string($value) || strlen($value)<1 || strlen($value)>128) throw new InvalidArgumentException('Invalid selection');
        $kind=$selection['filter_kind'] ?? 'platform'; $value=$selection['filter_value'] ?? 'all';
        if (!validReportFilterV2($kind,$value)) throw new InvalidArgumentException('Invalid filter');
        $window=analyticsWindow($ranges[$range],$request['period'] ?? null,$request['anchor'] ?? null);
        $report = $range === '1h' ? null : readProjectReportV2($project, ['range'=>$range==='1d'?'today':$range, 'anchor'=>$window['to_day'], 'compare'=>($request['compare'] ?? false)?'true':'false', 'filter_kind'=>$kind,'filter_value'=>$value]);
        $snapshot=['anchor'=>$request['anchor'] ?? $window['to_day']];
        $metrics=['aggregates'=>['event','value'],'sessions'=>['session'],'retention'=>['retention','activation'],'transitions'=>['screen_transition','screen_dwell'],'search'=>['search','search_zero_result'],'friction'=>['friction'],'funnels'=>['funnel_step'],'experiments'=>['experiment']];
        if (!$report || ($report['coverage']['source']==='legacy' && $kind==='platform' && $value==='all')) {
        foreach ($metrics as $name=>$reads) {
            $data=['from'=>$window['from_day'],'to'=>$window['to_day'],'current'=>[]];
            foreach ($reads as $metric) $data['current'][$metric]=aiReportableCells($project,$metric,$window);
            if ($request['compare'] ?? false) {
                $previous=analyticsWindow($ranges[$range],'previous',$window['to_day']);
                $data['previous_from']=$previous['from_day']; $data['previous_to']=$previous['to_day']; $data['previous']=[];
                foreach ($reads as $metric) $data['previous'][$metric]=aiReportableCells($project,$metric,$previous);
            }
            $snapshot[$name]=$data;
        }

        }
        if ($report && ($report['coverage']['source'] !== 'legacy' || $kind !== 'platform' || $value !== 'all')) {
            $snapshot = ['anchor'=>$window['to_day'],
                'aggregates'=>array_intersect_key($report,array_flip(['scope','coverage','generated_at','last_received_at','overview','comparison'])),
                'sessions'=>['visits'=>$report['overview']['visits'],'duration'=>$report['overview']['visit_duration']],
                'retention'=>['summary'=>aiVisibleRetentionSummary($report['retention_cohorts']),'cohorts'=>$report['retention_cohorts'],'previous_cohorts'=>$report['comparison_retention_cohorts']],
                'transitions'=>['scope'=>$selection,'state'=>'unsupported'],
            ];
            if ($kind==='platform') {
                $edges=counterReadTransitionsV2($project,$window['from_day'],$window['to_day'],$value);
                $snapshot['transitions']=['scope'=>$selection,'edges'=>array_values(array_filter($edges['cells'] ?? [],static fn($c)=>!isset($selection['node']) || ($c['dims']['from'] ?? null)===$selection['node']))];
            }
        }
        if (isset($selection['funnel'])) {
            $f=findFunnelRow($project,$selection['funnel']);
            if ($f) {
                $rev=isset($selection['revision']) ? (int)$selection['revision'] : (int)$f['revision'];
                $f=findFunnelRevision($project,$selection['funnel'],$rev);
                if ($f && $f['source']==='dashboard') {
                    $a=funnelCounterAnalyze(json_decode($f['steps'],true),readDashboardFunnelV2Steps($project,$f['funnel_key'],$rev,$window['from_day'],$window['to_day']),$f['count_mode']);
                    $snapshot['funnels']=['key'=>$f['funnel_key'],'revision'=>$rev,'from'=>$window['from_day'],'to'=>$window['to_day'],'steps'=>array_map(static fn($step)=>['key'=>$step['key'],'count'=>$step['count']],$a['steps'])];
                }
            }
        }
        if (isset($selection['experiment'])) {
            $e=findExperimentRow($project,$selection['experiment']);
            if ($e && in_array($e['mode'],['visibility','variants'],true)) {
                $from=substr($e['started_at'] ?? $e['created_at'],0,10); $to=gmdate('Y-m-d');
                $a=experimentCounterAnalyzeV2(['experiment_key'=>$e['experiment_key'],'variants'=>json_decode($e['variants'],true),'revision'=>(int)$e['revision']],
                    counterReadExperimentCellsV2($project,'experiment_exposure_v2',$from,$to,$e['experiment_key'],(int)$e['revision']),
                    counterReadExperimentCellsV2($project,'experiment_goal_v2',$from,$to,$e['experiment_key'],(int)$e['revision']));
                $snapshot['experiments']=['key'=>$e['experiment_key'],'revision'=>(int)$e['revision'],'basis'=>'since_revision_started','from'=>$from,'to'=>$to]+$a;
            }
        }
        $computed=isset($report['overview']['people']['value']) ? 'Recorded active installations: '.$report['overview']['people']['value'].'. Dates use UTC.' : 'There is not enough reportable data for the selected scope.';
        $result=aiGenerate($account,$project,$request,$action==='insight',$snapshot,$computed,requireProject:true);
        jsonResponse(200,$alias ? ['answer'=>$result['answer'],'source'=>implode(' · ',$result['sources'])] : $result);
    } catch (InvalidArgumentException $e) { errorResponse(400,'bad_request','Invalid AI request'); }
    catch (Throwable $e) {
        if ($action==='insight') { jsonResponse(200,['text'=>"There isn't enough reportable analytics data to summarize yet.",'mode'=>'computed','sources'=>[],'generated_at'=>gmdate('c')]); return; }
        $code=$e->getMessage();
        $status=match($code) { 'ai_unavailable'=>404,'ai_allowance_exhausted','ai_rate_limited'=>429,'ai_operation_pending','ai_operation_conflict','ai_operation_expired'=>409,default=>503 };
        if (!str_starts_with($code,'ai_')) $code='ai_provider_failed';
        errorResponse($status,$code,$code==='ai_allowance_exhausted' ? 'Your plan’s AI allowance has been reached.' : ($code==='ai_provider_failed' ? 'AI could not answer. Please retry.' : 'AI is unavailable.'));
    }
}

/** Operator access is exclusively a separate constant-time compared server credential. */
function handleAiOperator(string $method, string $resource, ?string $id): void {
    $config=aiConfig(); $supplied=$_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if ($config['operator_key']==='' || !hash_equals('Bearer '.$config['operator_key'],$supplied)) { errorResponse(401,'unauthorized','Operator credential required'); return; }
    $db=getDb();
    try {
        if ($method==='GET') {
            $rows=match($resource) { 'plans'=>aiQuery($db,'SELECT * FROM ai_plans')->fetchAll(),'assignments'=>aiQuery($db,'SELECT * FROM ai_assignments')->fetchAll(),'usage'=>$id===null ? throw new InvalidArgumentException() : aiQuery($db,'SELECT * FROM ai_monthly_usage WHERE account_id=?',[$id])->fetchAll(),default=>throw new InvalidArgumentException() };
            foreach ($rows as &$row) foreach ($row as $key=>&$value) {
                if (in_array($key,['active','ai_enabled'],true)) $value=(bool)$value;
                elseif (in_array($key,['generation_limit','cost_limit','cost','generations','expires_at'],true) && $value!==null) $value=(int)$value;
            }
            jsonResponse(200,$rows); return;
        }
        if ($method!=='PUT' || $id===null || !preg_match('/^[A-Za-z0-9_.-]{1,100}$/D',$id)) throw new InvalidArgumentException();
        $body=getJsonBody();
        if ($resource==='plans') {
            if (!is_bool($body['ai_enabled'] ?? null) || !is_int($body['generation_limit'] ?? null) || !is_int($body['cost_limit'] ?? null) || $body['generation_limit']<0 || $body['generation_limit']>1000000000 || $body['cost_limit']<0 || $body['cost_limit']>1000000000000) throw new InvalidArgumentException();
            aiQuery($db,'INSERT INTO ai_plans(plan_id,ai_enabled,generation_limit,cost_limit) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE ai_enabled=VALUES(ai_enabled),generation_limit=VALUES(generation_limit),cost_limit=VALUES(cost_limit)',[$id,(int)$body['ai_enabled'],$body['generation_limit'],$body['cost_limit']]);
        } elseif ($resource==='assignments') {
            if (!is_string($body['plan_id'] ?? null) || !is_bool($body['active'] ?? null) || isset($body['expires_at']) && !is_int($body['expires_at']) || !aiQuery($db,'SELECT 1 FROM ai_plans WHERE plan_id=?',[$body['plan_id']])->fetchColumn()) throw new InvalidArgumentException();
            aiQuery($db,'INSERT INTO ai_assignments(account_id,plan_id,active,expires_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE plan_id=VALUES(plan_id),active=VALUES(active),expires_at=VALUES(expires_at)',[$id,$body['plan_id'],(int)$body['active'],$body['expires_at'] ?? null]);
        } else throw new InvalidArgumentException();
        jsonResponse(200,['ok'=>true]);
    } catch (InvalidArgumentException $e) { errorResponse(400,'bad_request','Invalid operator request'); }
}

/** Uses the same eligible cohort weights as the dashboard; no hidden denominator is inferred. */
function aiVisibleRetentionSummary(array $cohorts): array {
    $summary=[];
    foreach ([1,3,7,14,30] as $age) {
        $size=0; $weighted=0.0; $count=0;
        foreach ($cohorts as $cohort) {
            $day=$cohort['days'][(string)$age] ?? [];
            if (($cohort['size']['value'] ?? 0)<=0 || !in_array($cohort['size']['state'],['reported','zero'],true) ||
                !in_array($day['state'] ?? '',['reported','zero'],true) || !isset($day['rate'])) continue;
            $size += $cohort['size']['value']; $weighted += $cohort['size']['value']*$day['rate']; $count += $day['value'] ?? 0;
        }
        $summary['day_'.$age]=$size ? ['value'=>$count,'rate'=>$weighted/$size,'contributors'=>$size,'state'=>'reported','unit'=>'installations','source'=>'v2']
            : ['value'=>null,'state'=>'insufficient_data','unit'=>'installations','source'=>'v2'];
    }
    return $summary;
}
