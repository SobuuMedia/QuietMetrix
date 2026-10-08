package com.quietmetrix.dashboard.ui.screens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks invite input and the client configuration without network or stored credentials. */
class ManagementLogicTest {
    /** Valid invitations trim outer whitespace and reject malformed addresses. */
    @Test fun validatesInvitationEmail() {
        assertTrue(isInvitationEmailValid(" person+test@example.com "))
        for (email in listOf("", "person", "a@", "@example.com", "a b@example.com", "a@@example.com", "a@example", "a@example..com")) {
            assertFalse(isInvitationEmailValid(email), email)
        }
    }

    /** Configuration points at this server, preserves JSON escaping, and uses a token placeholder. */
    @Test fun buildsUsableMcpConfiguration() {
        val url = "https://example.com/a\"b"
        val server = Json.parseToJsonElement(mcpClientConfiguration(url)).jsonObject["mcpServers"]!!.jsonObject["quietmetrix"]!!.jsonObject
        assertEquals("npx", server["command"]!!.jsonPrimitive.content)
        assertEquals(listOf("-y", "@sobuumedia/quietmetrix-mcp"), server["args"]!!.jsonArray.map { it.jsonPrimitive.content })
        val env = server["env"]!!.jsonObject
        assertEquals(url, env["QUIETMETRIX_URL"]!!.jsonPrimitive.content)
        assertEquals("qm_pat_your_token_here", env["QUIETMETRIX_TOKEN"]!!.jsonPrimitive.content)
    }
}
