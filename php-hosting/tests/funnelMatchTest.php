<?php

/**
 * Unit tests for funnelMatch()/funnelPercentile() — mirrors
 * servers/ktor/.../funnels/FunnelMatcher.kt and Percentile.kt. Keep the two in sync; the
 * server-to-server verification step diffs a Ktor and PHP /results response for the same
 * seed data, so these two implementations must agree bit-for-bit.
 *
 * Run: php php-hosting/tests/funnelMatchTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/funnelMatch.php';

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

function ev(string $name, int $seconds, ?string $screen = null, array $props = []): array {
    return ['event_name' => $name, 'ts' => ts($seconds), 'screen' => $screen, 'props' => $props];
}

$twoSteps = [
    ['key' => 'view', 'event' => 'screen_view', 'screen' => 'signup'],
    ['key' => 'submit', 'event' => 'signup_submitted'],
];

// --- happy path -------------------------------------------------------------
$result = funnelMatch($twoSteps, 3600, [
    ev('screen_view', 0, 'signup'),
    ev('signup_submitted', 10),
]);
check('happy path completes', true, $result['completed']);
check('happy path reaches both steps in order', ['view', 'submit'], array_column($result['reached'], 'step_key'));
check('happy path entry ts', ts(0), $result['reached'][0]['ts']);
check('happy path second step ts', ts(10), $result['reached'][1]['ts']);

// --- input order does not matter --------------------------------------------
$result = funnelMatch($twoSteps, 3600, [
    ev('signup_submitted', 10),
    ev('screen_view', 0, 'signup'),
]);
check('out-of-order input still completes', true, $result['completed']);
check('out-of-order input: entry ts is the earlier event', ts(0), $result['reached'][0]['ts']);

// --- window expiry ------------------------------------------------------------
$result = funnelMatch($twoSteps, 3600, [
    ev('screen_view', 0, 'signup'),
    ev('signup_submitted', 3601),
]);
check('window expiry by one second drops at the previous step', false, $result['completed']);
check('window expiry: reached only the entry step', ['view'], array_column($result['reached'], 'step_key'));

$result = funnelMatch($twoSteps, 3600, [
    ev('screen_view', 0, 'signup'),
    ev('signup_submitted', 3600),
]);
check('a match exactly at the deadline is inclusive', true, $result['completed']);

// Equal timestamps have no causal order. In particular, a batched/offline payload must not
// fabricate a conversion just because database insertion order happened to be favorable.
$result = funnelMatch($twoSteps, 3600, [
    ev('screen_view', 0, 'signup'),
    ev('signup_submitted', 0),
]);
check('steps at the same timestamp do not complete', false, $result['completed']);
check('same-timestamp events only reach entry', ['view'], array_column($result['reached'], 'step_key'));

// --- repeated step events -----------------------------------------------------
$result = funnelMatch($twoSteps, 3600, [
    ev('screen_view', 0, 'signup'),
    ev('screen_view', 1, 'signup'),
    ev('screen_view', 2, 'signup'),
    ev('signup_submitted', 10),
]);
check('repeated step events: entry is the FIRST match', ts(0), $result['reached'][0]['ts']);
check('repeated step events: still completes', true, $result['completed']);

// --- restart is ignored --------------------------------------------------------
$result = funnelMatch($twoSteps, 100, [
    ev('screen_view', 0, 'signup'),
    ev('signup_submitted', 500),
    ev('screen_view', 1000, 'signup'),
    ev('signup_submitted', 1010),
]);
check('a restart after the window expired does not resurrect completion', false, $result['completed']);
check('a restart: the actor stays bound to their first entry', ts(0), $result['reached'][0]['ts']);

// --- missing middle step -------------------------------------------------------
$threeSteps = array_merge($twoSteps, [['key' => 'paid', 'event' => 'purchase_completed']]);
$result = funnelMatch($threeSteps, 3600, [
    ev('screen_view', 0, 'signup'),
    ev('purchase_completed', 20),
]);
check('a missing middle step blocks matching a later step', ['view'], array_column($result['reached'], 'step_key'));
check('a missing middle step: not completed', false, $result['completed']);

// --- single-step funnel ---------------------------------------------------------
$result = funnelMatch([['key' => 'view', 'event' => 'screen_view']], 60, [ev('screen_view', 0)]);
check('a single-step funnel completes on entry alone', true, $result['completed']);

// --- no entry match --------------------------------------------------------------
check('no matching entry event returns null', null, funnelMatch($twoSteps, 3600, [ev('some_other_event', 0)]));

// --- screen filter -----------------------------------------------------------------
check('a screen filter excludes the wrong screen', null, funnelMatch($twoSteps, 3600, [ev('screen_view', 0, 'settings')]));

$result = funnelMatch($twoSteps, 3600, [
    ev('screen_view', 0, 'settings'),
    ev('screen_view', 1, 'signup'),
    ev('signup_submitted', 2),
]);
check('a screen filter accepts the right screen after skipping a wrong one', ts(1), $result['reached'][0]['ts']);

// --- prop filter --------------------------------------------------------------------
$stepsWithProps = [
    ['key' => 'view', 'event' => 'screen_view', 'props' => ['variant' => 'b']],
    ['key' => 'submit', 'event' => 'signup_submitted'],
];
$result = funnelMatch($stepsWithProps, 3600, [
    ev('screen_view', 0, null, ['variant' => 'a']),
    ev('screen_view', 1, null, ['variant' => 'b']),
    ev('signup_submitted', 2),
]);
check('a prop filter requires an exact match', ts(1), $result['reached'][0]['ts']);

// A numeric event prop (as json_decode produces it) must still match a string-valued step
// filter — otherwise a filter like {"tier": 2} silently matches nothing, ever.
$stepsWithNumericProp = [
    ['key' => 'view', 'event' => 'screen_view', 'props' => ['tier' => '2']],
    ['key' => 'submit', 'event' => 'signup_submitted'],
];
$result = funnelMatch($stepsWithNumericProp, 3600, [
    ev('screen_view', 0, null, ['tier' => 2]),
    ev('signup_submitted', 1),
]);
check('a string-valued prop filter matches a numeric event prop of equal value', ts(0), $result['reached'][0]['ts']);

$result = funnelMatch($stepsWithNumericProp, 3600, [
    ev('screen_view', 0, null, ['tier' => 3]),
    ev('signup_submitted', 1),
]);
check('a string-valued prop filter still rejects a numeric event prop of unequal value', null, $result);

// --- empty step list ------------------------------------------------------------------
check('an empty step list matches nothing', null, funnelMatch([], 3600, [ev('x', 0)]));

// --- percentile ------------------------------------------------------------------------
check('percentile of empty input is null', null, funnelPercentile([], 50.0));
check('percentile of a single value is that value at any p', 42, funnelPercentile([42], 50.0));
check('median of two values is the lower one under nearest-rank', 10, funnelPercentile([10, 20], 50.0));
check('p90 of two values is the higher one', 20, funnelPercentile([10, 20], 90.0));
check('percentile input order does not matter', 10, funnelPercentile([20, 10], 50.0));
check('median of an odd-sized list is the middle value', 30, funnelPercentile([10, 20, 30, 40, 50], 50.0));
check('p90 of ten values is the ninth smallest', 90, funnelPercentile([10, 20, 30, 40, 50, 60, 70, 80, 90, 100], 90.0));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all funnel-match assertions passed\n";
