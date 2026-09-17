package com.quietmetrix.server.security

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the agent-driven-setup documentation (docs/agents/setup.md): the one-time
 * QUIETMETRIX_TOKEN credential must be named explicitly, the docs must warn against
 * committing it, and every supported SDK platform must be covered so an agent following
 * the doc knows where to write the config on any of them.
 */
class AgentSetupDocsTest {

    @Test
    fun docs_name_the_agent_token_env_var() {
        val doc = readDoc("docs/agents/setup.md")
        assertTrue(doc.contains("QUIETMETRIX_TOKEN"))
        assertTrue(doc.contains("qm_pat_"))
    }

    @Test
    fun docs_warn_against_committing_the_agent_token() {
        val doc = readDoc("docs/agents/setup.md")
        assertTrue(doc.contains("never", ignoreCase = true))
        assertTrue(doc.contains("commit", ignoreCase = true))
    }

    @Test
    fun docs_cover_every_sdk_platform() {
        val doc = readDoc("docs/agents/setup.md")
        assertTrue(doc.contains("Android"))
        assertTrue(doc.contains("iOS"))
        assertTrue(doc.contains("Web", ignoreCase = false) || doc.contains("JS"))
        assertTrue(doc.contains("JVM"))
        assertTrue(doc.contains("Desktop"))
    }

    @Test
    fun docs_distinguish_the_two_credentials() {
        val doc = readDoc("docs/agents/setup.md")
        assertTrue(doc.contains("qm_ak_"))
        assertTrue(doc.contains("qm_pat_"))
        assertTrue(doc.contains("publishable", ignoreCase = true))
    }

    @Test
    fun api_reference_documents_the_tokens_endpoints() {
        val doc = readDoc("docs/api-reference.md")
        assertTrue(doc.contains("/api/v1/tokens"))
        assertTrue(doc.contains("qm_pat_"))
    }

    private fun readDoc(relativePath: String): String {
        var dir = java.io.File(".").absoluteFile.parentFile
        var guard = 0
        while (dir != null && guard < 12) {
            if (java.io.File(dir, "settings.gradle.kts").exists()) break
            dir = dir.parentFile
            guard++
        }
        val file = java.io.File(dir, relativePath)
        assertTrue(file.exists(), "Could not locate $relativePath from repo root ${dir?.absolutePath}")
        return file.readText()
    }
}
