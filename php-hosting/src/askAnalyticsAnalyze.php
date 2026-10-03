<?php

/** Computes a supported answer from privacy-gated aggregate reads; never infers causality. */
function askAnalyticsAnalyze(string $question, array $sessions, array $transitions, array $searches, array $zeroResults, array $retentionCells, string $range): array {
    $q = strtolower($question);
    $source = 'Selected range: ' . $range;
    if (preg_match('/\bwhy\b|por qué|por que/u', $q)) {
        return ['answer' => "I can't establish causes from aggregate counters. Ask about sessions, search results, Day 1 retention, or the most reached screen to see the observed values.", 'source' => 'Supported aggregate questions · ' . $source];
    }
    if (str_contains($q, 'session') || str_contains($q, 'visit') || str_contains($q, 'sesion') || str_contains($q, 'sesión')) {
        $total = sessionCounterAnalyze($sessions)['total_sessions'];
        return ['answer' => "There were $total sessions in the selected range.", 'source' => 'Sessions · ' . $source];
    }
    if (str_contains($q, 'search') || str_contains($q, 'búsqueda') || str_contains($q, 'busqueda')) {
        $worst = null;
        foreach (searchCounterAnalyze($searches, $zeroResults) as $result) {
            if ($result['rate'] !== null) { $worst = $result; break; }
        }
        if ($worst === null) return ['answer' => "There isn't enough privacy-safe search data to answer that yet.", 'source' => 'Search aggregates · ' . $source];
        $percent = (int)($worst['rate'] * 100);
        return ['answer' => "$percent% of searches on {$worst['screen']} returned no results ({$worst['zero_result']} of {$worst['total']}).", 'source' => 'Search aggregates · ' . $source];
    }
    if (str_contains($q, 'retention') || str_contains($q, 'return') || str_contains($q, 'came back') || str_contains($q, 'retención') || str_contains($q, 'retencion') || str_contains($q, 'volvieron')) {
        $known = array_values(array_filter(retentionCounterAnalyze($retentionCells), static fn(array $c): bool => $c['day1'] !== null && $c['size'] > 0));
        $size = array_sum(array_column($known, 'size'));
        if ($size === 0) return ['answer' => "There isn't enough privacy-safe Day 1 retention data to answer that yet.", 'source' => 'Retention aggregates · ' . $source];
        $returning = 0.0;
        foreach ($known as $cohort) $returning += round($cohort['day1'] * $cohort['size']);
        $percent = (int)($returning * 100 / $size);
        return ['answer' => "$percent% of people returned on Day 1 across cohorts with visible counts.", 'source' => 'Retention aggregates · ' . $source];
    }
    if (str_contains($q, 'screen') || str_contains($q, 'page') || str_contains($q, 'pantalla') || str_contains($q, 'página')) {
        $top = overviewCounterTopScreens($transitions, 1)[0] ?? null;
        if ($top === null) return ['answer' => "There isn't enough privacy-safe screen data to answer that yet.", 'source' => 'Screen transitions · ' . $source];
        return ['answer' => "{$top['screen']} was the most reached screen, with {$top['n']} transitions in the selected range.", 'source' => 'Screen transitions · ' . $source];
    }
    return ['answer' => "I can answer questions about sessions, search results, Day 1 retention, or the most reached screen. I only report patterns in the selected range; I can't establish causes.", 'source' => 'Supported aggregate questions · ' . $source];
}
