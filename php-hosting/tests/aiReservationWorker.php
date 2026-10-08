<?php
if (!isset($argv[1])) exit(0);
define('DB_HOST',getenv('BOOTSTRAP_TEST_HOST'));define('DB_NAME',getenv('BOOTSTRAP_TEST_NAME'));define('DB_USER',getenv('BOOTSTRAP_TEST_USER'));define('DB_PASS',getenv('BOOTSTRAP_TEST_PASS'));define('DB_CHARSET','utf8mb4');
require_once __DIR__.'/../src/db.php';require_once __DIR__.'/../src/ai.php';
$config=aiConfig(['QM_PROFILE'=>'cloud','QM_AI_MODEL'=>'fixture','QM_AI_API_KEY'=>'fixture','QM_AI_INPUT_MICRO_USD_PER_MILLION'=>'100000','QM_AI_OUTPUT_MICRO_USD_PER_MILLION'=>'200000','QM_AI_CACHE_MICRO_USD_PER_MILLION'=>'50000']);
$account=$argv[2] ?? 'owner';
try { aiReserve($account,'project-'.((int)$argv[1]%3),hash('sha256',$account.'op'.$argv[1]),hash('sha256','fp'.$argv[1]),null,$config,isset($argv[3]) ? (int)$argv[3] : null);echo 'reserved'; }
catch(RuntimeException $e) { if (!in_array($e->getMessage(),['ai_allowance_exhausted','ai_rate_limited'],true)) throw $e;echo 'denied'; }
