package com.quietmetrix.server.util

import com.quietmetrix.server.plugins.configureStatusPages
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundedReceiveTest {
    @Test fun `chunked body exceeding one MiB is rejected without Content Length`() = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            configureStatusPages()
            routing { post("/body") { call.boundedBody(); call.respondText("accepted") } }
        }
        val body = object : OutgoingContent.ReadChannelContent() {
            override val contentLength: Long? = null
            override fun readFrom() = ByteReadChannel(ByteArray(1_048_577) { 'x'.code.toByte() })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/body") { setBody(body) }.status)
    }
    @Test fun `untrusted leftmost forwarded address cannot override a trusted proxy chain`() {
        val trusted = setOf("172.30.88.2", "10.0.0.1")
        assertEquals("198.51.100.7", validatedClientIp("172.30.88.2", "203.0.113.9, 198.51.100.7, 10.0.0.1", trusted))
        assertEquals("198.51.100.7", validatedClientIp("198.51.100.7", "203.0.113.9", trusted))
        assertEquals("172.30.88.2", validatedClientIp("172.30.88.2", "garbage", trusted))
    }
}
