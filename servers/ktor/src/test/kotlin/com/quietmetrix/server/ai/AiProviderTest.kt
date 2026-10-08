package com.quietmetrix.server.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Protocol fixtures exercise native continuations, usage and sanitized failures. */
class AiProviderTest {
    private val fixtures = mapOf(
        "openai" to """{"choices":[{"message":{"role":"assistant","content":null,"reasoning_content":"opaque","tool_calls":[{"id":"c1","type":"function","function":{"name":"sessions","arguments":"{}"}}]}}],"usage":{"prompt_tokens":100,"completion_tokens":20,"prompt_tokens_details":{"cached_tokens":30}}}""",
        "gemini" to """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"id":"c1","name":"sessions","args":{}},"thoughtSignature":"signed"}]}}],"usageMetadata":{"promptTokenCount":100,"candidatesTokenCount":10,"thoughtsTokenCount":10,"cachedContentTokenCount":30}}""",
        "anthropic" to """{"content":[{"type":"thinking","thinking":"opaque","signature":"signed"},{"type":"tool_use","id":"c1","name":"sessions","input":{}}],"usage":{"input_tokens":70,"output_tokens":20,"cache_read_input_tokens":30}}""",
    )
    @Test fun `native tools preserve metadata and billable reasoning usage`() {
        for((kind, fixture) in fixtures) {
            val provider = ProtocolAiProvider(AiConfig(provider=kind, model="fixture", apiKey="secret"), AiTransport { _,_,_,_ -> 200 to fixture })
            val messages = provider.initial("system", emptyList(), "question")
            val reply = provider.complete(messages, true, 10)
            assertEquals(AiUsage(100,20,30), reply.usage)
            assertEquals("sessions", reply.calls.single().name)
            provider.continueWith(messages, reply, listOf(buildJsonObject { put("total", 20) }))
            assertTrue(messages.toString().contains(if(kind=="openai") "opaque" else "signed"))
            assertTrue(messages.toString().contains("c1"))
        }
    }
    @Test fun `DeepSeek flat cache usage reconciles as cached input`() {
        val raw=fixtures["openai"]!!.replace("\"prompt_tokens_details\":{\"cached_tokens\":30}","\"prompt_cache_hit_tokens\":30")
        assertEquals(AiUsage(100,20,30),ProtocolAiProvider(AiConfig()).decode(Json.parseToJsonElement(raw).jsonObject).usage)
    }
    @Test fun `plain summaries and malformed replies`() {
        val fixtures = mapOf("openai" to """{"choices":[{"message":{"role":"assistant","content":"Observed 20 sessions."}}]}""", "gemini" to """{"candidates":[{"content":{"role":"model","parts":[{"text":"Observed 20 sessions."}]}}]}""", "anthropic" to """{"content":[{"type":"text","text":"Observed 20 sessions."}]}""")
        for((kind,raw) in fixtures) {
            val p = ProtocolAiProvider(AiConfig(provider=kind))
            assertEquals("Observed 20 sessions.",p.decode(Json.parseToJsonElement(raw).jsonObject).text)
            assertFails { p.decode(buildJsonObject {}) }
        }
    }
    @Test fun `only explicit tool rejection permits snapshot fallback`() {
        for(kind in fixtures.keys) for(status in listOf(400,401,429,500)) {
            val p = ProtocolAiProvider(AiConfig(provider=kind,model="fixture"), AiTransport { _,_,_,_ -> status to "tools unsupported" })
            val failure = assertFailsWith<AiProviderFailure> { p.complete(p.initial("s",emptyList(),"q"),true,10) }
            assertEquals(status==400,failure.unsupportedTools)
        }
    }
    @Test fun `timeout and malformed tool arguments fail safely`() {
        val p=ProtocolAiProvider(AiConfig(),AiTransport { _,_,_,_ -> throw java.net.http.HttpTimeoutException("fixture") })
        assertFailsWith<java.net.http.HttpTimeoutException> { p.complete(p.initial("s",emptyList(),"q"),true,1) }
        val raw=fixtures["openai"]!!.replace("\\\"{}\\\"","oops")
        assertEquals("sessions", ProtocolAiProvider(AiConfig()).decode(Json.parseToJsonElement(raw).jsonObject).calls.single().name)
    }
}
