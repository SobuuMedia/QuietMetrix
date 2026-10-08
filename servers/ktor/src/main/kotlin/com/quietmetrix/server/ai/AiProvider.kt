package com.quietmetrix.server.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

/** Provider configuration stays server-side; rates are micro-USD per million tokens. */
data class AiConfig(
    val provider: String = "openai", val baseUrl: String = "", val model: String = "",
    val apiKey: String = "", val local: Boolean = false, val rates: AiRates? = null,
    val operatorKey: String = "",
) {
    /** Explicit credentials, model, and hosted pricing are required. */
    fun enabled(cloud: Boolean): Boolean = provider in setOf("openai", "gemini", "anthropic") &&
        model.matches(Regex("[A-Za-z0-9._:/-]{1,200}")) && (apiKey.isNotBlank() || local) &&
        (!local || baseUrl.isNotBlank()) && (!cloud || rates != null) &&
        runCatching { URI(url).let { it.host != null && (it.scheme == "https" || local && it.scheme == "http") && it.userInfo == null && it.query == null && it.fragment == null } }.getOrDefault(false)
    val url: String get() = baseUrl.ifBlank { when(provider) { "gemini" -> "https://generativelanguage.googleapis.com/v1beta"; "anthropic" -> "https://api.anthropic.com/v1"; else -> "https://api.openai.com/v1" } }.trimEnd('/')
    companion object {
        /** Reads environment values without publishing configuration. */
        fun fromEnvironment(): AiConfig {
            fun rate(key: String) = System.getenv(key)?.toLongOrNull()?.takeIf { it in 0..1_000_000_000 }
            val input = rate("QM_AI_INPUT_MICRO_USD_PER_MILLION")
            val output = rate("QM_AI_OUTPUT_MICRO_USD_PER_MILLION")
            val cache = rate("QM_AI_CACHE_MICRO_USD_PER_MILLION")
            return AiConfig(System.getenv("QM_AI_PROVIDER") ?: "openai", System.getenv("QM_AI_BASE_URL") ?: "",
                System.getenv("QM_AI_MODEL") ?: "", System.getenv("QM_AI_API_KEY") ?: "", System.getenv("QM_AI_LOCAL")?.equals("true", ignoreCase = true) == true,
                if (input != null && output != null && cache != null) AiRates(input, output, cache, rate("QM_AI_CACHE_WRITE_MICRO_USD_PER_MILLION") ?: input * 2) else null,
                System.getenv("QM_OPERATOR_API_KEY") ?: "")
        }
    }
}

/** Reported usage includes output reasoning tokens and cached input. */
data class AiUsage(val input: Long, val output: Long, val cache: Long = 0, val cacheWrite: Long = 0)

/** Integer arithmetic rounds costs up; bounds avoid overflow. */
data class AiRates(val input: Long, val output: Long, val cache: Long, val cacheWrite: Long = input * 2) {
    /** Computes billable cost with cached input removed from ordinary input. */
    fun cost(usage: AiUsage): Long {
        require(usage.input >= 0 && usage.output >= 0 && usage.cache in 0..usage.input && usage.cacheWrite in 0..(usage.input - usage.cache))
        return ((BigInteger.valueOf(usage.input - usage.cache - usage.cacheWrite) * BigInteger.valueOf(input) +
            BigInteger.valueOf(usage.output) * BigInteger.valueOf(output) +
            BigInteger.valueOf(usage.cache) * BigInteger.valueOf(cache) + BigInteger.valueOf(usage.cacheWrite) * BigInteger.valueOf(cacheWrite) + BigInteger.valueOf(999999)) / BigInteger.valueOf(1000000)).longValueExact()
    }
    val reservation: Long get() = 4L * AiRates(maxOf(input, cache, cacheWrite), output, maxOf(input, cache, cacheWrite)).cost(AiUsage(65536, 1024))
}

/** Calendar reset always uses UTC dates supplied by the service. */
fun aiResetDate(today: LocalDate): String = today.withDayOfMonth(1).plusMonths(1).toString()

/** Normalized provider reply retains the original protocol-specific continuation. */
data class AiReply(val text: String, val calls: List<AiToolCall>, val continuation: JsonElement, val usage: AiUsage?)

/** Tool arguments never contain an account or project selector. */
data class AiToolCall(val id: String, val name: String, val args: JsonObject, val nativeId: Boolean = true)

/** Sanitized failure, optionally carrying incurred usage. */
class AiProviderFailure(val unsupportedTools: Boolean = false) : RuntimeException("AI provider failed")

/** Injectable transport enables fixtures without contacting a provider. */
fun interface AiTransport { fun send(url: String, headers: Map<String, String>, body: JsonObject, timeoutSeconds: Long): Pair<Int, String> }

