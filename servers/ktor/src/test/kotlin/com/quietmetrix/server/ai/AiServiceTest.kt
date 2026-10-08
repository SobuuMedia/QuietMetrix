package com.quietmetrix.server.ai

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Orchestration tests prove forbidden calls stay at zero and only bound reportable tools reach models. */
class AiServiceTest {
    private val config=AiConfig(model="fixture-private-model",apiKey="private-key",rates=AiRates(100000,200000,50000))
    private val snapshot=buildJsonObject { put("anchor","2026-10-04");put("sessions",buildJsonObject { put("current",buildJsonObject { put("total",20) }) }) }
    private val question=AiRequest(question="sessions",operation_id="fixture-op")
    private val summary="""{"choices":[{"message":{"role":"assistant","content":"Observed 20 sessions."}}],"usage":{"prompt_tokens":100,"completion_tokens":20}}"""
    private fun ledger(): AiAccounting = mockk<AiAccounting>().also { ledger ->
        every { ledger.cached(any(),any()) } returns null
        every { ledger.reserve(any(),any(),any(),any(),any(),any()) } returns AiReservation("fixture")
        every { ledger.finish(any(),any(),any(),any()) } just Runs
    }
    @Test fun `unavailable exhausted and accounting outages never contact providers`() {
        for(code in listOf("ai_unavailable","ai_allowance_exhausted","ai_accounting_unavailable")) {
            val ledger=ledger();every { ledger.reserve(any(),any(),any(),any(),any(),any()) } throws AiDenied(code)
            var calls=0
            val service=AiService(config,ledger) { calls++;ProtocolAiProvider(config) }
            assertEquals(code,assertFailsWith<AiDenied> { service.generate("owner","p",question,false,snapshot,"computed") }.code)
            assertEquals("computed",service.generate("owner","p",question,true,snapshot,"computed")["text"]!!.jsonPrimitive.content)
            assertEquals(0,calls)
        }
    }
    @Test fun `cached summaries are free even when exhausted`() {
        val ledger=ledger(); val cached=buildJsonObject { put("text","cached");put("mode","ai") }
        every { ledger.cached("owner",any()) } returns cached
        val service=AiService(config,ledger) { error("must not call") }
        assertEquals(cached,service.generate("owner","p",question,true,snapshot,"computed"))
        verify(exactly=0) { ledger.reserve(any(),any(),any(),any(),any(),any()) }
    }
    @Test fun `cross project arguments and unknown tools return no analytics`() {
        val ledger=ledger();var calls=0;var followup=""
        val tools="""{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"1","type":"function","function":{"name":"sessions","arguments":"{\"project_id\":\"other\"}"}},{"id":"2","type":"function","function":{"name":"sql","arguments":"{}"}}]}}],"usage":{"prompt_tokens":100,"completion_tokens":20}}"""
        val provider=ProtocolAiProvider(config,AiTransport { _,_,body,_ -> calls++;if(calls==1)200 to tools else { followup=body.toString();200 to summary } })
        val result=AiService(config,ledger) { provider }.generate("owner","p",question,false,snapshot,"computed")
        assertTrue(result["sources"]!!.jsonArray.isEmpty())
        assertTrue(followup.contains("unsupported_tool_or_arguments"))
        assertFalse(followup.contains("\\\"total\\\":20"))
        assertFalse(followup.contains("fixture-private-model",ignoreCase=false) && followup.substringAfter("messages").contains("fixture-private-model"))
        assertEquals(2,calls)
    }
    @Test fun `explicit tool rejection gets exactly one bounded snapshot fallback`() {
        val ledger=ledger();var calls=0;var body=""
        val provider=ProtocolAiProvider(config,AiTransport { _,_,request,_ -> calls++;body=request.toString();if(calls==1)400 to "tools unsupported" else 200 to summary })
        val result=AiService(config,ledger) { provider }.generate("owner","p",question,false,snapshot,"computed")
        assertEquals(2,calls);assertEquals("Observed 20 sessions.",result["answer"]!!.jsonPrimitive.content)
        assertTrue(body.contains("Reportable aggregate snapshot"))
        assertFalse(Json.parseToJsonElement(body).jsonObject.containsKey("tools"))
        verify { ledger.finish(any(),any(),config.rates!!.reservation,null) }
    }
    @Test fun `provider failure frees count retains unknown costs and computes insights`() {
        val ledger=ledger();var calls=0
        val service=AiService(config,ledger) { ProtocolAiProvider(config,AiTransport { _,_,_,_ -> calls++;401 to "private error" }) }
        assertEquals("ai_provider_failed",assertFailsWith<AiDenied> { service.generate("owner","p",question,false,snapshot,"computed") }.code)
        assertEquals("computed",service.generate("owner","p",question,true,snapshot,"computed")["text"]!!.jsonPrimitive.content)
        assertEquals(2,calls)
        verify(exactly=2) { ledger.finish(any(),null,config.rates!!.reservation,any()) }
    }
    @Test fun `three rounds and four calls are a hard stop`() {
        val ledger=ledger();var calls=0
        val raw="""{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"1","type":"function","function":{"name":"sessions","arguments":"{}"}}]}}],"usage":{"prompt_tokens":100,"completion_tokens":20}}"""
        val service=AiService(config,ledger) { ProtocolAiProvider(config,AiTransport { _,_,_,_ -> calls++;200 to raw }) }
        assertEquals("ai_provider_failed",assertFailsWith<AiDenied> { service.generate("owner","p",question,false,snapshot,"computed") }.code)
        assertEquals(4,calls)
        verify { ledger.finish(any(),null,56L,null) }
    }
}
