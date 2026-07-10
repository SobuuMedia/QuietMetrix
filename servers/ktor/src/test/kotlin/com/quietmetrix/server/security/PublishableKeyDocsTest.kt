package com.quietmetrix.server.security

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the publishable-API-key threat-model framing in the public docs.
 *
 * The API key shipped in client apps (web, F-Droid APK, etc.) is publishable: it is
 * write-only and project-scoped. Docs must say so explicitly and must NOT call it a secret.
 * See docs/security/publishable-api-key.md.
 */
class PublishableKeyDocsTest {

    @Test
    fun docs_frame_the_api_key_as_publishable_and_write_only() {
        val doc = readDoc("docs/security/publishable-api-key.md")
        assertTrue(doc.contains("publishable", ignoreCase = true))
        assertTrue(doc.contains("write-only", ignoreCase = true))
        assertTrue(doc.contains("project-scoped", ignoreCase = true))
        assertTrue(doc.contains("rotate", ignoreCase = true))
    }

    @Test
    fun docs_describe_fdroid_publishing_guidance() {
        val doc = readDoc("docs/security/publishable-api-key.md")
        assertTrue(doc.contains("F-Droid", ignoreCase = true))
        assertTrue(doc.contains("dedicated", ignoreCase = true))
    }

    @Test
    fun docs_describe_privacy_and_abuse_defense_controls() {
        val doc = readDoc("docs/security/publishable-api-key.md")
        assertTrue(doc.contains("per-IP", ignoreCase = true))
        assertTrue(doc.contains("per-install", ignoreCase = true))
        assertTrue(doc.contains("quarantine", ignoreCase = true))
        assertTrue(doc.contains("salt", ignoreCase = true))
    }

    @Test
    fun api_reference_uses_publishable_framing() {
        val doc = readDoc("docs/api-reference.md")
        assertTrue(doc.contains("publishable", ignoreCase = true))
        assertTrue(doc.contains("write-only", ignoreCase = true))
    }

    @Test
    fun readme_authentication_section_uses_publishable_framing() {
        val doc = readDoc("README.md")
        assertTrue(doc.contains("publishable", ignoreCase = true))
        assertTrue(doc.contains("write-only", ignoreCase = true))
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