package com.quietmetrix.server.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertTrue

/** Paid/live calls require explicit opt-in and the exact configured model. */
class AiLiveSmokeTest {
    @Test
    @EnabledIfEnvironmentVariable(named="QM_AI_LIVE_SMOKE",matches="true")
    fun `explicitly configured live provider answers`() {
        val config=AiConfig.fromEnvironment()
        assertTrue(config.enabled(false))
        val provider=ProtocolAiProvider(config)
        val reply=provider.complete(provider.initial("Reply as plain text. Do not use tools.",emptyList(),"Say: smoke test passed"),false,60)
        assertTrue(reply.text.isNotBlank())
        println("Live smoke passed: ${config.provider} / ${config.model}")
    }
}
