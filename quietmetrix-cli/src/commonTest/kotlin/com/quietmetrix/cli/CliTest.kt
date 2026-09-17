package com.quietmetrix.cli

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 7 — CLI orchestration. No network: [FakeHttpClient] stands in for the real Ktor
 * adapter, so these prove the exit-code contract and stdout JSON shape an agent parses.
 */
class CliTest {

    private fun captured(): Pair<MutableList<String>, MutableList<String>> = mutableListOf<String>() to mutableListOf()

    @Test
    fun helpExitsZeroAndPrintsUsage() = runTest {
        val cli = QuietMetrixCli(FakeHttpClient())
        val (out, err) = captured()
        val code = cli.run(emptyList(), emptyMap(), out::add, err::add)
        assertEquals(ExitCode.OK, code)
        assertTrue(out.single().contains("quietmetrix"))
        assertTrue(err.isEmpty())
    }

    @Test
    fun usageErrorExitsOne() = runTest {
        val cli = QuietMetrixCli(FakeHttpClient())
        val (out, err) = captured()
        val code = cli.run(listOf("bogus"), emptyMap(), out::add, err::add)
        assertEquals(ExitCode.USAGE, code)
        assertTrue(err.single().contains("Unknown command"))
    }

    @Test
    fun projectCreateWithoutTokenExitsTwo() = runTest {
        val cli = QuietMetrixCli(FakeHttpClient())
        val (out, err) = captured()
        val code = cli.run(
            listOf("project", "create", "--url", "https://qm.example.com", "--name", "App"),
            emptyMap(),
            out::add, err::add,
        )
        assertEquals(ExitCode.AUTH, code)
        assertTrue(err.single().contains("QUIETMETRIX_TOKEN"))
        assertTrue(out.isEmpty())
    }

    @Test
    fun projectCreateSuccessPrintsKeyAndTrackingEndpoint() = runTest {
        val http = FakeHttpClient()
        http.enqueue(201, """{"id":"proj_1","name":"App","api_key":"qm_ak_abc","api_key_last4":"kabc","message":"stored"}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf("project", "create", "--url", "https://qm.example.com/", "--name", "App"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz"),
            out::add, err::add,
        )

        assertEquals(ExitCode.OK, code)
        assertTrue(err.isEmpty())
        val printed = out.single()
        assertTrue(printed.contains("qm_ak_abc"))
        assertTrue(printed.contains("https://qm.example.com/api/v1/track"))

        val request = http.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://qm.example.com/api/v1/projects", request.url)
        assertEquals("Bearer qm_pat_xyz", request.headers["Authorization"])
        assertTrue(request.body!!.contains("\"name\":\"App\""))
    }

    @Test
    fun projectCreateSendsIdempotencyKeyHeaderWhenGiven() = runTest {
        val http = FakeHttpClient()
        http.enqueue(201, """{"id":"proj_1","name":"App","api_key":"qm_ak_abc"}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        cli.run(
            listOf("project", "create", "--url", "https://qm.example.com", "--name", "App", "--idempotency-key", "req-1"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz"),
            out::add, err::add,
        )

        assertEquals("req-1", http.requests.single().headers["Idempotency-Key"])
    }

    @Test
    fun projectCreateOnUnauthorizedExitsTwo() = runTest {
        val http = FakeHttpClient()
        http.enqueue(401, """{"error":"unauthorized","message":"Invalid or expired token"}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf("project", "create", "--url", "https://qm.example.com", "--name", "App"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_revoked"),
            out::add, err::add,
        )

        assertEquals(ExitCode.AUTH, code)
        assertTrue(out.isEmpty())
    }

    @Test
    fun projectCreateOnServerErrorExitsThree() = runTest {
        val http = FakeHttpClient()
        http.enqueue(500, """{"error":"internal_error","message":"boom"}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf("project", "create", "--url", "https://qm.example.com", "--name", "App"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz"),
            out::add, err::add,
        )

        assertEquals(ExitCode.SERVER, code)
    }

    @Test
    fun projectCreateOnNetworkFailureExitsThree() = runTest {
        val http = FakeHttpClient()
        http.enqueueFailure(RuntimeException("connection refused"))
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf("project", "create", "--url", "https://qm.example.com", "--name", "App"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz"),
            out::add, err::add,
        )

        assertEquals(ExitCode.SERVER, code)
        assertTrue(err.single().contains("connection refused"))
    }

    @Test
    fun whoamiSuccessPrintsBodyAndExitsZero() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"version":"1.2.3","debug":false}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(listOf("whoami", "--url", "https://qm.example.com"), emptyMap(), out::add, err::add)

        assertEquals(ExitCode.OK, code)
        assertEquals("""{"version":"1.2.3","debug":false}""", out.single())
        assertEquals("https://qm.example.com/api/v1/_meta", http.requests.single().url)
    }

    @Test
    fun projectListWithoutTokenExitsTwo() = runTest {
        val cli = QuietMetrixCli(FakeHttpClient())
        val (out, err) = captured()
        val code = cli.run(listOf("project", "list", "--url", "https://qm.example.com"), emptyMap(), out::add, err::add)
        assertEquals(ExitCode.AUTH, code)
    }

    @Test
    fun projectListSuccessPrintsServerJsonVerbatim() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"projects":[{"id":"proj_1","name":"App"}],"total":1}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf("project", "list", "--url", "https://qm.example.com"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz"),
            out::add, err::add,
        )

