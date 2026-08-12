<?php

/**
 * Unit tests for funnelAnalyze() — mirrors
 * servers/ktor/.../funnels/FunnelAnalyzer.kt. Keep the two in sync; the server-to-server
 * verification step diffs a Ktor and PHP /results response for the same seed data.
 *
 * Run: php php-hosting/tests/funnelAnalyzeTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/funnelAnalyze.php';

$failures = 0;
function check(string $label, $expected, $actual): void {
    global $failures;
    if ($expected === $actual) {
        echo "  ok: $label\n";
        return;
    }
    $failures++;
    echo "FAIL: $label\n";
    echo '    expected: ' . var_export($expected, true) . "\n";
    echo '    actual:   ' . var_export($actual, true) . "\n";
}

function ts(int $seconds): string {
    return gmdate('Y-m-d\TH:i:s\Z', $seconds);
}

function row(string $actor, string $event, int $seconds, ?string $screen = null, ?string $platform = null): array {
    return ['actor_key' => $actor, 'event_name' => $event, 'ts' => ts($seconds), 'screen' => $screen, 'props' => [], 'platform' => $platform];
}

$twoSteps = [
    ['key' => 'view', 'event' => 'screen_view', 'screen' => 'signup'],
    ['key' => 'submit', 'event' => 'signup_submitted'],
];

// --- basic entered/converted/per-step conversion --------------------------------------
$result = funnelAnalyze($twoSteps, 3600, [
    row('A', 'screen_view', 0, 'signup'),
    row('A', 'signup_submitted', 10),
    row('B', 'screen_view', 0, 'signup'),
], ts(-1), ts(1000));

check('entered', 2, $result['entered']);
check('converted', 1, $result['converted']);
check('overall_conversion', 0.5, $result['overall_conversion']);
check('step 0 count', 2, $result['steps'][0]['count']);
check('step 0 conversion_from_entry', 1.0, $result['steps'][0]['conversion_from_entry']);
check('step 0 has no conversion_from_previous', 0.0, $result['steps'][0]['conversion_from_previous']);
check('step 0 dropped', 0, $result['steps'][0]['dropped']);
check('step 1 count', 1, $result['steps'][1]['count']);
check('step 1 conversion_from_entry', 0.5, $result['steps'][1]['conversion_from_entry']);
check('step 1 conversion_from_previous', 0.5, $result['steps'][1]['conversion_from_previous']);
check('step 1 dropped', 1, $result['steps'][1]['dropped']);
check('step 1 drop_rate', 0.5, $result['steps'][1]['drop_rate']);

// --- an actor with no matching events at all is not counted ------------------------------
$result = funnelAnalyze($twoSteps, 3600, [row('A', 'screen_view', 0, 'signup')], ts(-1), ts(1000));
check('single actor entered', 1, $result['entered']);

// --- entry outside the requested range is excluded entirely ------------------------------
$result = funnelAnalyze($twoSteps, 3600, [
    row('early', 'screen_view', 0, 'signup'),
    row('early', 'signup_submitted', 50),
    row('in-range', 'screen_view', 100, 'signup'),
    row('in-range', 'signup_submitted', 110),
], ts(90), ts(1000));
check('entry-range filter: entered', 1, $result['entered']);
check('entry-range filter: converted', 1, $result['converted']);

// --- median/p90 time to convert -----------------------------------------------------------
$result = funnelAnalyze($twoSteps, 3600, [
    row('A', 'screen_view', 0, 'signup'), row('A', 'signup_submitted', 10),
    row('B', 'screen_view', 0, 'signup'), row('B', 'signup_submitted', 20),
    row('C', 'screen_view', 0, 'signup'), row('C', 'signup_submitted', 30),
], ts(-1), ts(1000));
check('median_total_ms', 20000, $result['median_total_ms']);
check('step 1 median_ms_from_previous', 20000, $result['steps'][1]['median_ms_from_previous']);

// --- step 0 never has a time-from-previous -------------------------------------------------
$result = funnelAnalyze($twoSteps, 3600, [row('A', 'screen_view', 0, 'signup'), row('A', 'signup_submitted', 10)], ts(-1), ts(1000));
check('step 0 median_ms_from_previous is null', null, $result['steps'][0]['median_ms_from_previous']);
check('step 0 p90_ms_from_previous is null', null, $result['steps'][0]['p90_ms_from_previous']);

// --- breakdown groups actors by their entry event's dimension value ------------------------
$result = funnelAnalyze($twoSteps, 3600, [
    row('A', 'screen_view', 0, 'signup', 'android'), row('A', 'signup_submitted', 10),
    row('B', 'screen_view', 0, 'signup', 'ios'),
    row('C', 'screen_view', 0, 'signup', 'android'), row('C', 'signup_submitted', 10),
], ts(-1), ts(1000), 'platform');

$byValue = [];
foreach ($result['breakdown']['values'] as $v) { $byValue[$v['value']] = $v; }
check('breakdown dimension name', 'platform', $result['breakdown']['dimension']);
check('android entered', 2, $byValue['android']['entered']);
check('android overall_conversion', 1.0, $byValue['android']['overall_conversion']);
check('ios entered', 1, $byValue['ios']['entered']);
check('ios overall_conversion', 0.0, $byValue['ios']['overall_conversion']);

// --- no breakdown requested means no breakdown key -------------------------------------------
$result = funnelAnalyze($twoSteps, 3600, [row('A', 'screen_view', 0, 'signup')], ts(-1), ts(1000));
check('no breakdown requested', null, $result['breakdown']);

// --- trend buckets actors by the day of their entry -------------------------------------------
$dayZero = 0;
$dayOne = 86400;
$result = funnelAnalyze($twoSteps, 3600, [
    row('A', 'screen_view', $dayZero, 'signup'), row('A', 'signup_submitted', $dayZero + 10),
    row('B', 'screen_view', $dayOne, 'signup'),
], ts(-1), ts($dayOne + 1000), null, true);

check('trend has 2 buckets', 2, count($result['trend']));
check('trend day 0 entered', 1, $result['trend'][0]['entered']);
check('trend day 0 converted', 1, $result['trend'][0]['converted']);
check('trend day 1 entered', 1, $result['trend'][1]['entered']);
check('trend day 1 converted', 0, $result['trend'][1]['converted']);

// --- no trend requested means no trend key ------------------------------------------------------
$result = funnelAnalyze($twoSteps, 3600, [row('A', 'screen_view', 0, 'signup')], ts(-1), ts(1000));
check('no trend requested', null, $result['trend']);

// --- counted_by -------------------------------------------------------------------------------------
$result = funnelAnalyze($twoSteps, 3600, [row('install_hash_abc', 'screen_view', 0, 'signup')], ts(-1), ts(1000));
check('counted_by install', 'install', $result['counted_by']);

$result = funnelAnalyze($twoSteps, 3600, [row('sid:abc123', 'screen_view', 0, 'signup')], ts(-1), ts(1000));
check('counted_by session', 'session', $result['counted_by']);

$result = funnelAnalyze($twoSteps, 3600, [
    row('install_hash_abc', 'screen_view', 0, 'signup'),
    row('sid:xyz', 'screen_view', 0, 'signup'),
], ts(-1), ts(1000));
check('counted_by mixed', 'mixed', $result['counted_by']);

// Attempts must remain separate even when a session starts two flows for different books.
$attemptRows = [
    array_merge(row('sid:s1', 'screen_view', 0, 'signup'), ['session_id' => 's1', 'props' => ['book_id' => 'a']]),
    array_merge(row('sid:s1', 'signup_submitted', 1), ['session_id' => 's1', 'props' => ['book_id' => 'a']]),
    array_merge(row('sid:s1', 'screen_view', 2, 'signup'), ['session_id' => 's1', 'props' => ['book_id' => 'b']]),
];
$result = funnelAnalyze($twoSteps, 3600, $attemptRows, ts(-1), ts(1000), null, false, 'attempt', 'session', 'book_id');
check('attempt mode keeps separate entries', 2, $result['entered']);
check('attempt mode only counts the completed attempt', 1, $result['converted']);
check('attempt mode reports session attempts', 'session attempt', $result['counted_by']);

// --- empty input --------------------------------------------------------------------------------------
$result = funnelAnalyze($twoSteps, 3600, [], ts(-1), ts(1000));
check('empty input entered', 0, $result['entered']);
check('empty input converted', 0, $result['converted']);
check('empty input overall_conversion', 0.0, $result['overall_conversion']);
check('empty input step counts are zero', true, $result['steps'][0]['count'] === 0 && $result['steps'][1]['count'] === 0);

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all funnel-analyze assertions passed\n";
