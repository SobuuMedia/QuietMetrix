<?php

/** Equal, adjacent inclusive UTC counter-date windows; instant metadata uses an exclusive end. */
function analyticsWindow(int $seconds, ?string $period = null, ?string $anchor = null, ?int $now = null): array {
    if (!in_array($period, [null, 'current', 'previous'], true) || $seconds < 1 || $seconds > 31536000) {
        throw new InvalidArgumentException('Invalid analytics period or length');
    }
    $today = new DateTimeImmutable(gmdate('Y-m-d', $now ?? time()), new DateTimeZone('UTC'));
    $end = $anchor === null ? $today : DateTimeImmutable::createFromFormat('!Y-m-d', $anchor, new DateTimeZone('UTC'));
    if ($end === false || ($anchor !== null && $end->format('Y-m-d') !== $anchor) || $end > $today || $end < $today->modify('-365 days')) {
        throw new InvalidArgumentException('anchor must be a valid UTC date within the past year');
    }
    $days = max(1, (int)ceil($seconds / 86400));
    $to = $period === 'previous' ? $end->modify("-$days days") : $end;
    $from = $to->modify('-' . ($days - 1) . ' days');
    return [
        'from_day' => $from->format('Y-m-d'), 'to_day' => $to->format('Y-m-d'), 'days' => $days,
        'from' => $from->format('Y-m-d\TH:i:s\Z'), 'to' => $to->modify('+1 day')->format('Y-m-d\TH:i:s\Z'),
    ];
}

/** Validates window query values before any route reads project counters. */
function requestAnalyticsWindow(int $seconds): array {
    try {
        foreach (['period', 'anchor'] as $key) {
            if (isset($_GET[$key]) && !is_string($_GET[$key])) throw new InvalidArgumentException('Invalid analytics window');
        }
        return analyticsWindow($seconds, $_GET['period'] ?? null, $_GET['anchor'] ?? null);
    } catch (InvalidArgumentException $error) {
        errorResponse(400, 'bad_request', $error->getMessage());
        exit;
    }
}

/** SDK cohort weeks are numbered in seven-day bins starting on January 1. */
function reportedCohortStart(string $label): ?string {
    if (preg_match('/^(\d{4})-W(\d{2})$/', $label, $matches)) {
        $week = (int)$matches[2];
        if ($week < 1 || $week > 53) return null;
        return (new DateTimeImmutable($matches[1] . '-01-01', new DateTimeZone('UTC')))
            ->modify('+' . (($week - 1) * 7) . ' days')->format('Y-m-d');
    }
    $date = DateTimeImmutable::createFromFormat('!Y-m-d', $label, new DateTimeZone('UTC'));
    return $date && $date->format('Y-m-d') === $label ? $label : null;
}
