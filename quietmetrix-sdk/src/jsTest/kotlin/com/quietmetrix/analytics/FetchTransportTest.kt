package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.transport.platformGet
import com.quietmetrix.analytics.internal.transport.platformPost
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The browser transport sends the existing JSON protocol directly, without Ktor. */
class FetchTransportTest {
    private val globals: dynamic = js("globalThis")
    private val originalFetch: dynamic = globals.fetch

    @AfterTest
    fun restoreFetch() { globals.fetch = originalFetch }

    @Test
    fun postPreservesJsonAndHeaders() = runTest {
        var called = false
        globals.fetch = { url: String, init: dynamic ->
            called = true
            assertEquals("https://fixture.invalid/api/v2/counters", url)
            assertEquals("POST", init.method)
            assertEquals("fixture-key", init.headers["X-QM-Api-Key"])
            assertEquals("application/json", init.headers["Content-Type"])
            assertEquals("{\"schema\":2}", init.body)
            Promise.resolve<dynamic>(js("new Response('', {status: 202})"))
        }
        assertTrue(platformPost("https://fixture.invalid/api/v2/counters", "fixture-key", "{\"schema\":2}"))
        assertTrue(called)
    }

    @Test
    fun getReturnsBodyAndUsesApiKey() = runTest {
        globals.fetch = { _: String, init: dynamic ->
            assertEquals("GET", init.method)
            assertEquals("fixture-key", init.headers["X-QM-Api-Key"])
            Promise.resolve<dynamic>(js("new Response('{\"capabilities\":[]}', {status: 200})"))
        }
        assertEquals("{\"capabilities\":[]}", platformGet("https://fixture.invalid/meta", "fixture-key"))
    }

    @Test
    fun errorsAndRejectedRequestsRemainNonThrowing() = runTest {
        globals.fetch = { _: String, _: dynamic -> Promise.resolve<dynamic>(js("new Response('', {status: 400})")) }
        assertFalse(platformPost("https://fixture.invalid/counters", "key", "{}"))
        assertNull(platformGet("https://fixture.invalid/meta", "key"))
        globals.fetch = { _: String, _: dynamic -> Promise.reject(Exception("offline")) }
        assertFalse(platformPost("https://fixture.invalid/counters", "key", "{}"))
        assertNull(platformGet("https://fixture.invalid/meta", "key"))
    }

    @Test
    fun cancellationAbortsTheOutstandingRequest() = runTest {
        var signal: dynamic = null
        globals.fetch = { _: String, init: dynamic ->
            signal = init.signal
            Promise<dynamic> { _, _ -> }
        }
        val job = launch { platformGet("https://fixture.invalid/meta", "key") }
        runCurrent()
        assertFalse(signal.aborted as Boolean)
        job.cancelAndJoin()
        assertTrue(signal.aborted as Boolean)
    }
}
