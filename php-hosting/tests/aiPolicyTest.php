<?php
require_once __DIR__ . '/../src/ai.php';
function check(bool $ok): void { if (!$ok) throw new RuntimeException('AI policy assertion failed'); }
check(!aiConfig([])['enabled']);
check(!aiConfig(['QM_AI_MODEL'=>'local','QM_AI_BASE_URL'=>'http://localhost:1234/v1'])['enabled']);
check(aiConfig(['QM_AI_MODEL'=>'local','QM_AI_BASE_URL'=>'http://localhost:1234/v1','QM_AI_LOCAL'=>'true'])['enabled']);
check(!aiConfig(['QM_PROFILE'=>'cloud','QM_AI_MODEL'=>'x','QM_AI_API_KEY'=>'secret'])['enabled']);
check(aiResetDate('2026-12-31') === '2027-01-01');
check(aiCost(['input'=>1000000,'output'=>1000000,'cache'=>0], ['input'=>100,'output'=>200,'cache'=>50]) === 300);
check(aiCost(['input'=>1000000,'output'=>0,'cache'=>500000], ['input'=>100,'output'=>200,'cache'=>50]) === 75);
check(aiCost(['input'=>1000000,'output'=>0,'cache'=>0,'cache_write'=>1000000], ['input'=>100,'output'=>200,'cache'=>50,'cache_write'=>125]) === 125);
check(aiMaximumCost(['rates'=>['input'=>1,'output'=>1,'cache'=>1,'cache_write'=>1]]) === 4);
echo "AI policy passed\n";
