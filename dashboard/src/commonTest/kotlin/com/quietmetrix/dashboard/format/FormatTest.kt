package com.quietmetrix.dashboard.format

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {

    @Test
    fun formatCountGroupsThousands() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1,000", formatCount(1_000))
        assertEquals("12,345", formatCount(12_345))
        assertEquals("123,456", formatCount(123_456))
        assertEquals("1,234,567", formatCount(1_234_567))
    }

    @Test
    fun formatBucketLabelKeepsDailyAndCollapsesHourly() {
        assertEquals("2026-06-28", formatBucketLabel("2026-06-28", hourly = false))
        assertEquals("14:00", formatBucketLabel("2026-06-28T14", hourly = true))
        assertEquals("09:00", formatBucketLabel("2026-06-28T09", hourly = true))
    }

    @Test
    fun formatCountHandlesNegatives() {
        assertEquals("-1,234", formatCount(-1_234))
        assertEquals("0", formatCount(0L))
    }

    @Test
    fun formatDurationSeconds() {
        assertEquals("0s", formatDuration(0))
        assertEquals("45s", formatDuration(45))
        assertEquals("59s", formatDuration(59))
    }

    @Test
    fun formatDurationMinutes() {
        assertEquals("1m 0s", formatDuration(60))
        assertEquals("1m 5s", formatDuration(65))
        assertEquals("5m 30s", formatDuration(330))
    }

    @Test
    fun formatDurationHours() {
        assertEquals("1h 0m", formatDuration(3_600))
        assertEquals("1h 1m", formatDuration(3_665))
        assertEquals("2h 30m", formatDuration(9_000))
    }

    @Test
    fun formatMinorUnitsToTwoDecimals() {
        assertEquals("4.99", formatMinorUnits(499))
        assertEquals("0.00", formatMinorUnits(0))
        assertEquals("100.00", formatMinorUnits(10000))
        assertEquals("-4.99", formatMinorUnits(-499))
    }

    @Test
    fun formatFloatOneDecimal() {
        assertEquals("0.0", formatFloat(0f))
        assertEquals("3.5", formatFloat(3.5f))
        assertEquals("3.0", formatFloat(3.0f))
        assertEquals("12.0", formatFloat(12f))
        assertEquals("-1.5", formatFloat(-1.5f))
    }

    @Test
    fun formatFloatTwoDecimals() {
        assertEquals("2.50", formatFloat(2.5f, decimals = 2))
        assertEquals("3.45", formatFloat(3.45f, decimals = 2))
        assertEquals("0.00", formatFloat(0f, decimals = 2))
    }

    @Test
    fun formatDateOnlyParsesIso() {
        assertEquals("", formatDateOnly(null))
        assertEquals("", formatDateOnly(""))
        assertEquals("Jan 15, 2024", formatDateOnly("2024-01-15T10:30:00"))
        assertEquals("Jan 15, 2024", formatDateOnly("2024-01-15 10:30:00"))
        assertEquals("Jan 15, 2024", formatDateOnly("2024-01-15"))
        assertEquals("Dec 1, 2024", formatDateOnly("2024-12-01"))
    }

    @Test
    fun formatDateOnlyFallsBackToRawDatePart() {
        assertEquals("garbage", formatDateOnly("garbage"))
    }

    @Test
    fun formatPercentRoundsToWholeNumberByDefault() {
        assertEquals("0%", formatPercent(0.0))
        assertEquals("50%", formatPercent(0.5))
        assertEquals("100%", formatPercent(1.0))
        assertEquals("67%", formatPercent(0.6666666666666666))
        assertEquals("1%", formatPercent(0.006))
    }

    @Test
    fun formatPercentSupportsDecimals() {
        assertEquals("66.7%", formatPercent(0.6666666666666666, decimals = 1))
        assertEquals("0.0%", formatPercent(0.0, decimals = 1))
    }

    @Test
    fun formatPercentClampsOutOfRangeFractions() {
        // Small negative/over-1 rounding artifacts from division must not render as -1%/101%.
        assertEquals("0%", formatPercent(-0.001))
        assertEquals("100%", formatPercent(1.004))
    }
}
