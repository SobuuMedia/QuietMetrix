<?php
require_once __DIR__ . '/../src/askAnalyticsAnalyze.php';
require_once __DIR__ . '/../src/sessionCounterAnalyze.php';
require_once __DIR__ . '/../src/searchCounterAnalyze.php';
require_once __DIR__ . '/../src/overviewCounterAnalyze.php';
require_once __DIR__ . '/../src/retentionCounterAnalyze.php';

function assertAsk(bool $condition, string $message): void {
    if (!$condition) { fwrite(STDERR, "FAIL: $message\n"); exit(1); }
    echo "  ok: $message\n";
}

$answer = askAnalyticsAnalyze('How many sessions?', [['dims' => ['bucket' => '0_10s'], 'n' => 12]], [], [], [], [], '7d');
assertAsk(str_contains($answer['answer'], '12 sessions'), 'answers from the sessions aggregate');
assertAsk(str_contains($answer['source'], 'Sessions'), 'includes its aggregate source');

$unknown = askAnalyticsAnalyze('Why did revenue fall?', [], [], [], [], [], '30d');
assertAsk(str_contains($unknown['answer'], "can't establish causes"), 'does not invent causal answers');
assertAsk(str_contains($unknown['answer'], 'Day 1 retention'), 'describes supported question scope');

echo "all ask-analytics assertions passed\n";
