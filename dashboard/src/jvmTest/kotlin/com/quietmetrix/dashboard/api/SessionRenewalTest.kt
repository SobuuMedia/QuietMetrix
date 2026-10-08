package com.quietmetrix.dashboard.api

import com.quietmetrix.dashboard.storage.TokenStorage
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRenewalTest {
    private fun HttpServer.reply(path: String, handler: (com.sun.net.httpserver.HttpExchange) -> Pair<Int, String>) {
        createContext(path) { exchange ->
            val (status, body) = handler(exchange)
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    @Test fun `concurrent unauthorized requests rotate once and retry once`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool(); server.executor = executor
        val refreshes = AtomicInteger(); val requests = AtomicInteger()
        server.reply("/api/v1/auth/refresh") { refreshes.incrementAndGet(); 200 to """{"token":"renewed","refresh_token":"rotated"}""" }
        server.reply("/api/v1/projects") { call ->
            requests.incrementAndGet()
            if (call.requestHeaders.getFirst("Authorization") == "Bearer renewed") 200 to """{"projects":[]}"""
            else 401 to """{"error":"unauthorized"}"""
        }
        server.start()
        try {
            val api = ApiClient("http://127.0.0.1:${server.address.port}")
            api.token = "expired"; api.refreshToken = "original"
            coroutineScope { (1..8).map { async { api.listProjects() } }.awaitAll() }
            assertEquals(1, refreshes.get()); assertTrue(requests.get() in 9..16)
            assertEquals("renewed", api.token); assertEquals("rotated", api.refreshToken)
        } finally { server.stop(0); executor.shutdownNow(); TokenStorage.clear(); TokenStorage.clearRefresh() }
    }

    @Test fun `refresh completing after logout cannot restore browser credentials`() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool(); server.executor = executor
        server.reply("/api/v1/auth/refresh") {
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            200 to """{"token":"late-token","refresh_token":"late-refresh"}"""
        }
        server.start()
        try {
            val api = ApiClient("http://127.0.0.1:${server.address.port}")
            api.token = "old"; api.refreshToken = "old-refresh"
            val result = async(Dispatchers.IO) { api.refresh() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            api.token = null; api.refreshToken = null
            TokenStorage.clear(); TokenStorage.clearRefresh(); release.countDown()
            assertFalse(result.await()); assertNull(api.token); assertNull(TokenStorage.get())
        } finally { release.countDown(); server.stop(0); executor.shutdownNow() }
    }
}