        assertEquals(ExitCode.OK, code)
        assertEquals("""{"projects":[{"id":"proj_1","name":"App"}],"total":1}""", out.single())
    }

    // --- analytics / funnel ------------------------------------------------------------------

    @Test
    fun analyticsAggregatesWithoutTokenExitsTwo() = runTest {
        val cli = QuietMetrixCli(FakeHttpClient())
        val (out, err) = captured()
        val code = cli.run(
            listOf("analytics", "aggregates", "--url", "https://qm.example.com", "--project", "proj_1"),
            emptyMap(), out::add, err::add,
        )
        assertEquals(ExitCode.AUTH, code)
        assertTrue(err.single().contains("QUIETMETRIX_TOKEN"))
    }

    @Test
    fun analyticsAggregatesSuccessPrintsServerJsonAndSendsRangeParams() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"top_events":[]}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf("analytics", "aggregates", "--url", "https://qm.example.com", "--project", "proj_1", "--range", "1d"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz"),
            out::add, err::add,
        )

        assertEquals(ExitCode.OK, code)
        assertEquals("""{"top_events":[]}""", out.single())
        val request = http.requests.single()
        assertTrue(request.url.startsWith("https://qm.example.com/api/v1/projects/proj_1/aggregates?"))
        assertTrue(request.url.contains("range=1d"))
        assertEquals("Bearer qm_pat_xyz", request.headers["Authorization"])
    }

    @Test
    fun analyticsAggregatesForbiddenExitsTwo() = runTest {
        val http = FakeHttpClient()
        http.enqueue(403, """{"error":"forbidden","message":"This token cannot read analytics"}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf("analytics", "aggregates", "--url", "https://qm.example.com", "--project", "proj_1"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_readonly"),
            out::add, err::add,
        )

        assertEquals(ExitCode.AUTH, code)
        assertTrue(out.isEmpty())
    }

    @Test
    fun funnelListSuccessPrintsServerJsonVerbatim() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"funnels":[{"funnel_key":"signup"}]}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf("funnel", "list", "--url", "https://qm.example.com", "--project", "proj_1"),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz"),
            out::add, err::add,
        )

        assertEquals(ExitCode.OK, code)
        assertEquals("""{"funnels":[{"funnel_key":"signup"}]}""", out.single())
        assertEquals("https://qm.example.com/api/v1/projects/proj_1/funnels", http.requests.single().url)
    }

    @Test
    fun funnelResultsSendsFunnelKeyInThePathAndRangeInTheQuery() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"entered":10,"converted":4}""")
        val cli = QuietMetrixCli(http)
        val (out, err) = captured()

        val code = cli.run(
            listOf(
                "funnel", "results",
                "--url", "https://qm.example.com", "--project", "proj_1",
                "--funnel", "signup", "--range", "30d",
            ),
            mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz"),
            out::add, err::add,
        )

        assertEquals(ExitCode.OK, code)
        assertEquals("""{"entered":10,"converted":4}""", out.single())
        val url = http.requests.single().url
        assertTrue(url.startsWith("https://qm.example.com/api/v1/projects/proj_1/funnels/signup/results?"))
        assertTrue(url.contains("range=30d"))
    }
}
