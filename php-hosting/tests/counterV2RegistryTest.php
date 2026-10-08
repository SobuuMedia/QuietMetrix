<?php

require_once __DIR__ . '/../src/counterRegistry.php';
require_once __DIR__ . '/../src/counterRegistryV2.php';
require_once __DIR__ . '/../src/routes/countersV2.php';

$failures = 0;
function v2Check(string $name, $expected, $actual): void {
    global $failures;
    if ($expected !== $actual) { $failures++; echo "FAIL {$name}: " . var_export($actual, true) . "\n"; }
    else echo "  ok: {$name}\n";
}

$valid = counterV2Validate('screen_view_v2', ['screen' => 'home']);
v2Check('registered screen view accepted', true, $valid['valid']);
v2Check('registry hash uses shared separator canonicalization', hash('sha256', "screen\x1fhome"), $valid['hash']);
v2Check('unknown metric rejected', 'unknown_metric', counterV2Validate('free_form', [])['reason']);
v2Check('empty metric rejects unexpected dimensions', 'dims_mismatch', counterV2Validate('visit_start_v2', ['x' => 'y'])['reason']);
v2Check('reserved screen rejects', 'reserved_screen', counterV2Validate('screen_view_v2', ['screen' => '__exit__'])['reason']);
v2Check('invalid retention day rejected', 'retention_return_invalid', counterV2Validate('retention_return_v2', ['entry_day' => '2026-99-99', 'age_day' => '7'])['reason']);

$bad = counterV2Validate('screen_view_v2', ['screen' => 'home']);
$item = ['item' => ['m' => 'screen_view_v2', 'd' => ['screen' => 'home'], 'n' => 2, 'contributors' => 1], 'canonical' => $bad['canonical'], 'hash' => $bad['hash']];
$a = counterV2PayloadHash('2026-10-04', 9, ['platform' => 'android', 'version' => '2.0'], ['version' => '1.0'], [$item]);
$b = counterV2PayloadHash('2026-10-04', 9, ['version' => '2.0', 'platform' => 'android'], ['version' => '1.0'], [$item]);
v2Check('batch canonicalization ignores JSON map key order', $a, $b);
v2Check('batch payload hash matches the shared Kotlin vector', '48f8e44362b4c88c059f15a40db705df084fa9f2254804aaed291a26e8ca1fc7', $a);

$envelopeItems = [];
foreach ([
    ['item'=>['day'=>'2026-10-04','hour'=>9,'m'=>'screen_view_v2','d'=>['screen'=>'home'],'n'=>2,'contributors'=>0,'segment_kind'=>'global','segment_value'=>'all']],
    ['item'=>['day'=>'2026-10-05','hour'=>null,'m'=>'screen_view_v2','d'=>['screen'=>'home'],'n'=>1,'contributors'=>0,'segment_kind'=>'platform','segment_value'=>'android']],
] as $cell) {
    $check = counterV2Validate($cell['item']['m'], $cell['item']['d']);
    $envelopeItems[] = $cell + ['canonical'=>$check['canonical'], 'hash'=>$check['hash']];
}
$envelopeHash = counterV2EnvelopePayloadHash(2, ['platform'=>'android','version'=>'2.1'], [], $envelopeItems);
v2Check('schema-2 per-item payload hash matches Kotlin shared vector', '983e990ed69f7811292afdc82e7e0c39600958043b0f1e37f5ce05ca03e77955', $envelopeHash);
v2Check('schema-2 hash ignores item order', $envelopeHash, counterV2EnvelopePayloadHash(2, ['version'=>'2.1','platform'=>'android'], [], array_reverse($envelopeItems)));
v2Check('global segment accepts only all', true, counterV2ValidSegment('global','all'));
v2Check('valid ISO country projection is accepted', true, counterV2ValidSegment('country','FR'));
v2Check('unknown ISO country projection is rejected', false, counterV2ValidSegment('country','ZZ'));
v2Check('unsupported segment is rejected', false, counterV2ValidSegment('language','fr'));

if ($failures > 0) exit(1);
echo "counterV2RegistryTest passed\n";
