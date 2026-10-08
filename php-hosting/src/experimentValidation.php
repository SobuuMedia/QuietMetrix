<?php

/**
 * Validation rules for an A/B testing experiment definition. Mirrors
 * servers/ktor/.../experiments/ExperimentValidation.kt exactly -- keep the two in sync.
 *
 * $definition shape: ['key' => string, 'name' => string, 'screen' => ?string,
 * 'traffic_percent' => int, 'variants' => [['name' => string, 'weight' => int], ...],
 * 'countries' => ?array].
 */

const EXPERIMENT_NONE_VARIANT = 'none';
const EXPERIMENT_KEY_REGEX = '/^[a-z0-9_]{1,32}$/';
const EXPERIMENT_VARIANT_NAME_REGEX = '/^[a-z0-9_]{1,32}$/';
const EXPERIMENT_COUNTRY_REGEX = '/^[A-Z]{2}$/';
const EXPERIMENT_MAX_COUNTRIES = 50;
const EXPERIMENT_MAX_NAME_LENGTH = 255;
const EXPERIMENT_MAX_SCREEN_LENGTH = 128;

/** All violations of $definition, or an empty array when it is valid. */
function experimentValidate(array $definition): array {
    $errors = [];
    $mode = (string)($definition['mode'] ?? 'legacy');
    if (!in_array($mode, ['legacy', 'visibility', 'variants'], true)) $errors[] = 'mode must be legacy, visibility, or variants';

    $key = (string)($definition['key'] ?? '');
    if (!preg_match(EXPERIMENT_KEY_REGEX, $key)) {
        $errors[] = 'key must match ^[a-z0-9_]{1,32}$';
    }

    $name = (string)($definition['name'] ?? '');
    if (trim($name) === '' || strlen($name) > EXPERIMENT_MAX_NAME_LENGTH) {
        $errors[] = 'name must be 1-' . EXPERIMENT_MAX_NAME_LENGTH . ' characters';
    }

    $screen = $definition['screen'] ?? null;
    if ($screen !== null && strlen((string)$screen) > EXPERIMENT_MAX_SCREEN_LENGTH) {
        $errors[] = 'screen must be at most ' . EXPERIMENT_MAX_SCREEN_LENGTH . ' characters';
    }

    $trafficPercent = (int)($definition['traffic_percent'] ?? 0);
    if ($trafficPercent < 1 || $trafficPercent > 100) {
        $errors[] = 'traffic_percent must be between 1 and 100';
    }

    $variants = is_array($definition['variants'] ?? null) ? $definition['variants'] : [];
    if (count($variants) !== 2) {
        $errors[] = 'exactly 2 variants are required';
    } else {
        $names = [];
        $weightSum = 0;
        foreach ($variants as $v) {
            $vName = (string)($v['name'] ?? '');
            $vWeight = (int)($v['weight'] ?? 0);
            $names[] = $vName;
            $weightSum += $vWeight;
            if (!preg_match(EXPERIMENT_VARIANT_NAME_REGEX, $vName)) {
                $errors[] = "variant name '$vName' must match ^[a-z0-9_]{1,32}\$";
            }
            if ($vName === EXPERIMENT_NONE_VARIANT) {
                $errors[] = "variant name cannot be '" . EXPERIMENT_NONE_VARIANT . "'";
            }
            $minWeight = $mode === 'legacy' ? 1 : 0;
            $maxWeight = $mode === 'legacy' ? 99 : 100;
            if ($vWeight < $minWeight || $vWeight > $maxWeight) {
                $errors[] = "variant '$vName' weight must be between $minWeight and $maxWeight";
            }
        }
        if (count($names) === 2 && $names[0] === $names[1]) {
            $errors[] = 'variant names must be distinct';
        }
        if ($weightSum !== 100) {
            $errors[] = 'variant weights must sum to 100';
        }
    }

    if ($mode !== 'legacy') {
        $bPercent = $definition['b_percent'] ?? null;
        if (!is_int($bPercent) || $bPercent < 0 || $bPercent > 100) $errors[] = 'b_percent must be between 0 and 100';
        if (trim((string)($definition['placement_key'] ?? '')) === '') $errors[] = 'placement_key is required for v2 experiments';
        if (count($variants) === 2 && is_int($bPercent) &&
            ((int)($variants[0]['weight'] ?? -1) !== 100 - $bPercent || (int)($variants[1]['weight'] ?? -1) !== $bPercent)) {
            $errors[] = 'variant weights must match b_percent (A = 100 - B, B = b_percent)';
        }
        if (!in_array((string)($definition['match_mode'] ?? 'all'), ['all', 'any'], true)) $errors[] = 'match_mode must be all or any';
        $goalEvent = $definition['goal_event'] ?? null;
        if ($goalEvent !== null && !preg_match('/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/', (string)$goalEvent)) $errors[] = 'goal_event must be a valid event key';
        foreach (($definition['languages'] ?? []) as $language) {
            if (!preg_match('/^[a-z]{2,3}$/', (string)$language)) $errors[] = 'languages must contain normalized primary language codes';
        }
    }

    $countries = $definition['countries'] ?? null;
    if ($countries !== null) {
        if (!is_array($countries)) {
            $errors[] = 'countries must be an array';
        } else {
            if (count($countries) > EXPERIMENT_MAX_COUNTRIES) {
                $errors[] = 'at most ' . EXPERIMENT_MAX_COUNTRIES . ' countries are allowed';
            }
            foreach ($countries as $c) {
                if (!preg_match(EXPERIMENT_COUNTRY_REGEX, (string)$c)) {
                    $errors[] = "country code '$c' must be a 2-letter uppercase ISO code";
                }
            }
        }
    }

    return $errors;
}

/**
 * Uppercases, trims, dedupes and sorts a raw country-code list; blank entries are dropped.
 * Returns null (meaning "all countries") for a null or empty input -- never an empty array.
 */
function experimentNormalizeCountries(?array $raw): ?array {
    if ($raw === null) return null;
    $normalized = [];
    foreach ($raw as $c) {
        $trimmed = strtoupper(trim((string)$c));
        if ($trimmed !== '' && !in_array($trimmed, $normalized, true)) {
            $normalized[] = $trimmed;
        }
    }
    if (empty($normalized)) return null;
    sort($normalized);
    return $normalized;
}
