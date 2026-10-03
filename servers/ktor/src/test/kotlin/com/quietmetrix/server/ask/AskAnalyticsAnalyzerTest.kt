package com.quietmetrix.server.ask

import com.quietmetrix.server.persistence.CounterRepository.CounterCell
import kotlin.test.Test
import kotlin.test.assertContains

/** Ensures computed answers remain grounded in aggregate data and do not claim causes. */
class AskAnalyticsAnalyzerTest {
    @Test
    fun reportsExactSessionAggregate() {
        val answer = AskAnalyticsAnalyzer.answer("How many sessions?", listOf(CounterCell(mapOf("bucket" to "0_10s"), 12, 5)), emptyList(), emptyList(), emptyList(), emptyList(), "7 days")
        assertContains(answer.text, "12 sessions")
        assertContains(answer.source, "Sessions")
    }

    @Test
    fun unsupportedQuestionExplainsLimitsWithoutInventingCausality() {
        val answer = AskAnalyticsAnalyzer.answer("Why did revenue fall?", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "30 days")
        assertContains(answer.text, "can't establish causes")
        assertContains(answer.text, "sessions")
    }
}
