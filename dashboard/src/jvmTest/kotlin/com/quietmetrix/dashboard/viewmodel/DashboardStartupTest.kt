package com.quietmetrix.dashboard.viewmodel

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.App
import com.quietmetrix.dashboard.storage.TokenStorage
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardStartupTest {
    private class Fixture(private val respond: (HttpExchange) -> Pair<Int, String>) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val executor = Executors.newCachedThreadPool()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@Fixture.executor
            createContext("/") { exchange ->
                val (status, body) = respond(exchange)
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val url = "http://127.0.0.1:${server.address.port}"
        override fun close() {
            scope.cancel()
            server.stop(0)
            executor.shutdownNow()
            TokenStorage.clear()
            TokenStorage.clearRefresh()
            TokenStorage.clearUser()
            TokenStorage.clearLastProjectId()
        }
    }

    @Test fun savedSessionAndHomeDataDoNotWaitForMetadataOrOtherPanels(): Unit = runBlocking {
        val metadataEntered = CountDownLatch(1)
        val panelsEntered = CountDownLatch(3)
        val releaseMetadata = CountDownLatch(1)
        val releasePanels = CountDownLatch(1)
        val paths = ConcurrentLinkedQueue<String>()
        Fixture { exchange ->
            val path = exchange.requestURI.path
            paths.add(path)
            assertEquals(if (path.endsWith("/_meta")) null else "Bearer saved-token", exchange.requestHeaders.getFirst("Authorization"))
            when {
                path.endsWith("/_meta") -> {
                    metadataEntered.countDown()
                    check(releaseMetadata.await(10, TimeUnit.SECONDS))
                    200 to "{}"
                }
                path.endsWith("/projects") -> 200 to """{"projects":[{"id":"first","name":"First"},{"id":"last","name":"Last"}]}"""
                path.endsWith("/aggregates") || path.endsWith("/sessions") || path.endsWith("/retention") -> {
                    panelsEntered.countDown()
                    check(releasePanels.await(10, TimeUnit.SECONDS))
                    200 to "{}"
                }
                path.endsWith("/capabilities") -> 200 to """{"enabled":false,"available":false}"""
                else -> 200 to "{}"
            }
        }.use { fixture ->
            try {
                TokenStorage.save("saved-token")
                TokenStorage.saveUser("""{"id":"owner","email":"owner@example.com","role":"developer"}""")
                TokenStorage.saveLastProjectId("last")
                val vm = DashboardViewModel(fixture.scope)
                vm.init(fixture.url)
                // This is the state App observes in its very first composition.
                assertEquals("saved-token", vm.state.value.token)
                assertEquals("developer", vm.state.value.role)
                assertTrue(metadataEntered.await(5, TimeUnit.SECONDS))
                assertTrue(panelsEntered.await(5, TimeUnit.SECONDS), "All home panels must start while metadata and other panels are blocked")
                assertEquals("last", vm.state.value.currentProjectId)
                assertEquals(setOf(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention), vm.state.value.loadingSections)
                releasePanels.countDown()
                withTimeout(5_000) { vm.state.first { it.aggregates != null && it.sessions != null && it.retention != null && it.loadingSections.isEmpty() } }
                assertEquals(ServerConnection.Checking, vm.state.value.serverConnection)
                assertTrue(paths.none { it.endsWith("/transitions") || it.endsWith("/search") || it.endsWith("/friction") || it.endsWith("/funnels") })
                releaseMetadata.countDown()
                withTimeout(5_000) { vm.state.first { it.serverConnection == ServerConnection.Online } }
            } finally {
                releaseMetadata.countDown()
                releasePanels.countDown()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun firstScreenWithSavedSessionHasNoLoginForm() {
        val release = CountDownLatch(1)
        Fixture {
            check(release.await(15, TimeUnit.SECONDS))
            200 to "{}"
        }.use { fixture ->
            try {
                TokenStorage.save("saved-token")
                runComposeUiTest {
                    setContent { App(fixture.url) }
                    onAllNodesWithText("Sign in").assertCountEquals(0)
                    onAllNodesWithText("Email").assertCountEquals(0)
                    onAllNodesWithText("Overview").assertCountEquals(1)
                }
            } finally { release.countDown() }
        }
    }

    @Test fun missingSessionShowsSignedOutStateImmediately() {
        Fixture { 200 to "{}" }.use { fixture ->
            val vm = DashboardViewModel(fixture.scope)
            vm.init(fixture.url)
            assertNull(vm.state.value.token)
            assertFalse(vm.state.value.loading)
        }
    }

    @Test fun rejectedSessionReturnsToLoginAndClearsCredentials() = runBlocking {
        Fixture { exchange ->
            if (exchange.requestURI.path.endsWith("/_meta")) 200 to "{}"
            else 401 to """{"error":"unauthorized"}"""
        }.use { fixture ->
            TokenStorage.save("expired-token")
            TokenStorage.saveRefresh("revoked-refresh")
            val vm = DashboardViewModel(fixture.scope)
            vm.init(fixture.url)
            withTimeout(5_000) { vm.state.first { it.token == null } }
            assertNull(TokenStorage.get())
            assertNull(TokenStorage.getRefresh())
            assertFalse(vm.state.value.loading)
        }
    }

    @Test fun serverFailureKeepsSavedSessionVisible() = runBlocking {
        Fixture { 503 to """{"error":"unavailable"}""" }.use { fixture ->
            TokenStorage.save("saved-token")
            val vm = DashboardViewModel(fixture.scope)
            vm.init(fixture.url)
            withTimeout(5_000) { vm.state.first { it.error != null } }
            assertEquals("saved-token", vm.state.value.token)
            assertEquals("saved-token", TokenStorage.get())
        }
    }
}
