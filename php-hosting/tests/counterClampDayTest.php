<?php

/**
 * Unit tests for counterClampDay() -- mirrors
 * servers/ktor/.../counters/CounterIngestProcessor.kt's clampDay(). No DB required: this is
 * the pure day-window logic shared by the route handler.
 *
 * Run: php php-hosting/tests/counterClampDayTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/counters.php';

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

$today = '2026-09-04';

check('a same-day value passes through unchanged', $today, counterClampDay($today, $today));
check('a day within the window passes through unchanged', '2026-09-03', counterClampDay('2026-09-03', $today));
check('a day in the future is clamped to today', $today, counterClampDay('2026-09-14', $today));
check('a day far in the past is clamped to today minus two days', '2026-09-02', counterClampDay('2026-08-01', $today));
check('exactly today minus two days passes through unchanged', '2026-09-02', counterClampDay('2026-09-02', $today));
check('a malformed day string falls back to today', $today, counterClampDay('not-a-date', $today));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all counter-clamp-day assertions passed\n";
