<?php

/**
 * Unit tests for counterValidate()/counterCanonicalize() -- mirrors
 * servers/ktor/.../counters/CounterRegistryTest.kt. Keep the two in sync; the shared vectors
 * below must produce the identical canonical string and hash in both languages, or the same
 * logical cell splits into two rows across backends.
 *
 * Run: php php-hosting/tests/counterRegistryTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/counterRegistry.php';

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

// --- structural validation ---------------------------------------------------------------

check('unknown metric is invalid', 'unknown_metric', counterValidate('not_a_real_metric', ['from' => 'Library'])['reason']);

check('missing a required dim is invalid', 'dims_mismatch', counterValidate('screen_transition', ['from' => 'Library'])['reason']);

check(
    'an extra undeclared dim is invalid',
    'dims_mismatch',
    counterValidate('screen_transition', ['from' => 'Library', 'to' => 'BookDetail', 'extra' => 'x'])['reason'],
);

check(
    'a value outside the allowed charset is invalid',
    'dim_value_invalid',
    counterValidate('screen_transition', ['from' => 'Library', 'to' => 'Book Detail!'])['reason'],
);

check(
    'a key outside the allowed charset is invalid',
    false,
    counterValidate('screen_transition', ['FROM' => 'Library', 'to' => 'BookDetail'])['valid'],
);

// --- canonicalization ---------------------------------------------------------------------

$a = counterValidate('screen_transition', ['from' => 'Library', 'to' => 'BookDetail']);
$b = counterValidate('screen_transition', ['to' => 'BookDetail', 'from' => 'Library']);
check('canonicalization is independent of input key order (canonical)', $a['canonical'], $b['canonical']);
check('canonicalization is independent of input key order (hash)', $a['hash'], $b['hash']);

// Shared vector -- servers/ktor CounterRegistryTest and this file must produce this exact
// hash for this exact input.
check(
    'canonicalization and hash match a fixed shared vector',
    '4b3584b7f1d30836d77378aa3e351dce36ea7ae9f8c101ae0633dd31159e4594',
    counterValidate('screen_transition', ['from' => 'Library', 'to' => 'BookDetail'])['hash'],
);

// Guards against a naive no-separator concatenation, where {from:"AB",to:"C"} and
// {from:"A",to:"BC"} would canonicalize to the same string.
$swapped = counterValidate('screen_transition', ['from' => 'BookDetail', 'to' => 'Library']);
check('swapping which value goes with which key changes the hash', false, $a['hash'] === $swapped['hash']);

// --- every declared v1 metric validates a well-formed example -----------------------------

$examples = [
    'screen_transition' => ['from' => 'Library', 'to' => 'BookDetail'],
    'screen_dwell'       => ['screen' => 'BookDetail', 'bucket' => '5_10s'],
    'funnel_step'        => ['f' => 'checkout', 'rev' => '7', 'step' => '3'],
    'retention'          => ['cohort' => '2026-W31', 'day' => '7'],
    'session'            => ['bucket' => '1_3'],
    'event'              => ['name' => 'page_view'],
    'value'              => ['name' => 'purchase'],
    'activation'         => ['cohort' => '2026-W31'],
    'search'             => ['screen' => 'Library'],
    'search_zero_result' => ['screen' => 'Library'],
    'friction'           => ['screen' => 'Checkout', 'kind' => 'rage_tap'],
    'experiment'         => ['exp' => 'checkout_cta', 'variant' => 'b', 'action' => 'impression'],
    'unique_window_v2'   => ['scope_kind' => 'project', 'scope_key' => 'all', 'days' => '7', 'end_day' => '2026-10-04'],
];
foreach ($examples as $metric => $dims) {
    check("$metric validates a well-formed example", true, counterValidate($metric, $dims)['valid']);
}

// --- experiment metric: shared vector with CounterRegistryTest.kt -------------------------
// Both backends must produce this exact canonical string and hash for the A/B testing
// "experiment" metric, or the same logical impression/interaction cell splits into two rows
// across backends.

$expResult = counterValidate('experiment', ['exp' => 'checkout_cta', 'variant' => 'b', 'action' => 'impression']);
check('experiment metric is valid', true, $expResult['valid']);
check('experiment metric hashes to the shared vector', 'f7dec88b3cd61e3b865e98c0b56991eb1f61e241ccdf381fe15f0bd9b9175c2f', $expResult['hash']);

check(
    'experiment metric missing action is invalid',
    'dims_mismatch',
    counterValidate('experiment', ['exp' => 'checkout_cta', 'variant' => 'b'])['reason'],
);
check(
    'experiment metric with an extra key is invalid',
    'dims_mismatch',
    counterValidate('experiment', ['exp' => 'checkout_cta', 'variant' => 'b', 'action' => 'impression', 'extra' => 'x'])['reason'],
);

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all counter-registry assertions passed\n";
