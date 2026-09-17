package com.quietmetrix.cli

import com.quietmetrix.cli.http.HttpClient
import com.quietmetrix.cli.http.HttpResponse
import io.ktor.client.HttpClient as KtorHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** The slice of Node's global `process` object the CLI needs. */
private external interface NodeProcess {
    val argv: Array<String>
    val env: dynamic
    var exitCode: Int
}

private external val process: NodeProcess

/** Node's `process.env` is a plain JS object; read it into a real Map for testable code. */
private fun nodeEnv(): Map<String, String> {
    val env = process.env
    val keys = js("Object.keys(env)") as Array<String>
    return keys.associateWith { key -> (env[key] as? String).orEmpty() }
}

/** Adapts Ktor's JS-engine client to the CLI's minimal [HttpClient] contract. */
private class KtorHttpAdapter : HttpClient {
    private val client = KtorHttpClient()

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        val response = client.request(url) {
            method = HttpMethod.Get
            headers.forEach { (k, v) -> header(k, v) }
        }
        return HttpResponse(response.status.value, response.bodyAsText())
    }

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val response = client.request(url) {
            method = HttpMethod.Post
            headers.forEach { (k, v) -> header(k, v) }
            setBody(body)
        }
        return HttpResponse(response.status.value, response.bodyAsText())
    }
}

fun main() {
    // process.argv is [node, script.js, ...userArgs].
    val args = process.argv.drop(2)
    val cli = QuietMetrixCli(KtorHttpAdapter())

    MainScope().launch {
        val exitCode = cli.run(
            args = args,
            env = nodeEnv(),
            out = { line -> println(line) },
            err = { line -> console.error(line) },
        )
        process.exitCode = exitCode
    }
}
