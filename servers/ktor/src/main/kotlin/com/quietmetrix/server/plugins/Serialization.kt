package com.quietmetrix.server.plugins

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentlengthlimit.ContentLengthLimit
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(DoubleReceive)
    install(ContentLengthLimit) {
        limit = 10L * 1024L * 1024L
    }
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            isLenient = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
}