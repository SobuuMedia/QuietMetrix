<?php

/**
 * Structural validation and canonicalization for counter cells. Mirrors
 * servers/ktor/.../counters/CounterRegistry.kt exactly, including the canonicalization
 * algorithm -- the two must hash identical dims to the identical value, or the same logical
 * cell silently splits into two rows across backends. Keep both in sync; see
 * counterRegistryTest.php for the shared vectors.
 *
 * A counter is (metric, dims) -> n. This registry is what stands between arbitrary SDK input
 * and the `counters` table: an unknown metric, an undeclared dimension, or a value outside the
 * allowed charset is rejected here rather than persisted.
 */

/** v1 metrics: each requires exactly this set of dimension keys -- no more, no fewer. */
function counterMetricRegistry(): array {
    return [
        'screen_transition' => ['from', 'to'],
        'screen_dwell'       => ['screen', 'bucket'],
        'funnel_step'        => ['f', 'rev', 'step'],
        'retention'          => ['cohort', 'day'],
        'session'            => ['bucket'],
        'event'              => ['name'],
        'value'              => ['name'],
        'activation'         => ['cohort'],
        'search'             => ['screen'],
        'search_zero_result' => ['screen'],
        'friction'           => ['screen', 'kind'],
    ];
}

const COUNTER_KEY_REGEX = '/^[a-z0-9_]{1,32}$/';
const COUNTER_VALUE_REGEX = '/^[A-Za-z0-9_.:\/-]{1,128}$/';

/**
 * Validates $dims against the declared shape of $metric and, if valid, canonicalizes and
 * hashes them. Returns ['valid' => true, 'canonical' => string, 'hash' => string] or
 * ['valid' => false, 'reason' => string].
 */
function counterValidate(string $metric, array $dims): array {
    $registry = counterMetricRegistry();
    if (!array_key_exists($metric, $registry)) {
        return ['valid' => false, 'reason' => 'unknown_metric'];
    }
    $required = $registry[$metric];
    $keys = array_keys($dims);
    sort($required);
    $sortedKeys = $keys;
    sort($sortedKeys);
    if ($sortedKeys !== $required) {
        return ['valid' => false, 'reason' => 'dims_mismatch'];
    }
    foreach ($dims as $key => $value) {
        if (!is_string($key) || !preg_match(COUNTER_KEY_REGEX, $key)) {
            return ['valid' => false, 'reason' => 'dim_key_invalid'];
        }
        if (!is_string($value) || !preg_match(COUNTER_VALUE_REGEX, $value)) {
            return ['valid' => false, 'reason' => 'dim_value_invalid'];
        }
    }
    $canonical = counterCanonicalize($dims);
    return ['valid' => true, 'canonical' => $canonical, 'hash' => hash('sha256', $canonical)];
}

/**
 * Sorted-key join with ASCII unit/record separators (\x1F, \x1E) between value/key and
 * between pairs. Values are charset-restricted above so neither separator can ever appear
 * inside a key or value, making this join unambiguous without percent-encoding.
 */
function counterCanonicalize(array $dims): string {
    ksort($dims);
    $parts = [];
    foreach ($dims as $key => $value) {
        $parts[] = $key . "\x1F" . $value;
    }
    return implode("\x1E", $parts);
}
