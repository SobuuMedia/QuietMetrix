package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.CreateFunnelRequest
import com.quietmetrix.dashboard.api.ErrorKind
import com.quietmetrix.dashboard.api.FunnelStepDto
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises actual client/VM HTTP wiring, including failed writes and a changed reporting scope. */
class ImplementationGapHttpTest {
    @Test fun filteredReportsAndFailedFunnelSavesKeepTheirScopeAndDraftLifecycle() = runBlocking {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val writes=AtomicInteger()
        server.createContext("/") { exchange ->
            val path=exchange.requestURI.path
            val query=exchange.requestURI.rawQuery.orEmpty()
            val (status,body)=when {
                path.endsWith("/meta") -> 200 to """{"debug":false,"selfhost":true,"version":"test"}"""
                path.endsWith("/report") -> {
                    val count=if (query.contains("filter_value=DE")) 6 else 20
                    200 to """{"overview":{"people":{"value":$count,"state":"reported","unit":"installations","source":"v2"}},"retention_cohorts":[]}"""
                }
                path.endsWith("/funnels") && exchange.requestMethod=="POST" -> {
                    if (writes.incrementAndGet()==1) 400 to """{"error":"schema_violation","message":"Invalid definition"}"""
                    else 201 to """{"funnel_key":"signup","name":"Signup","revision":1}"""
                }
                path.endsWith("/funnels") -> 200 to """{"funnels":[]}"""
                path.endsWith("/experiments") -> 200 to """{"experiments":[]}"""
                path.endsWith("/capabilities") -> 200 to """{"enabled":false,"available":false}"""
                else -> 200 to "{}"
            }
            exchange.requestBody.close()
            val bytes=body.toByteArray()
            exchange.responseHeaders.add("Content-Type","application/json")
            exchange.sendResponseHeaders(status,bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val vm=DashboardViewModel(scope)
            vm.init("http://127.0.0.1:${server.address.port}")
            vm.selectProject("proj_gap_test")
            withTimeout(10_000) { vm.state.first { it.aggregates?.people==20L } }
            vm.setOverviewFilter("country","DE")
            withTimeout(10_000) { vm.state.first { it.aggregates?.people==6L } }
            assertTrue(vm.state.value.aggregates?.topEvents.orEmpty().isEmpty())
            assertEquals(OverviewFilter("country","DE"),vm.state.value.overviewFilter)
            var saved=false
            val request=CreateFunnelRequest(funnelKey="signup",name="Signup",steps=listOf(FunnelStepDto("one","start"),FunnelStepDto("two","finish")))
            vm.saveFunnel(request,onSaved={ saved=true })
            withTimeout(10_000) { vm.state.first { !it.funnelSaving && it.funnelSaveError!=null } }
            assertFalse(saved)
            assertEquals(ErrorKind.Invalid,vm.state.value.funnelSaveError?.kind)
            vm.saveFunnel(request,onSaved={ saved=true })
            withTimeout(10_000) { vm.state.first { !it.funnelSaving } }
            assertTrue(saved)
            assertEquals(2,writes.get())
            vm.hideWorthALook("saved-local-choice",false)
            val reloaded=DashboardViewModel(scope)
            reloaded.init("http://127.0.0.1:${server.address.port}")
            reloaded.selectProject("proj_gap_test")
            assertTrue("saved-local-choice" in reloaded.state.value.dismissedWorthALookIds)
            reloaded.selectProject("proj_other")
            assertFalse("saved-local-choice" in reloaded.state.value.dismissedWorthALookIds)
        } finally { scope.cancel(); server.stop(0) }
    }
}