/** HTTP response sizes and total request durations are bounded. */
class AiHttpTransport : AiTransport {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
    override fun send(url: String, headers: Map<String, String>, body: JsonObject, timeoutSeconds: Long): Pair<Int, String> {
        val builder = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(timeoutSeconds.coerceIn(1, 60)))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        headers.forEach { (key, value) -> builder.header(key, value) }
        val future = client.sendAsync(builder.build(), HttpResponse.BodyHandler { _ ->
            object : HttpResponse.BodySubscriber<ByteArray> {
                private val completion = java.util.concurrent.CompletableFuture<ByteArray>()
                private val output = java.io.ByteArrayOutputStream()
                private var subscription: java.util.concurrent.Flow.Subscription? = null
                override fun getBody(): java.util.concurrent.CompletionStage<ByteArray> = completion
                override fun onSubscribe(value: java.util.concurrent.Flow.Subscription) { subscription=value;value.request(Long.MAX_VALUE) }
                override fun onNext(items: List<java.nio.ByteBuffer>) {
                    for(item in items) {
                        if(output.size()+item.remaining()>262144) { subscription?.cancel();completion.completeExceptionally(AiProviderFailure());return }
                        val bytes=ByteArray(item.remaining());item.get(bytes);output.write(bytes)
                    }
                }
                override fun onError(error: Throwable) { completion.completeExceptionally(error) }
                override fun onComplete() { completion.complete(output.toByteArray()) }
            }
        })
        try {
            val response=future.get(timeoutSeconds.coerceIn(1,60),java.util.concurrent.TimeUnit.SECONDS)
            return response.statusCode() to response.body().toString(Charsets.UTF_8)
        } catch(error: Exception) { future.cancel(true);throw AiProviderFailure() }
    }
}

/** Shared adapter contract for bounded read-only tool conversations. */
interface AiProvider {
    fun initial(system: String, history: List<JsonObject>, question: String): MutableList<JsonObject>
    fun complete(messages: List<JsonObject>, tools: Boolean, seconds: Long): AiReply
    fun continueWith(messages: MutableList<JsonObject>, reply: AiReply, results: List<JsonObject>)
}

