package com.quietmetrix.server.ai

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get

/** The application owns the housekeeping job and cancels it when the server stops. */
fun Application.configureAiMaintenance() {
    val ledger: AiAccounting = get()
    val job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        while(isActive) {
            runCatching { ledger.cleanup() }
            delay(60_000)
        }
    }
    monitor.subscribe(ApplicationStopped) { job.cancel() }
}
