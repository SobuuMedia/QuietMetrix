<?php

/**
 * Reshapes decoded funnel steps for the JSON wire: each step's `props` becomes a
 * stdClass (or empty-but-forced-object) of string values so json_encode emits
 * `{}`/`{"k":"v"}` rather than `[]`. PHP's assoc json_decode collapses an empty JSON
 * object and an empty JSON array to the same `[]`, and json_encode then guesses
 * "array" back — which breaks a strictly-typed client (Map<String,String> in
 * Kotlin) reading `"props":[]` instead of `"props":{}`. Only used for the response
 * envelope — callers that need the raw decoded steps (`props` still a plain array)
 * must read them before this runs.
 *
 * @param array $steps decoded steps: [{key, event, name?, screen?, props?}, ...]
 * @return array steps with `props` forced to an object-shaped value for json_encode
 */
function funnelStepsForWire(array $steps): array {
    $result = [];
    foreach ($steps as $step) {
        if (!is_array($step)) continue;
        $props = [];
        foreach (($step['props'] ?? []) as $key => $value) {
            if (!is_string($key)) continue;
            if (is_array($value)) continue;
            $props[$key] = funnelPropToString($value);
        }
        $step['props'] = empty($props) ? new stdClass() : $props;
        $result[] = $step;
    }
    return $result;
}

/**
 * Stringifies a decoded JSON scalar so wire props always round-trip the same way regardless
 * of source type. A plain (string) cast is not safe here: (string)false === '' in PHP, which
 * would make a false prop indistinguishable from an absent/empty one.
 */
function funnelPropToString($value): string {
    if (is_bool($value)) return $value ? 'true' : 'false';
    return (string)$value;
}
