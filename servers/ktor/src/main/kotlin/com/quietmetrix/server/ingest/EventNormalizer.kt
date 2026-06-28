package com.quietmetrix.server.ingest

import com.quietmetrix.server.domain.Event
import com.quietmetrix.server.domain.TrackEventRequest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class EventNormalizer {

    private val json = Json { ignoreUnknownKeys = true }

    private val uaPatterns = mapOf(
        "mobile" to Regex("(?i)mobile|android|iphone|ipod|windows phone"),
        "tablet" to Regex("(?i)ipad|tablet|kindle|silk|(android(?!.*mobile))"),
        "bot" to Regex("(?i)bot|crawler|spider|slurp|mediapartners"),
    )

    private val browserPatterns = listOf(
        "edge" to Regex("(?i)edg/([\\d.]+)"),
        "chrome" to Regex("(?i)chrome/([\\d.]+)"),
        "firefox" to Regex("(?i)firefox/([\\d.]+)"),
        "safari" to Regex("(?i)version/([\\d.]+).*safari"),
    )

    private val osPatterns = listOf(
        "ios" to Regex("(?i)cpu iphone os ([\\d_]+)"),
        "android" to Regex("(?i)android ([\\d.]+)"),
        "macos" to Regex("(?i)mac os x ([\\d_.]+)"),
        "windows" to Regex("(?i)windows nt ([\\d.]+)"),
        "linux" to Regex("(?i)linux"),
    )

    fun normalize(request: TrackEventRequest, projectId: String, clientIp: String?): Event {
        val now = Instant.fromEpochMilliseconds(java.time.Instant.now().toEpochMilli())

        val eventTs = request.ts?.let { parseTimestamp(it) } ?: now
        val (ts, wasOffline) = validateTimestamp(eventTs, now, request.wasOffline)

        val deviceClass = request.ctx?.ua?.let { classifyDevice(it) }
        val (browser, browserVer) = request.ctx?.ua?.let { parseBrowser(it) } ?: (null to null)
        val (os, osVer) = request.ctx?.ua?.let { parseOS(it) } ?: (null to null)
        val (sw, sh) = parseScreenResolution(request.ctx?.viewport)

        return Event(
            projectId = projectId,
            eventName = request.event,
            screen = request.screen,
            props = request.props?.let { json.encodeToString(JsonObject.serializer(), JsonObject(it)) },
            sid = request.sid,
            ts = ts,
            wasOffline = wasOffline,
            country = request.ctx?.country,
            deviceClass = deviceClass ?: "unknown",
            language = request.ctx?.language?.take(10),
            platform = request.sdk?.platform,
            sdkVersion = request.sdk?.version,
            receivedAt = now,
            anonymousId = request.ctx?.anonymousId,
            browser = browser,
            browserVersion = browserVer,
            os = os,
            osVersion = osVer,
            screenWidth = sw,
            screenHeight = sh,
            referrer = request.ctx?.referrer?.take(500),
            sessionNumber = request.ctx?.sessionNumber,
            isSessionStart = request.ctx?.isSessionStart ?: false,
            isSessionEnd = request.ctx?.isSessionEnd ?: false,
        )
    }

    private fun parseTimestamp(ts: String): Instant? {
        return try {
            Instant.parse(ts)
        } catch (_: Exception) {
            null
        }
    }

    private fun validateTimestamp(clientTs: Instant, serverTs: Instant, clientWasOffline: Boolean): Pair<Instant, Boolean> {
        val diffMs = clientTs.toEpochMilliseconds() - serverTs.toEpochMilliseconds()
        val twentyFourHoursMs = 24L * 60 * 60 * 1_000
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1_000

        // Reject future-dated timestamps — use server time
        if (diffMs > twentyFourHoursMs) {
            return Pair(serverTs, true)
        }
        // Events older than 24h are marked as offline regardless of client claim
        val wasOffline = clientWasOffline || (-diffMs > twentyFourHoursMs)
        // Clamp timestamps more than 7 days in the past
        if (-diffMs > sevenDaysMs) {
            return Pair(serverTs, wasOffline)
        }
        return Pair(clientTs, wasOffline)
    }

    private fun classifyDevice(ua: String): String {
        if (uaPatterns["bot"]?.containsMatchIn(ua) == true) return "bot"
        if (uaPatterns["mobile"]?.containsMatchIn(ua) == true) return "mobile"
        if (uaPatterns["tablet"]?.containsMatchIn(ua) == true) return "tablet"
        return "desktop"
    }

    private fun parseBrowser(ua: String): Pair<String?, String?> {
        for ((name, regex) in browserPatterns) {
            val match = regex.find(ua) ?: continue
            return name to match.groupValues[1].take(50)
        }
        return null to null
    }

    private fun parseOS(ua: String): Pair<String?, String?> {
        for ((name, regex) in osPatterns) {
            val match = regex.find(ua) ?: continue
            val version = match.groupValues[1].replace("_", ".").take(50)
            return name to version
        }
        return null to null
    }

    private fun parseScreenResolution(viewport: String?): Pair<Int?, Int?> {
        if (viewport == null) return null to null
        val parts = viewport.split("x")
        if (parts.size == 2) return parts[0].toIntOrNull() to parts[1].toIntOrNull()
        return null to null
    }
}