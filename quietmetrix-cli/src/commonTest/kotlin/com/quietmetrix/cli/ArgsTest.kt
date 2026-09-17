package com.quietmetrix.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArgsTest {

    @Test
    fun noArgsShowsHelp() {
        assertEquals(ParsedCommand.Help, parseArgs(emptyList(), emptyMap()))
    }

    @Test
    fun helpFlagShowsHelp() {
        assertEquals(ParsedCommand.Help, parseArgs(listOf("--help"), emptyMap()))
        assertEquals(ParsedCommand.Help, parseArgs(listOf("-h"), emptyMap()))
        assertEquals(ParsedCommand.Help, parseArgs(listOf("help"), emptyMap()))
    }

    @Test
    fun unknownCommandIsAUsageError() {
        assertIs<ParsedCommand.UsageError>(parseArgs(listOf("frobnicate"), emptyMap()))
    }

    @Test
    fun projectCreateParsesNameAndUrl() {
        val cmd = parseArgs(
            listOf("project", "create", "--url", "https://qm.example.com", "--name", "My App"),
            emptyMap(),
        )
        val create = assertIs<ParsedCommand.ProjectCreate>(cmd)
        assertEquals("https://qm.example.com", create.url)
        assertEquals("My App", create.name)
        assertEquals(null, create.description)
    }

    @Test
    fun projectCreateFallsBackToUrlEnvVar() {
        val cmd = parseArgs(
            listOf("project", "create", "--name", "My App"),
            mapOf("QUIETMETRIX_URL" to "https://qm.example.com"),
        )
        val create = assertIs<ParsedCommand.ProjectCreate>(cmd)
        assertEquals("https://qm.example.com", create.url)
    }

    @Test
    fun projectCreateWithoutUrlIsAUsageError() {
        val cmd = parseArgs(listOf("project", "create", "--name", "My App"), emptyMap())
        val error = assertIs<ParsedCommand.UsageError>(cmd)
        assertTrue(error.message.contains("--url"), "expected the error to mention --url, was: ${error.message}")
    }

    @Test
    fun projectCreateWithoutNameIsAUsageError() {
        val cmd = parseArgs(listOf("project", "create", "--url", "https://qm.example.com"), emptyMap())
        val error = assertIs<ParsedCommand.UsageError>(cmd)
        assertTrue(error.message.contains("--name"), "expected the error to mention --name, was: ${error.message}")
    }

    @Test
    fun projectCreateWithBlankNameIsAUsageError() {
        assertIs<ParsedCommand.UsageError>(
            parseArgs(listOf("project", "create", "--url", "https://qm.example.com", "--name", "  "), emptyMap())
        )
    }

    @Test
    fun projectCreateParsesDescriptionAndIdempotencyKey() {
        val cmd = parseArgs(
            listOf(
                "project", "create",
                "--url", "https://qm.example.com",
                "--name", "My App",
                "--description", "A test app",
                "--idempotency-key", "req-123",
            ),
            emptyMap(),
        )
        val create = assertIs<ParsedCommand.ProjectCreate>(cmd)
        assertEquals("A test app", create.description)
        assertEquals("req-123", create.idempotencyKey)
    }

    @Test
    fun projectListParsesJsonFlag() {
        val cmd = parseArgs(listOf("project", "list", "--url", "https://qm.example.com", "--json"), emptyMap())
        val list = assertIs<ParsedCommand.ProjectList>(cmd)
        assertTrue(list.json)
    }

    @Test
    fun whoamiRequiresUrl() {
        assertIs<ParsedCommand.UsageError>(parseArgs(listOf("whoami"), emptyMap()))
        val cmd = parseArgs(listOf("whoami", "--url", "https://qm.example.com"), emptyMap())
        assertEquals(ParsedCommand.Whoami("https://qm.example.com"), cmd)
    }

    @Test
    fun urlIsNormalizedDuringParsing() {
        val cmd = parseArgs(listOf("whoami", "--url", "https://qm.example.com/"), emptyMap())
        assertEquals(ParsedCommand.Whoami("https://qm.example.com"), cmd)
    }

    @Test
    fun missingValueForAFlagIsAUsageError() {
        assertIs<ParsedCommand.UsageError>(
            parseArgs(listOf("project", "create", "--url", "https://qm.example.com", "--name"), emptyMap())
        )
    }

    @Test
    fun unknownOptionIsAUsageError() {
        assertIs<ParsedCommand.UsageError>(
            parseArgs(listOf("project", "create", "--url", "https://qm.example.com", "--name", "x", "--bogus"), emptyMap())
        )
    }

    // --- analytics / funnel ------------------------------------------------------------------

    @Test
    fun analyticsAggregatesDefaultsRangeToSevenDays() {
        val cmd = parseArgs(listOf("analytics", "aggregates", "--url", "https://qm.example.com", "--project", "proj_1"), emptyMap())
        val aggregates = assertIs<ParsedCommand.AnalyticsAggregates>(cmd)
        assertEquals("proj_1", aggregates.projectId)
        assertEquals("7d", aggregates.range)
    }

    @Test
    fun analyticsAggregatesAcceptsAnExplicitRange() {
        val cmd = parseArgs(
            listOf("analytics", "aggregates", "--url", "https://qm.example.com", "--project", "proj_1", "--range", "30d"),
            emptyMap(),
        )
        assertEquals("30d", assertIs<ParsedCommand.AnalyticsAggregates>(cmd).range)
    }

    @Test
    fun analyticsCommandWithoutProjectIsAUsageError() {
        val error = assertIs<ParsedCommand.UsageError>(
            parseArgs(listOf("analytics", "aggregates", "--url", "https://qm.example.com"), emptyMap())
        )
        assertTrue(error.message.contains("--project"))
    }

    @Test
    fun analyticsCommandWithAnInvalidRangeIsAUsageError() {
        val error = assertIs<ParsedCommand.UsageError>(
            parseArgs(
                listOf("analytics", "aggregates", "--url", "https://qm.example.com", "--project", "proj_1", "--range", "1w"),
                emptyMap(),
            )
        )
        assertTrue(error.message.contains("--range"), "expected the error to mention --range, was: ${error.message}")
    }

    @Test
    fun analyticsTransitionsSessionsAndRetentionAllParse() {
        val transitions = assertIs<ParsedCommand.AnalyticsTransitions>(
            parseArgs(listOf("analytics", "transitions", "--url", "https://qm.example.com", "--project", "proj_1"), emptyMap())
        )
        assertEquals("7d", transitions.range)
        assertIs<ParsedCommand.AnalyticsSessions>(
            parseArgs(listOf("analytics", "sessions", "--url", "https://qm.example.com", "--project", "proj_1"), emptyMap())
        )
        assertIs<ParsedCommand.AnalyticsRetention>(
            parseArgs(listOf("analytics", "retention", "--url", "https://qm.example.com", "--project", "proj_1"), emptyMap())
        )
    }

    @Test
    fun funnelListParsesProjectId() {
        val cmd = parseArgs(listOf("funnel", "list", "--url", "https://qm.example.com", "--project", "proj_1"), emptyMap())
        assertEquals("proj_1", assertIs<ParsedCommand.FunnelList>(cmd).projectId)
    }

    @Test
    fun funnelResultsRequiresFunnelKey() {
        val error = assertIs<ParsedCommand.UsageError>(
            parseArgs(listOf("funnel", "results", "--url", "https://qm.example.com", "--project", "proj_1"), emptyMap())
        )
        assertTrue(error.message.contains("--funnel"))
    }

    @Test
    fun funnelResultsParsesFunnelKeyAndRange() {
        val cmd = parseArgs(
            listOf(
                "funnel", "results",
                "--url", "https://qm.example.com", "--project", "proj_1",
                "--funnel", "signup", "--range", "1d",
            ),
            emptyMap(),
        )
        val results = assertIs<ParsedCommand.FunnelResults>(cmd)
        assertEquals("signup", results.funnelKey)
        assertEquals("1d", results.range)
    }
}
