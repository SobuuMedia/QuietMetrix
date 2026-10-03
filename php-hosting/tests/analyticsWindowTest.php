<?php
require_once __DIR__ . '/../src/analyticsWindow.php';
$now = strtotime('2026-10-03T12:30:00Z');
$current = analyticsWindow(604800, null, '2026-10-03', $now);
$previous = analyticsWindow(604800, 'previous', '2026-10-03', $now);
if ($current['from_day'] !== '2026-09-27' || $current['to_day'] !== '2026-10-03' ||
    $previous['from_day'] !== '2026-09-20' || $previous['to_day'] !== '2026-09-26' || $previous['days'] !== 7) {
    throw new RuntimeException('Period dates overlap or have unequal lengths');
}
if (analyticsWindow(3600, 'previous', null, $now)['to_day'] !== '2026-10-02') throw new RuntimeException('Sub-day resolution incorrect');
foreach ([['other', null], [null, '2026-02-30'], [null, '2026-10-04'], [null, '2020-01-01']] as [$period, $anchor]) {
    try { analyticsWindow(604800, $period, $anchor, $now); } catch (InvalidArgumentException $e) { continue; }
    throw new RuntimeException('Invalid window accepted');
}
echo "Analytics window assertions passed\n";
