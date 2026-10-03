package com.quietmetrix.dashboard.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reducer coverage for Ask drawer send, loading, failure/retry and prefill flows. */
class AskDrawerTest {
    @Test
    fun sendsQuestionAndResolvesWithSource() {
        val sent = reduceAskDrawer(AskDrawerState(open = true), AskDrawerEvent.Submit("  How many sessions? "))
        assertTrue(sent.loading)
        assertEquals("", sent.draft)
        assertEquals(AskMessage(AskMessageRole.User, "How many sessions?"), sent.messages.single())
        val answered = reduceAskDrawer(sent, AskDrawerEvent.Answer("120 sessions", "Sessions · 7 days"))
        assertFalse(answered.loading)
        assertEquals("Sessions · 7 days", answered.messages.last().source)
    }

    @Test
    fun failedQuestionCanRetryAndCtaCanPrefill() {
        val prefilled = reduceAskDrawer(AskDrawerState(), AskDrawerEvent.Open("Why did checkout drop?"))
        assertEquals("Why did checkout drop?", prefilled.draft)
        val failed = reduceAskDrawer(reduceAskDrawer(prefilled, AskDrawerEvent.Submit(prefilled.draft)), AskDrawerEvent.Fail("Unavailable"))
        assertEquals("Why did checkout drop?", failed.retryQuestion)
        val retried = reduceAskDrawer(failed, AskDrawerEvent.Retry)
        assertTrue(retried.loading)
        assertFalse(retried.messages.any { it.role == AskMessageRole.Error })
    }

    @Test
    fun blankQuestionsDoNotCreateBubbles() {
        assertEquals(AskDrawerState(open = true), reduceAskDrawer(AskDrawerState(open = true), AskDrawerEvent.Submit("  ")))
    }
}
