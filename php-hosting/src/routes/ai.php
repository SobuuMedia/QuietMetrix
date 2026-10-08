<?php
require_once __DIR__ . '/../ai.php';

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
        $window=analyticsWindow($ranges[$range],$request['period'] ?? null,$request['anchor'] ?? null);
        $snapshot=['anchor'=>$request['anchor'] ?? $window['to_day']];
        $metrics=['aggregates'=>['event','value'],'sessions'=>['session'],'retention'=>['retention','activation'],'transitions'=>['screen_transition','screen_dwell'],'search'=>['search','search_zero_result'],'friction'=>['friction'],'funnels'=>['funnel_step'],'experiments'=>['experiment']];
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
        $computed=askAnalyticsAnalyze('sessions',counterReadCells($project,'session',$window['from_day'],$window['to_day']),[],[],[],[],$range)['answer'];
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
