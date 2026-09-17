package com.quietmetrix.mcp

import com.quietmetrix.cli.QuietMetrixClient
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

/** The slice of Node's global `process` and `process.stdin`/`stdout` the server needs. */
private external interface NodeProcess {
    val env: dynamic
    var exitCode: Int
    val stdin: dynamic
    val stdout: dynamic
}

private external val process: NodeProcess

private fun nodeEnv(): Map<String, String> {
    val env = process.env
    val keys = js("Object.keys(env)") as Array<String>
    return keys.associateWith { key -> (env[key] as? String).orEmpty() }
}

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

/**
 * Reads newline-delimited JSON-RPC requests from stdin and writes responses to stdout — the
 * MCP stdio transport. Buffers partial reads across `data` events since Node delivers stdin
 * in arbitrary chunks, not line-by-line.
 */
fun main() {
    val server = McpServer(QuietMetrixClient(KtorHttpAdapter()), nodeEnv())
    val scope = MainScope()
    var buffer = ""

    process.stdin.setEncoding("utf8")
    process.stdin.on("data") { chunk: String ->
        buffer += chunk
        while (true) {
            val newlineIndex = buffer.indexOf('\n')
            if (newlineIndex < 0) break
            val line = buffer.substring(0, newlineIndex).trim()
            buffer = buffer.substring(newlineIndex + 1)
            if (line.isEmpty()) continue
            scope.launch {
                val response = server.handle(line)
                if (response != null) {
                    process.stdout.write(response + "\n")
                }
            }
        }
    }
    process.stdin.on("end") { process.exitCode = 0 }
    // Node exits once stdin closes and no more work is scheduled — nothing further needed here.
    Unit
}
