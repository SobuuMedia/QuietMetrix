package com.quietmetrix.dashboard.api

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Role/email recovery from a JWT for restored sessions (no stored user). */
@OptIn(ExperimentalEncodingApi::class)
class JwtDecodeTest {

    private fun jwt(payloadJson: String): String {
        val b64 = Base64.UrlSafe.encode(payloadJson.encodeToByteArray()).trimEnd('=')
        return "header.$b64.signature"
    }

    @Test
    fun decodesPhpStyleClaims() {
        val user = decodeUserFromJwt(jwt("""{"sub":"u1","email":"a@b.c","role":"developer"}"""))
        assertEquals("u1", user?.id)
        assertEquals("a@b.c", user?.email)
        assertEquals("developer", user?.role)
    }

    @Test
    fun decodesKtorStyleClaims() {
        val user = decodeUserFromJwt(jwt("""{"userId":"42","email":"k@b.c","role":"reviewer"}"""))
        assertEquals("42", user?.id)
        assertEquals("reviewer", user?.role)
    }

    @Test
    fun defaultsRoleToAdminWhenMissing() {
        val user = decodeUserFromJwt(jwt("""{"sub":"u1","email":"a@b.c"}"""))
        assertEquals(UserRole.ADMIN, user?.role)
    }

    @Test
    fun returnsNullForGarbage() {
        assertNull(decodeUserFromJwt("not-a-jwt"))
    }
}
