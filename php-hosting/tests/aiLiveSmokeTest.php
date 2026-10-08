<?php
require_once __DIR__.'/../src/ai.php';
if (getenv('QM_AI_LIVE_SMOKE') !== 'true') { echo "skipped: opt in with QM_AI_LIVE_SMOKE=true and explicit provider/model/credentials\n"; exit(0); }
$config=aiConfig();
if (!$config['enabled']) throw new RuntimeException('Explicit valid provider configuration is required');
$system='Reply with plain text only. Do not use tools.';
$reply=aiProvider($config,$system,aiMessages($config,$system,[],'Say: smoke test passed'),false,60);
if ($reply['text']==='') throw new RuntimeException('Provider returned no text');
echo 'Live smoke passed: '.$config['provider'].' / '.$config['model']."\n";
