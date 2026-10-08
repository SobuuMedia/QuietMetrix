package com.quietmetrix.server.util

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

class RequestTooLarge : RuntimeException("Request body exceeds 1 MB")
class RequestBodyTimedOut : RuntimeException("Request body timed out")

val requestJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Limits actual bytes and total read time without blocking a server thread on a slow client. */
suspend fun ApplicationCall.boundedBody(): ByteArray = withTimeoutOrNull(15_000) {
    val channel = receiveChannel()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = channel.readAvailable(buffer, 0, minOf(buffer.size, 1_048_577 - output.size()))
        if (count < 0) break
        if (count > 0) output.write(buffer, 0, count)
        if (output.size() > 1_048_576) throw RequestTooLarge()
    }
    output.toByteArray()
} ?: throw RequestBodyTimedOut()

suspend inline fun <reified T> ApplicationCall.receiveBounded(): T =
    requestJson.decodeFromString<T>(boundedBody().toString(Charsets.UTF_8))
