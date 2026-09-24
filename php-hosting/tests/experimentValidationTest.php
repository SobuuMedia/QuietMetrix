<?php

/**
 * Unit tests for experimentValidate()/experimentNormalizeCountries() -- mirrors
 * servers/ktor/.../experiments/ExperimentValidationTest.kt. Keep the two in sync.
 *
 * Run: php php-hosting/tests/experimentValidationTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/experimentValidation.php';

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

function validDef(array $overrides = []): array {
    return array_merge([
        'key' => 'checkout_cta',
        'name' => 'Checkout button colour',
        'screen' => 'Checkout',
        'traffic_percent' => 100,
        'variants' => [['name' => 'a', 'weight' => 50], ['name' => 'b', 'weight' => 50]],
        'countries' => null,
    ], $overrides);
}

check('a well-formed definition has no errors', [], experimentValidate(validDef()));

check('an invalid key is rejected', true, !empty(experimentValidate(validDef(['key' => 'Checkout CTA!']))));
check('an empty key is rejected', true, !empty(experimentValidate(validDef(['key' => '']))));
check('a too-long key is rejected', true, !empty(experimentValidate(validDef(['key' => str_repeat('a', 33)]))));

check('a blank name is rejected', true, !empty(experimentValidate(validDef(['name' => '  ']))));

check('traffic_percent 0 is rejected', true, !empty(experimentValidate(validDef(['traffic_percent' => 0]))));
check('traffic_percent 101 is rejected', true, !empty(experimentValidate(validDef(['traffic_percent' => 101]))));
check('traffic_percent 1 is accepted', [], experimentValidate(validDef(['traffic_percent' => 1])));
check('traffic_percent 100 is accepted', [], experimentValidate(validDef(['traffic_percent' => 100])));

check('only 1 variant is rejected', true, !empty(experimentValidate(validDef(['variants' => [['name' => 'a', 'weight' => 100]]]))));
check('3 variants are rejected', true, !empty(experimentValidate(validDef([
    'variants' => [['name' => 'a', 'weight' => 34], ['name' => 'b', 'weight' => 33], ['name' => 'c', 'weight' => 33]],
]))));

check('variant named none is rejected', true, !empty(experimentValidate(validDef([
    'variants' => [['name' => 'none', 'weight' => 50], ['name' => 'b', 'weight' => 50]],
]))));

check('duplicate variant names are rejected', true, !empty(experimentValidate(validDef([
    'variants' => [['name' => 'a', 'weight' => 50], ['name' => 'a', 'weight' => 50]],
]))));

check('weight 0 is rejected', true, !empty(experimentValidate(validDef([
    'variants' => [['name' => 'a', 'weight' => 0], ['name' => 'b', 'weight' => 100]],
]))));
check('weights not summing to 100 are rejected', true, !empty(experimentValidate(validDef([
    'variants' => [['name' => 'a', 'weight' => 60], ['name' => 'b', 'weight' => 60]],
]))));

check('a 3-letter country code is rejected', true, !empty(experimentValidate(validDef(['countries' => ['ESP']]))));
check('a lowercase country code is rejected', true, !empty(experimentValidate(validDef(['countries' => ['e5']]))));
check('more than 50 countries is rejected', true, !empty(experimentValidate(validDef([
    'countries' => array_map(fn($i) => 'A' . chr(65 + ($i % 26)), range(1, 51)),
]))));
check('a valid 2-letter country list is accepted', [], experimentValidate(validDef(['countries' => ['ES', 'FR']])));

check(
    'normalizeCountries uppercases trims dedupes and sorts',
    ['ES', 'FR'],
    experimentNormalizeCountries([' fr', 'ES', 'es', 'FR ']),
);
check('normalizeCountries(null) is null', null, experimentNormalizeCountries(null));
check('normalizeCountries([]) is null', null, experimentNormalizeCountries([]));
check('normalizeCountries([blank]) is null', null, experimentNormalizeCountries(['  ']));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all experiment-validation assertions passed\n";
