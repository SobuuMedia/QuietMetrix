package com.quietmetrix.mcp

import com.quietmetrix.cli.QuietMetrixClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 8 — the MCP protocol surface. No network and no stdio: [FakeHttpClient] stands in
 * for the real Ktor adapter, and lines are handed to [McpServer.handle] directly, matching
 * how Main.kt would after buffering a line off stdin.
 */
class McpServerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun server(http: FakeHttpClient = FakeHttpClient(), env: Map<String, String> = emptyMap()): McpServer =
        McpServer(QuietMetrixClient(http), env)

    @Test
    fun initializeReturnsProtocolVersionAndServerInfo() = runTest {
        val response = server().handle("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertTrue(result.containsKey("protocolVersion"))
        assertTrue(result["capabilities"]!!.jsonObject.containsKey("tools"))
        assertEquals("quietmetrix", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun notificationsInitializedGetsNoResponse() = runTest {
        val response = server().handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertNull(response)
    }

    @Test
    fun toolsListReturnsAllTools() = runTest {
        val response = server().handle("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")!!
        val tools = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        val names = tools.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(
            setOf(
                "quietmetrix_create_project", "quietmetrix_list_projects",
                "quietmetrix_get_aggregates", "quietmetrix_get_transitions",
                "quietmetrix_get_sessions", "quietmetrix_get_retention",
                "quietmetrix_list_funnels", "quietmetrix_get_funnel_results",
            ),
            names.toSet(),
        )
    }

    @Test
    fun toolsListDeclaresProjectIdAsRequiredForAggregates() = runTest {
        val response = server().handle("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")!!
        val tools = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        val aggregates = tools.first { it.jsonObject["name"]!!.jsonPrimitive.content == "quietmetrix_get_aggregates" }
        val required = aggregates.jsonObject["inputSchema"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("project_id"), required)
        assertTrue(aggregates.jsonObject["inputSchema"]!!.jsonObject["properties"]!!.jsonObject.containsKey("range"))
    }

    @Test
    fun toolsListDeclaresProjectIdAndFunnelKeyAsRequiredForFunnelResults() = runTest {
        val response = server().handle("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")!!
        val tools = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        val results = tools.first { it.jsonObject["name"]!!.jsonPrimitive.content == "quietmetrix_get_funnel_results" }
        val required = results.jsonObject["inputSchema"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(setOf("project_id", "funnel_key"), required.toSet())
    }

    @Test
    fun toolsListDeclaresNameAsRequiredForCreateProject() = runTest {
        val response = server().handle("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")!!
        val tools = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        val createTool = tools.first { it.jsonObject["name"]!!.jsonPrimitive.content == "quietmetrix_create_project" }
        val required = createTool.jsonObject["inputSchema"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("name"), required)
    }

    @Test
    fun unknownMethodReturnsJsonRpcError() = runTest {
        val response = server().handle("""{"jsonrpc":"2.0","id":3,"method":"bogus"}""")!!
        val error = json.parseToJsonElement(response).jsonObject["error"]!!.jsonObject
        assertEquals(-32601, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun malformedJsonReturnsParseError() = runTest {
        val response = server().handle("not json")!!
        val error = json.parseToJsonElement(response).jsonObject["error"]!!.jsonObject
        assertEquals(-32700, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun createProjectToolCallSucceeds() = runTest {
        val http = FakeHttpClient()
        http.enqueue(201, """{"id":"proj_1","name":"App","api_key":"qm_ak_abc","api_key_last4":"kabc"}""")
        val response = server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"quietmetrix_create_project","arguments":{"url":"https://qm.example.com","name":"App"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertTrue(result["isError"] == null)
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("qm_ak_abc"))
        assertTrue(text.contains("https://qm.example.com/api/v1/track"))

        val request = http.requests.single()
        assertEquals("Bearer qm_pat_xyz", request.headers["Authorization"])
    }

    @Test
    fun createProjectToolCallWithoutTokenReturnsToolError() = runTest {
        val response = server(FakeHttpClient(), emptyMap()).handle(
            """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"quietmetrix_create_project","arguments":{"url":"https://qm.example.com","name":"App"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("QUIETMETRIX_TOKEN"))
    }

    @Test
    fun createProjectToolCallFallsBackToEnvUrl() = runTest {
        val http = FakeHttpClient()
        http.enqueue(201, """{"id":"proj_1","name":"App","api_key":"qm_ak_abc"}""")
        server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz", "QUIETMETRIX_URL" to "https://qm.example.com")).handle(
            """{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"quietmetrix_create_project","arguments":{"name":"App"}}}"""
        )
        assertEquals("https://qm.example.com/api/v1/projects", http.requests.single().url)
    }

    @Test
    fun listProjectsToolCallReturnsServerBody() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"projects":[],"total":0}""")
        val response = server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"quietmetrix_list_projects","arguments":{"url":"https://qm.example.com"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("""{"projects":[],"total":0}""", text)
    }

    @Test
    fun unknownToolNameReturnsToolError() = runTest {
        val response = server(FakeHttpClient(), mapOf("QUIETMETRIX_TOKEN" to "x")).handle(
            """{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"bogus_tool","arguments":{}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun authErrorFromServerSurfacesAsToolError() = runTest {
        val http = FakeHttpClient()
        http.enqueue(401, """{"error":"unauthorized","message":"Invalid or expired token"}""")
        val response = server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_revoked")).handle(
            """{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"quietmetrix_create_project","arguments":{"url":"https://qm.example.com","name":"App"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
    }

    // --- analytics / funnel tools ------------------------------------------------------------

    @Test
    fun getAggregatesToolCallSendsRangeAndReturnsServerBody() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"top_events":[]}""")
        val response = server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"quietmetrix_get_aggregates","arguments":{"url":"https://qm.example.com","project_id":"proj_1","range":"1d"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertTrue(result["isError"] == null)
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("""{"top_events":[]}""", text)

        val request = http.requests.single()
        assertTrue(request.url.startsWith("https://qm.example.com/api/v1/projects/proj_1/aggregates?"))
        assertTrue(request.url.contains("range=1d"))
    }

    @Test
    fun getAggregatesToolCallDefaultsRangeToSevenDays() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"top_events":[]}""")
        server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"quietmetrix_get_aggregates","arguments":{"url":"https://qm.example.com","project_id":"proj_1"}}}"""
        )
        assertTrue(http.requests.single().url.contains("range=7d"))
    }

    @Test
    fun getAggregatesToolCallWithoutProjectIdReturnsToolError() = runTest {
        val response = server(FakeHttpClient(), mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"quietmetrix_get_aggregates","arguments":{"url":"https://qm.example.com"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("project_id"))
    }

    @Test
    fun getAggregatesToolCallWithAnInvalidRangeReturnsToolError() = runTest {
        val response = server(FakeHttpClient(), mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"quietmetrix_get_aggregates","arguments":{"url":"https://qm.example.com","project_id":"proj_1","range":"1w"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun listFunnelsToolCallReturnsServerBody() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"funnels":[{"funnel_key":"signup"}]}""")
        val response = server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"quietmetrix_list_funnels","arguments":{"url":"https://qm.example.com","project_id":"proj_1"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("""{"funnels":[{"funnel_key":"signup"}]}""", text)
    }

    @Test
    fun getFunnelResultsToolCallSendsFunnelKeyInThePathAndRangeInTheQuery() = runTest {
        val http = FakeHttpClient()
        http.enqueue(200, """{"entered":10,"converted":4}""")
        val response = server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"quietmetrix_get_funnel_results","arguments":{"url":"https://qm.example.com","project_id":"proj_1","funnel_key":"signup","range":"30d"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("""{"entered":10,"converted":4}""", text)

        val url = http.requests.single().url
        assertTrue(url.startsWith("https://qm.example.com/api/v1/projects/proj_1/funnels/signup/results?"))
        assertTrue(url.contains("range=30d"))
    }

    @Test
    fun getFunnelResultsToolCallWithoutFunnelKeyReturnsToolError() = runTest {
        val response = server(FakeHttpClient(), mapOf("QUIETMETRIX_TOKEN" to "qm_pat_xyz")).handle(
            """{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"quietmetrix_get_funnel_results","arguments":{"url":"https://qm.example.com","project_id":"proj_1"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("funnel_key"))
    }

    @Test
    fun analyticsToolCallWithoutTokenReturnsToolError() = runTest {
        val response = server(FakeHttpClient(), emptyMap()).handle(
            """{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"name":"quietmetrix_get_aggregates","arguments":{"url":"https://qm.example.com","project_id":"proj_1"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("QUIETMETRIX_TOKEN"))
    }

    @Test
    fun analyticsToolForbiddenScopeSurfacesAsToolError() = runTest {
        val http = FakeHttpClient()
        http.enqueue(403, """{"error":"forbidden","message":"This token cannot read analytics"}""")
        val response = server(http, mapOf("QUIETMETRIX_TOKEN" to "qm_pat_readonly")).handle(
            """{"jsonrpc":"2.0","id":15,"method":"tools/call","params":{"name":"quietmetrix_get_aggregates","arguments":{"url":"https://qm.example.com","project_id":"proj_1"}}}"""
        )!!
        val result = json.parseToJsonElement(response).jsonObject["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
    }
}
