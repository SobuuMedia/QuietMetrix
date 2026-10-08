package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.ApiClient
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.ServerConnection
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises the actual HTTP wrapper against a loopback fixture instead of mirroring request code. */
class ManagementApiTest {
    /** Selected token expiry and scopes reach the existing backend contract unchanged. */
    @Test fun tokenExpiryIsSentToBackend() = runBlocking {
        val request = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/tokens") { exchange ->
            request.set(exchange.requestBody.bufferedReader().readText())
            val bytes = """{"id":"token-id","name":"Agent","token":"qm_pat_fixture","last4":"ture"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            ApiClient("http://127.0.0.1:${server.address.port}").createToken("Agent", listOf("analytics:read"), 90)
            val body = Json.parseToJsonElement(request.get()).jsonObject
            assertEquals(90, body["expires_in_days"]!!.jsonPrimitive.int)
            assertEquals(listOf("analytics:read"), body["scopes"]!!.jsonArray.map { it.jsonPrimitive.content })
        } finally { server.stop(0) }
    }

    /** The status follows successful and failed real API responses while retaining the server URL. */
    @Test fun serverConnectionReflectsMetadataResponses() = runBlocking {
        val response = AtomicReference("""{"version":"fixture-version","debug":true}""")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/_meta") { exchange ->
            val value = response.get()
            val bytes = value.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (value == "{}") 503 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        server.start()
        try {
            val vm = DashboardViewModel(scope)
            val url = "http://127.0.0.1:${server.address.port}"
            vm.init("$url/")
            assertEquals(url, vm.state.value.serverBaseUrl)
            assertEquals("fixture-version", vm.state.value.serverVersion)
            assertEquals(ServerConnection.Online, vm.state.value.serverConnection)
            response.set("{}")
            vm.checkServerConnection()
            withTimeout(5_000) { while (vm.state.value.serverConnection != ServerConnection.Offline) delay(10) }
            assertEquals("fixture-version", vm.state.value.serverVersion)
        } finally { scope.cancel(); server.stop(0) }
    }
}
