<?php

/**
 * Validation rules for a funnel definition. Mirrors
 * servers/ktor/.../funnels/FunnelValidation.kt; keep the two in sync.
 */

const FUNNEL_KEY_PATTERN = '/^[a-z0-9_-]{1,64}$/';
const FUNNEL_MIN_STEPS = 2;
const FUNNEL_MAX_STEPS = 10;
const FUNNEL_MAX_PER_PROJECT = 20;
const FUNNEL_MIN_WINDOW_SECONDS = 60;
const FUNNEL_MAX_WINDOW_SECONDS = 90 * 86400;
const FUNNEL_DEFAULT_WINDOW_SECONDS = 7 * 86400;

/**
 * Validates a funnel definition shaped like:
 *   ['funnel_key' => string, 'name' => string, 'steps' => [['key' => string,
 *    'event' => string, 'name'? => string, 'screen'? => string, 'props'? => array], ...],
 *    'window_seconds' => int, 'count_mode' => 'actor'|'attempt',
 *    'identity_scope' => 'install_or_session'|'install'|'session', 'correlation_property'? => string]
 *
 * [$existingFunnelCount] is the project's current funnel count, EXCLUDING the one being
 * validated (0 for a brand-new funnel; the pre-update count minus one when editing).
 *
 * Returns an array of error strings — empty means valid.
 */
function validateFunnelDefinition(array $definition, int $existingFunnelCount): array {
    $errors = [];

    $funnelKey = $definition['funnel_key'] ?? '';
    if (!is_string($funnelKey) || !preg_match(FUNNEL_KEY_PATTERN, $funnelKey)) {
        $errors[] = 'funnel_key must match ^[a-z0-9_-]{1,64}$';
    }

    $name = $definition['name'] ?? '';
    if (!is_string($name) || trim($name) === '') {
        $errors[] = 'name must not be blank';
    }

    $steps = $definition['steps'] ?? null;
    if (!is_array($steps)) {
        $errors[] = 'steps must be an array';
        $steps = [];
    }
    $stepCount = count($steps);
    if ($stepCount < FUNNEL_MIN_STEPS || $stepCount > FUNNEL_MAX_STEPS) {
        $errors[] = 'steps must have between ' . FUNNEL_MIN_STEPS . ' and ' . FUNNEL_MAX_STEPS . ' entries';
    }

    $stepKeys = array_map(fn($s) => is_array($s) ? ($s['key'] ?? null) : null, $steps);
    if (count(array_unique($stepKeys)) !== count($stepKeys)) {
        $errors[] = 'step keys must be unique within a funnel';
    }

    foreach ($steps as $i => $step) {
        if (!is_array($step)) {
            $errors[] = "steps[$i] must be an object";
            continue;
        }
        $stepKey = $step['key'] ?? '';
        if (!is_string($stepKey) || !preg_match(FUNNEL_KEY_PATTERN, $stepKey)) {
            $errors[] = "steps[$i].key must match ^[a-z0-9_-]{1,64}$";
        }
        $event = $step['event'] ?? '';
        if (!is_string($event) || trim($event) === '') {
            $errors[] = "steps[$i].event must not be blank";
        }
        if (array_key_exists('props', $step)) {
            $props = $step['props'];
            if (!is_array($props)) {
                $errors[] = "steps[$i].props must be an object";
            } else {
                foreach ($props as $value) {
                    if (is_array($value)) {
                        $errors[] = "steps[$i].props values must be scalar (string, number, or boolean)";
                        break;
                    }
                }
            }
        }
    }

    $windowSeconds = $definition['window_seconds'] ?? null;
    if (!is_int($windowSeconds) || $windowSeconds < FUNNEL_MIN_WINDOW_SECONDS || $windowSeconds > FUNNEL_MAX_WINDOW_SECONDS) {
        $errors[] = 'window_seconds must be between ' . FUNNEL_MIN_WINDOW_SECONDS . ' and ' . FUNNEL_MAX_WINDOW_SECONDS;
    }

    $countMode = $definition['count_mode'] ?? 'actor';
    if (!in_array($countMode, ['actor', 'attempt'], true)) {
        $errors[] = 'count_mode must be actor or attempt';
    }
    $identityScope = $definition['identity_scope'] ?? 'install_or_session';
    if (!in_array($identityScope, ['install_or_session', 'install', 'session'], true)) {
        $errors[] = 'identity_scope must be install_or_session, install, or session';
    }
    $correlationProperty = $definition['correlation_property'] ?? null;
    if ($countMode === 'attempt' && (!is_string($correlationProperty) || trim($correlationProperty) === '')) {
        $errors[] = 'correlation_property is required when count_mode is attempt';
    }

    if ($existingFunnelCount >= FUNNEL_MAX_PER_PROJECT) {
        $errors[] = 'a project may have at most ' . FUNNEL_MAX_PER_PROJECT . ' funnels';
    }

    return $errors;
}