/** Native OpenAI, Gemini and Anthropic formats preserve tool IDs and signed content blocks. */
class ProtocolAiProvider(private val config: AiConfig, private val transport: AiTransport = AiHttpTransport()) : AiProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val native = config.provider
    private val toolNames = listOf("aggregates", "sessions", "retention", "transitions", "search", "friction", "funnels", "experiments")
    private val schema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}); put("additionalProperties", false) }
    private var system = ""
    override fun initial(system: String, history: List<JsonObject>, question: String): MutableList<JsonObject> {
        this.system = system
        val messages = mutableListOf<JsonObject>()
        if (native == "openai") messages += buildJsonObject { put("role", "system"); put("content", system) }
        (history + buildJsonObject { put("role", "user"); put("content", question) }).forEach { item ->
            val role = item["role"]!!.jsonPrimitive.content
            val content = item["content"]!!.jsonPrimitive.content
            messages += when(native) {
                "gemini" -> buildJsonObject { put("role", if(role == "assistant") "model" else "user"); put("parts", buildJsonArray { add(buildJsonObject { put("text", content) }) }) }
                else -> item
            }
        }
        return messages
    }
    override fun complete(messages: List<JsonObject>, tools: Boolean, seconds: Long): AiReply {
        val body = buildJsonObject {
            if (native != "gemini") put("model", config.model)
            when(native) {
                "gemini" -> {
                    put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
                    put("contents", JsonArray(messages)); put("generationConfig", buildJsonObject { put("maxOutputTokens", 1024) })
                    if(tools) put("tools", buildJsonArray { add(buildJsonObject { put("functionDeclarations", buildJsonArray { toolNames.forEach { name -> add(buildJsonObject { put("name", name); put("description", "Read reportable $name for the authorized UTC window"); put("parameters", if(native == "gemini") JsonObject(schema.filterKeys { it != "additionalProperties" }) else schema) }) } }) }) })
                }
                "anthropic" -> {
                    put("system", system); put("messages", JsonArray(messages)); put("max_tokens", 1024)
                    if(tools) put("tools", buildJsonArray { toolNames.forEach { name -> add(buildJsonObject { put("name", name); put("description", "Read reportable $name for the authorized UTC window"); put("input_schema", schema) }) } })
                }
                else -> {
                    put("messages", JsonArray(messages)); put(if(config.model.startsWith("gpt-5") || config.model.startsWith("gpt-6") || Regex("^o[134]").containsMatchIn(config.model)) "max_completion_tokens" else "max_tokens", 1024)
                    if(tools) put("tools", buildJsonArray { toolNames.forEach { name -> add(buildJsonObject { put("type", "function"); put("function", buildJsonObject { put("name", name); put("description", "Read reportable $name for the authorized UTC window"); put("parameters", if(native == "gemini") JsonObject(schema.filterKeys { it != "additionalProperties" }) else schema) }) }) } })
                }
            }
        }
        if (body.toString().toByteArray().size > 65536) throw AiProviderFailure()
        val headers = when(native) {
            "gemini" -> mapOf("x-goog-api-key" to config.apiKey)
            "anthropic" -> mapOf("x-api-key" to config.apiKey, "anthropic-version" to "2023-06-01")
            else -> if(config.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${config.apiKey}")
        }
        val endpoint = config.url + when(native) { "gemini" -> "/models/${config.model}:generateContent"; "anthropic" -> "/messages"; else -> "/chat/completions" }
        val (status, raw) = transport.send(endpoint, headers, body, seconds)
        if(status !in 200..299) throw AiProviderFailure(tools && status in setOf(400, 422) && Regex("tools?|function", RegexOption.IGNORE_CASE).containsMatchIn(raw))
        return decode(json.parseToJsonElement(raw).jsonObject)
    }
    /** Parses protocol fixture responses into a shared result. */
    fun decode(root: JsonObject): AiReply {
        fun JsonObject.n(key: String) = get(key)?.jsonPrimitive?.longOrNull
        fun args(value: JsonElement?) = when(value) { is JsonObject -> value; is JsonPrimitive -> runCatching { json.parseToJsonElement(value.content).jsonObject }.getOrDefault(buildJsonObject { put("invalid", true) }); else -> buildJsonObject { put("invalid", true) } }
        return when(native) {
            "gemini" -> {
                val content = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject ?: throw AiProviderFailure()
                val parts = content["parts"]!!.jsonArray
                val u = root["usageMetadata"]?.jsonObject
                AiReply(parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }.joinToString("\n"), parts.mapNotNull { it.jsonObject["functionCall"]?.jsonObject }.mapIndexed { i, c -> AiToolCall(c["id"]?.jsonPrimitive?.content ?: "$i", c["name"]!!.jsonPrimitive.content, args(c["args"]), c.containsKey("id")) }, content,
                    u?.n("promptTokenCount")?.let { input -> u.n("candidatesTokenCount")?.let { AiUsage(input, it + (u.n("thoughtsTokenCount") ?: 0), u.n("cachedContentTokenCount") ?: 0) } })
            }
            "anthropic" -> {
                val content = root["content"]?.jsonArray ?: throw AiProviderFailure()
                val u = root["usage"]?.jsonObject
                AiReply(content.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }.joinToString("\n") { it.jsonObject["text"]!!.jsonPrimitive.content }, content.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "tool_use" }.map { val c = it.jsonObject; AiToolCall(c["id"]!!.jsonPrimitive.content, c["name"]!!.jsonPrimitive.content, args(c["input"])) }, content,
                    u?.n("input_tokens")?.let { input -> u.n("output_tokens")?.let { AiUsage(input + (u.n("cache_read_input_tokens") ?: 0) + (u.n("cache_creation_input_tokens") ?: 0), it, u.n("cache_read_input_tokens") ?: 0, u.n("cache_creation_input_tokens") ?: 0) } })
            }
            else -> {
                val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject ?: throw AiProviderFailure()
                val u = root["usage"]?.jsonObject
                AiReply(content["content"]?.let { if(it == JsonNull) "" else it.jsonPrimitive.content } ?: "", content["tool_calls"]?.jsonArray?.map { val c = it.jsonObject; val f = c["function"]!!.jsonObject; AiToolCall(c["id"]!!.jsonPrimitive.content, f["name"]!!.jsonPrimitive.content, args(f["arguments"])) } ?: emptyList(), content,
                    u?.n("prompt_tokens")?.let { input -> u.n("completion_tokens")?.let { AiUsage(input, it, u["prompt_tokens_details"]?.jsonObject?.n("cached_tokens") ?: u.n("prompt_cache_hit_tokens") ?: 0) } })
            }
        }
    }
    override fun continueWith(messages: MutableList<JsonObject>, reply: AiReply, results: List<JsonObject>) {
        when(native) {
            "gemini" -> {
                messages += reply.continuation.jsonObject
                messages += buildJsonObject { put("role", "user"); put("parts", buildJsonArray { reply.calls.zip(results).forEach { (call, result) -> add(buildJsonObject { put("functionResponse", buildJsonObject { put("name", call.name); if(call.nativeId) put("id", call.id); put("response", result) }) }) } }) }
            }
            "anthropic" -> {
                messages += buildJsonObject { put("role", "assistant"); put("content", reply.continuation) }
                messages += buildJsonObject { put("role", "user"); put("content", buildJsonArray { reply.calls.zip(results).forEach { (call, result) -> add(buildJsonObject { put("type", "tool_result"); put("tool_use_id", call.id); put("content", result.toString()) }) } }) }
            }
            else -> {
                messages += reply.continuation.jsonObject
                reply.calls.zip(results).forEach { (call, result) -> messages += buildJsonObject { put("role", "tool"); put("tool_call_id", call.id); put("content", result.toString()) } }
            }
        }
    }
}
