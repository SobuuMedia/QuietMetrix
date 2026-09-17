package com.quietmetrix.cli

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves a short range token (`1h`/`1d`/`7d`/`30d`/`90d`) into the query-parameter shapes
 * QuietMetrix's two backends actually expect — a trailing window ending now, in UTC. The two
 * backends disagree on convention (Ktor's `/aggregates` and `/events` want explicit ISO
 * `from`/`to`, its `/transitions`/`/sessions`/`/retention` want `?days=N`, PHP and the
 * funnel-results endpoints on both backends want `?range=<token>`) — rather than have the CLI
 * guess which backend it's talking to, every request sends all three, and each backend reads
 * whichever it understands.
 */
@OptIn(ExperimentalTime::class)
object RangeToken {
    /** The range tokens every analytics endpoint accepts. "7d" is the default — roughly "this week". */
    val VALID = setOf("1h", "1d", "7d", "30d", "90d")

    val DEFAULT = "7d"

    fun isValid(token: String): Boolean = token in VALID

    private fun seconds(token: String): Long = when (token) {
        "1h" -> 3_600L
        "1d" -> 86_400L
        "30d" -> 2_592_000L
        "90d" -> 7_776_000L
        else -> 604_800L // "7d"
    }

    data class Resolved(val range: String, val fromIso: String, val toIso: String, val days: Int)

    /** [now] is injectable so callers can test a fixed instant without a network or a clock mock. */
    fun resolve(token: String, now: Instant = Clock.System.now()): Resolved {
        val windowSeconds = seconds(token)
        val from = now.minus(windowSeconds.seconds)
        val days = (windowSeconds / 86_400L).toInt().coerceAtLeast(1)
        return Resolved(range = token, fromIso = from.toString(), toIso = now.toString(), days = days)
    }

    /**
     * Appends `range`/`from`/`to`/`days` as query parameters to [baseUrl] (which must not
     * already contain a `?`). ISO instants are percent-encoded (only `:` needs it here) since
     * [com.quietmetrix.cli.http.HttpClient] takes a fully-formed URL string, not structured
     * query params.
     */
    fun appendTo(baseUrl: String, resolved: Resolved): String {
        val from = resolved.fromIso.replace(":", "%3A")
        val to = resolved.toIso.replace(":", "%3A")
        return "$baseUrl?range=${resolved.range}&from=$from&to=$to&days=${resolved.days}"
    }
}
