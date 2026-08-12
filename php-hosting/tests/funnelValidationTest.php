<?php

/**
 * Unit tests for validateFunnelDefinition() — mirrors
 * servers/ktor/.../funnels/FunnelValidation.kt; keep the two in sync.
 *
 * Run: php php-hosting/tests/funnelValidationTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/funnelValidation.php';

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

function step(string $key, string $event = 'some_event'): array {
    return ['key' => $key, 'event' => $event];
}

function definition(
    string $funnelKey = 'signup',
    string $name = 'Signup',
    ?array $steps = null,
    int $windowSeconds = 604800
): array {
    return [
        'funnel_key' => $funnelKey,
        'name' => $name,
        'steps' => $steps ?? [step('view'), step('submit')],
        'window_seconds' => $windowSeconds,
    ];
}

check('a well-formed two-step funnel is valid', [],
    validateFunnelDefinition(definition(), 0));

check('rejects a funnel key with uppercase or spaces', true,
    count(validateFunnelDefinition(definition(funnelKey: 'Sign Up'), 0)) > 0);

check('accepts a funnel key with hyphens and underscores', [],
    validateFunnelDefinition(definition(funnelKey: 'signup-v2_beta'), 0));

check('rejects a funnel key over 64 chars', true,
    count(validateFunnelDefinition(definition(funnelKey: str_repeat('a', 65)), 0)) > 0);

check('rejects a blank name', true,
    count(validateFunnelDefinition(definition(name: '  '), 0)) > 0);

check('rejects a single-step funnel', true,
    count(validateFunnelDefinition(definition(steps: [step('view')]), 0)) > 0);

check('rejects more than 10 steps', true,
    count(validateFunnelDefinition(definition(steps: array_map(
        fn($i) => step("step$i"), range(1, 11)
    )), 0)) > 0);

check('accepts exactly 10 steps', [],
    validateFunnelDefinition(definition(steps: array_map(
        fn($i) => step("step$i"), range(1, 10)
    )), 0));

check('rejects duplicate step keys', true,
    count(validateFunnelDefinition(definition(steps: [step('view'), step('view')]), 0)) > 0);

check('rejects a step with a blank event name', true,
    count(validateFunnelDefinition(definition(steps: [step('view', ''), step('submit')]), 0)) > 0);

check('rejects a step key that fails the slug pattern', true,
    count(validateFunnelDefinition(definition(steps: [step('View Step'), step('submit')]), 0)) > 0);

check('rejects a window under 60 seconds', true,
    count(validateFunnelDefinition(definition(windowSeconds: 59), 0)) > 0);

check('accepts a window of exactly 60 seconds', [],
    validateFunnelDefinition(definition(windowSeconds: 60), 0));

check('rejects a window over 90 days', true,
    count(validateFunnelDefinition(definition(windowSeconds: 90 * 86400 + 1), 0)) > 0);

check('accepts a window of exactly 90 days', [],
    validateFunnelDefinition(definition(windowSeconds: 90 * 86400), 0));

check('rejects the 21st funnel in a project', true,
    count(validateFunnelDefinition(definition(), 20)) > 0);

check('accepts the 20th funnel in a project', [],
    validateFunnelDefinition(definition(), 19));

check('collects multiple errors at once rather than stopping at the first', true,
    count(validateFunnelDefinition(definition(funnelKey: 'Bad Key', name: '', steps: [step('only-one')]), 0)) >= 3);

// --- props ------------------------------------------------------------------------------

function stepWithProps(array $props): array {
    return ['key' => 'view', 'event' => 'screen_view', 'props' => $props];
}

check('accepts a step with string-valued props', [],
    validateFunnelDefinition(definition(steps: [stepWithProps(['variant' => 'b']), step('submit')]), 0));

check('accepts a step with scalar (non-string) props values', [],
    validateFunnelDefinition(definition(steps: [stepWithProps(['tier' => 2, 'ok' => true]), step('submit')]), 0));

check('accepts a step with no props key at all', [],
    validateFunnelDefinition(definition(steps: [step('view'), step('submit')]), 0));

check('rejects a step whose props is not an array', true,
    count(validateFunnelDefinition(definition(steps: [
        ['key' => 'view', 'event' => 'screen_view', 'props' => 'not-an-array'], step('submit'),
    ]), 0)) > 0);

check('rejects a step whose props value is itself an array/object', true,
    count(validateFunnelDefinition(definition(steps: [
        stepWithProps(['nested' => ['a' => 1]]), step('submit'),
    ]), 0)) > 0);

check('rejects attempt mode without a correlation property', true,
    count(validateFunnelDefinition(array_merge(definition(), ['count_mode' => 'attempt']), 0)) > 0);

check('accepts attempt mode with a correlation property', [],
    validateFunnelDefinition(array_merge(definition(), [
        'count_mode' => 'attempt', 'correlation_property' => 'flow_id',
    ]), 0));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all funnel validation assertions passed\n";
