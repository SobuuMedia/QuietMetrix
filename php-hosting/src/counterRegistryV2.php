<?php

/** Strict v2 counter shapes; kept in parity with Ktor CounterRegistryV2. */
function counterV2Shapes(): array {
    return [
        'unique_window_v2' => ['scope_kind','scope_key','days','end_day'],
        'unique_hour_v2' => ['scope_kind','scope_key'],
        'witness_window_v2' => ['target_metric','target_hash','days','end_day'],
        'visit_start_v2' => [], 'visit_duration_ms_v2' => [], 'visit_complete_v2' => [],
        'screen_view_v2' => ['screen'], 'screen_dwell_ms_v2' => ['screen'], 'screen_dwell_sample_v2' => ['screen'],
        'event_v2' => ['name'], 'crash_v2' => [], 'error_v2' => [], 'screen_transition_v2' => ['from','to'],
        'search_v2' => ['screen'], 'search_zero_result_v2' => ['screen'], 'retention_entry_v2' => ['entry_day'],
        'retention_return_v2' => ['entry_day','age_day'],
        'funnel_step_v2' => ['namespace','f','revision','step','entry_day'],
        'experiment_exposure_v2' => ['exp','revision','variant'], 'experiment_goal_v2' => ['exp','revision','variant'],
        'sdk_presence_v2' => ['sdk_version','app_version','os_family','os_major'],
    ];
}

function counterV2Validate(string $metric, array $dims): array {
    $shapes = counterV2Shapes();
    if (!isset($shapes[$metric])) return ['valid' => false, 'reason' => 'unknown_metric'];
    $keys = array_keys($dims); sort($keys); $expected = $shapes[$metric]; sort($expected);
    if ($keys !== $expected) return ['valid' => false, 'reason' => 'dims_mismatch'];
    foreach ($dims as $key => $value) {
        if (!is_string($key) || preg_match(COUNTER_KEY_REGEX, $key) !== 1) return ['valid' => false, 'reason' => 'dim_key_invalid'];
        if (!is_string($value) || preg_match(COUNTER_VALUE_REGEX, $value) !== 1) return ['valid' => false, 'reason' => 'dim_value_invalid'];
    }
    $validDate = static function (string $key) use ($dims): bool {
        $date = DateTimeImmutable::createFromFormat('!Y-m-d', $dims[$key] ?? '');
        return $date !== false && $date->format('Y-m-d') === ($dims[$key] ?? null);
    };
    $validDays = static function () use ($dims): bool {
        $days = filter_var($dims['days'] ?? null, FILTER_VALIDATE_INT);
        return (($days >= 1 && $days <= 7) || $days === 30 || $days === 90);
    };
    if ($metric === 'unique_window_v2') {
        $kind = $dims['scope_kind']; $scope = $dims['scope_key'];
        if (!$validDays() || !$validDate('end_day') || !in_array($kind, ['project','screen'], true) ||
            ($kind === 'project' && $scope !== 'all') || ($kind === 'screen' && in_array($scope, ['all','__exit__'], true)))
            return ['valid' => false, 'reason' => 'unique_window_invalid'];
    } elseif ($metric === 'unique_hour_v2') {
        if (!in_array($dims['scope_kind'], ['project','screen'], true) ||
            ($dims['scope_kind'] === 'project' && $dims['scope_key'] !== 'all') ||
            ($dims['scope_kind'] === 'screen' && in_array($dims['scope_key'], ['all','__exit__'], true)))
            return ['valid' => false, 'reason' => 'unique_scope_invalid'];
    } elseif ($metric === 'witness_window_v2') {
        $ordinary = array_diff(array_keys($shapes), ['unique_window_v2','unique_hour_v2','witness_window_v2']);
        if (!$validDays() || !$validDate('end_day') || preg_match('/^[a-f0-9]{64}$/', $dims['target_hash']) !== 1 || !in_array($dims['target_metric'], $ordinary, true))
            return ['valid' => false, 'reason' => 'witness_window_invalid'];
    } elseif (in_array($metric, ['screen_view_v2','screen_dwell_ms_v2','screen_dwell_sample_v2','search_v2','search_zero_result_v2'], true)) {
        if ($dims['screen'] === '__exit__') return ['valid' => false, 'reason' => 'reserved_screen'];
    } elseif ($metric === 'screen_transition_v2') {
        if ($dims['from'] === '__exit__' || ($dims['to'] === '__exit__' && $dims['from'] === '')) return ['valid' => false, 'reason' => 'invalid_transition'];
    } elseif ($metric === 'retention_entry_v2') {
        if (!$validDate('entry_day')) return ['valid' => false, 'reason' => 'entry_day_invalid'];
    } elseif ($metric === 'retention_return_v2') {
        if (!$validDate('entry_day') || !in_array($dims['age_day'], ['1','3','7','14','30'], true)) return ['valid' => false, 'reason' => 'retention_return_invalid'];
    } elseif ($metric === 'funnel_step_v2') {
        $revision = filter_var($dims['revision'], FILTER_VALIDATE_INT); $step = filter_var($dims['step'], FILTER_VALIDATE_INT);
        if (!$validDate('entry_day') || $revision === false || $revision < 1 || $step === false || $step < 1 || $step > 10) return ['valid' => false, 'reason' => 'funnel_step_invalid'];
    } elseif (in_array($metric, ['experiment_exposure_v2','experiment_goal_v2'], true)) {
        $revision = filter_var($dims['revision'], FILTER_VALIDATE_INT);
        if ($revision === false || $revision < 1 || !in_array($dims['variant'], ['a','b'], true)) return ['valid' => false, 'reason' => 'experiment_cell_invalid'];
    }
    ksort($dims);
    $canonical = implode("\x1e", array_map(static fn($k, $v) => $k . "\x1f" . $v, array_keys($dims), array_values($dims)));
    return ['valid' => true, 'canonical' => $canonical, 'hash' => hash('sha256', $canonical)];
}

/** Project-wide budget hash excludes cohort/window dates; kept in parity with Ktor. */
function counterV2CardinalityHash(string $metric, array $dims): string {
    unset($dims['entry_day'], $dims['end_day']);
    if ($metric === 'witness_window_v2') unset($dims['target_hash']);
    ksort($dims);
    $canonical = implode("\x1e", array_map(static fn($k, $v) => $k . "\x1f" . $v, array_keys($dims), array_values($dims)));
    return hash('sha256', $canonical);
}
