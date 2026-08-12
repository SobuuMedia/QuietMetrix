<?php

/**
 * Unit tests for funnelStepsForWire() — the boundary that reshapes decoded funnel
 * steps for the JSON response so `props` always round-trips as an object, never an
 * array. Without this, json_encode(json_decode('{"props":{}}', true)) re-emits
 * "props":[] because PHP cannot tell an empty object from an empty list once it has
 * been decoded to an associative array — and the Kotlin dashboard's
 * Map<String,String> rejects that shape outright.
 *
 * Run: php php-hosting/tests/funnelStepsWireTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/funnelSteps.php';

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

// An empty PHP array (what json_decode gives us for both "props":{} and
// "props":[]) must always be re-encoded as an object.
check('empty props array encodes as an empty object, not an array',
    '[{"key":"a","event":"e","props":{}}]',
    json_encode(funnelStepsForWire([['key' => 'a', 'event' => 'e', 'props' => []]])));

check('a step with no props key at all gets an explicit empty object',
    '[{"key":"a","event":"e","props":{}}]',
    json_encode(funnelStepsForWire([['key' => 'a', 'event' => 'e']])));

check('non-empty string props survive untouched',
    '[{"key":"a","event":"e","props":{"referrer":"google"}}]',
    json_encode(funnelStepsForWire([['key' => 'a', 'event' => 'e', 'props' => ['referrer' => 'google']]])));

check('scalar non-string prop values are stringified',
    '[{"key":"a","event":"e","props":{"tier":"2","ok":"true"}}]',
    json_encode(funnelStepsForWire([['key' => 'a', 'event' => 'e', 'props' => ['tier' => 2, 'ok' => true]]])));

check('non-scalar prop values are dropped',
    '[{"key":"a","event":"e","props":{"kept":"1"}}]',
    json_encode(funnelStepsForWire([
        ['key' => 'a', 'event' => 'e', 'props' => ['kept' => 1, 'dropped' => ['nested' => true]]],
    ])));

check('other step fields pass through untouched',
    '[{"key":"view","event":"page_view","name":"View","screen":"home","props":{}}]',
    json_encode(funnelStepsForWire([
        ['key' => 'view', 'event' => 'page_view', 'name' => 'View', 'screen' => 'home'],
    ])));

check('multiple steps are each reshaped independently',
    '[{"key":"a","event":"e1","props":{}},{"key":"b","event":"e2","props":{"x":"1"}}]',
    json_encode(funnelStepsForWire([
        ['key' => 'a', 'event' => 'e1', 'props' => []],
        ['key' => 'b', 'event' => 'e2', 'props' => ['x' => 1]],
    ])));

check('a malformed (non-array) step is skipped rather than crashing',
    '[{"key":"a","event":"e","props":{}}]',
    json_encode(funnelStepsForWire([
        ['key' => 'a', 'event' => 'e'],
        'not-a-step',
    ])));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all funnel steps wire-shape assertions passed\n";
