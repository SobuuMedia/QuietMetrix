package com.quietmetrix.server.persistence

import com.quietmetrix.server.domain.Event
import com.quietmetrix.server.funnels.FunnelActorRow
import com.quietmetrix.server.funnels.actorKey
import com.quietmetrix.server.persistence.tables.Events
import com.quietmetrix.server.persistence.tables.EventsInbox
import com.quietmetrix.server.persistence.tables.Sessions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.function
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private fun kotlinx.datetime.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochMilli(this.toEpochMilliseconds())

private fun java.time.Instant.toKotlinInstant(): kotlinx.datetime.Instant =
    kotlinx.datetime.Instant.fromEpochMilliseconds(this.toEpochMilli())

class EventRepository(private val database: Database) {

    fun insert(event: Event): Long {
        return transaction(database) {
            Events.insert {
                it[projectId] = event.projectId.toLong()
                it[eventName] = event.eventName
                it[screen] = event.screen
                it[props] = event.props
                it[sessionId] = event.sid
                it[ts] = LocalDateTime.ofInstant(event.ts.toJavaInstant(), ZoneOffset.UTC)
                it[wasOffline] = event.wasOffline
                it[country] = event.country
                it[deviceClass] = event.deviceClass
                it[language] = event.language
                it[platform] = event.platform
                it[sdkVersion] = event.sdkVersion
                it[receivedAt] = LocalDateTime.ofInstant(
                    event.receivedAt?.toJavaInstant() ?: java.time.Instant.now(),
                    ZoneOffset.UTC
                )
                it[installHash] = event.installHash
                it[city] = event.city
                it[region] = event.region
                it[browser] = event.browser
                it[browserVersion] = event.browserVersion
                it[eventOs] = event.os
                it[osVersion] = event.osVersion
                it[screenWidth] = event.screenWidth
                it[screenHeight] = event.screenHeight
                it[durationMs] = event.durationMs
                it[referrer] = event.referrer
                it[sessionNumber] = event.sessionNumber
                it[isSessionStart] = event.isSessionStart
                it[isSessionEnd] = event.isSessionEnd
            } get Events.id
        }
    }

    fun insertToInbox(projectId: Long, payload: String): Long {
        return transaction(database) {
            EventsInbox.insert {
                it[EventsInbox.projectId] = projectId
                it[EventsInbox.payload] = payload
            } get EventsInbox.id
        }
    }

    fun findByProjectId(
        projectId: Long,
        limit: Int = 100,
        offset: Int = 0,
        eventName: String? = null,
        screen: String? = null,
        from: java.time.Instant? = null,
        to: java.time.Instant? = null,
    ): List<Event> {
        return transaction(database) {
            Events
                .selectAll()
                .where {
                    var op: Op<Boolean> = Events.projectId eq projectId
                    eventName?.let { op = op and (Events.eventName eq it) }
                    screen?.let { op = op and (Events.screen eq it) }
                    from?.let { op = op and (Events.ts greaterEq LocalDateTime.ofInstant(it, ZoneOffset.UTC)) }
                    to?.let { op = op and (Events.ts lessEq LocalDateTime.ofInstant(it, ZoneOffset.UTC)) }
                    op
                }
                .orderBy(Events.ts, SortOrder.DESC)
                .limit(limit).offset(offset.toLong())
                .map { rowToEvent(it) }
        }
    }

    /**
     * Rows relevant to matching a funnel: events named after one of [eventNames] (i.e. any
     * step's event), within `[from, to)` — the caller widens `to` by the funnel's window so
     * a completion just after the requested range is still counted for an actor who entered
     * inside it. Ordered by ts so the [limit] cap is deterministic; capped size means the
     * caller must treat an exactly-full result as [truncated].
     */
    fun findFunnelEvents(projectId: Long, eventNames: Set<String>, from: Instant, to: Instant, limit: Int): List<FunnelActorRow> {
        if (eventNames.isEmpty()) return emptyList()
        return transaction(database) {
            Events
                .selectAll()
                .where {
                    (Events.projectId eq projectId) and
                    (Events.eventName inList eventNames) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts less LocalDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                .orderBy(Events.ts, SortOrder.ASC)
                .limit(limit)
                .mapNotNull { row ->
                    val actor = actorKey(row[Events.installHash], row[Events.sessionId]) ?: return@mapNotNull null
                    FunnelActorRow(
                        actorKey = actor,
                        installHash = row[Events.installHash],
                        sessionId = row[Events.sessionId],
                        eventName = row[Events.eventName],
                        ts = row[Events.ts].atZone(ZoneOffset.UTC).toInstant().toKotlinInstant(),
                        screen = row[Events.screen],
                        props = decodeFunnelProps(row[Events.props]),
                        country = row[Events.country],
                        platform = row[Events.platform],
                        deviceClass = row[Events.deviceClass],
                        language = row[Events.language],
                    )
                }
        }
    }

    private fun decodeFunnelProps(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            Json.decodeFromString(JsonObject.serializer(), raw).mapValues { (_, v) ->
                (v as? JsonPrimitive)?.content ?: v.toString()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun findTopEvents(projectId: Long, from: java.time.Instant, to: java.time.Instant, limit: Int = 10): List<Map<String, Any?>> {
        return transaction(database) {
            Events
                .select(Events.eventName, Events.id.count())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                .groupBy(Events.eventName)
                .orderBy(Events.id.count(), SortOrder.DESC)
                .limit(limit)
                .map { mapOf("event_name" to it[Events.eventName], "count" to it[Events.id.count()].toInt()) }
        }
    }

    fun findTopScreens(projectId: Long, from: java.time.Instant, to: java.time.Instant, limit: Int = 10): List<Map<String, Any?>> {
        return transaction(database) {
            Events
                .select(Events.screen, Events.id.count())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    (Events.screen.isNotNull())
                }
                .groupBy(Events.screen)
                .orderBy(Events.id.count(), SortOrder.DESC)
                .limit(limit)
                .map { mapOf("screen" to it[Events.screen]!!, "count" to it[Events.id.count()].toInt()) }
        }
    }

    /** Per-screen dwell time, aggregated from `screen_view` events that carry a `duration_ms`. */
    fun findScreenDurations(projectId: Long, from: java.time.Instant, to: java.time.Instant, limit: Int = 10): List<Map<String, Any?>> {
        val avgCol = Events.durationMs.avg()
        val sumCol = Events.durationMs.sum()
        val countCol = Events.id.count()
        return transaction(database) {
            Events
                .select(Events.screen, avgCol, sumCol, countCol)
                .where {
                    (Events.projectId eq projectId) and
                    (Events.eventName eq "screen_view") and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    (Events.screen.isNotNull()) and
                    (Events.durationMs.isNotNull())
                }
                .groupBy(Events.screen)
                .orderBy(sumCol, SortOrder.DESC)
                .limit(limit)
                .map {
                    mapOf(
                        "screen" to it[Events.screen]!!,
                        "count" to it[countCol].toInt(),
                        "avg_ms" to (it[avgCol]?.toLong() ?: 0L),
                        "total_ms" to (it[sumCol] ?: 0L),
                    )
                }
        }
    }

    fun findDailyTotals(projectId: Long, from: java.time.Instant, to: java.time.Instant): List<Map<String, Any?>> {
        return transaction(database) {
            Events
                .select(
                    Events.ts.function("DATE").alias("day"),
                    Events.id.count()
                )
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                .groupBy(Events.ts.function("DATE"))
                .orderBy(Events.ts.function("DATE") to SortOrder.ASC)
                .map { mapOf("day" to it[Events.ts.function("DATE")]!!.toString(), "total" to it[Events.id.count()].toInt()) }
        }
    }

    fun countByProjectId(projectId: Long): Long {
        return transaction(database) {
            Events
                .selectAll()
                .where { Events.projectId eq projectId }
                .count()
        }
    }

    private fun rowToEvent(row: ResultRow) = Event(
        id = row[Events.id].toString(),
        projectId = row[Events.projectId].toString(),
        eventName = row[Events.eventName],
        screen = row[Events.screen],
        props = row[Events.props],
        sid = row[Events.sessionId],
        ts = row[Events.ts]
            .atZone(ZoneOffset.UTC).toInstant().toKotlinInstant(),
        wasOffline = row[Events.wasOffline],
        country = row[Events.country],
        deviceClass = row[Events.deviceClass],
        language = row[Events.language],
        platform = row[Events.platform],
        sdkVersion = row[Events.sdkVersion],
        receivedAt = row[Events.receivedAt]
            .atZone(ZoneOffset.UTC).toInstant().toKotlinInstant(),
        installHash = row[Events.installHash],
        city = row[Events.city],
        region = row[Events.region],
        browser = row[Events.browser],
        browserVersion = row[Events.browserVersion],
        os = row[Events.eventOs],
        osVersion = row[Events.osVersion],
        screenWidth = row[Events.screenWidth],
        screenHeight = row[Events.screenHeight],
        durationMs = row[Events.durationMs],
        referrer = row[Events.referrer],
        sessionNumber = row[Events.sessionNumber],
        isSessionStart = row[Events.isSessionStart],
        isSessionEnd = row[Events.isSessionEnd],
    )

    fun countByProjectIdAndRange(projectId: Long, from: Instant, to: Instant): Long {
        return transaction(database) {
            Events.selectAll()
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                .count()
        }
    }

    /** Count events the app reported as errors (CrashWatch owns the details). */
    fun countErrorsByProjectIdAndRange(projectId: Long, from: Instant, to: Instant): Long {
        return transaction(database) {
            Events.selectAll()
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    (Events.eventName.lowerCase() inList listOf("error", "crash", "exception"))
                }
                .count()
        }
    }

    fun findCountriesBreakdown(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            Events.select(Events.country, Events.id.count())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    Events.country.isNotNull()
                }
                .groupBy(Events.country)
                .orderBy(Events.id.count(), SortOrder.DESC)
                .map { mapOf("name" to it[Events.country]!!, "count" to it[Events.id.count()].toInt()) }
        }
    }

    fun findBrowsersBreakdown(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            Events.select(Events.browser, Events.id.count())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    Events.browser.isNotNull()
                }
                .groupBy(Events.browser)
                .orderBy(Events.id.count(), SortOrder.DESC)
                .map { mapOf("name" to it[Events.browser]!!, "count" to it[Events.id.count()].toInt()) }
        }
    }

    fun findOSBreakdown(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            Events.select(Events.eventOs, Events.id.count())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    Events.eventOs.isNotNull()
                }
                .groupBy(Events.eventOs)
                .orderBy(Events.id.count(), SortOrder.DESC)
                .map { mapOf("name" to it[Events.eventOs]!!, "count" to it[Events.id.count()].toInt()) }
        }
    }

    fun findPlatformsBreakdown(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            Events.select(Events.platform, Events.id.count())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    Events.platform.isNotNull()
                }
                .groupBy(Events.platform)
                .orderBy(Events.id.count(), SortOrder.DESC)
                .map { mapOf("name" to it[Events.platform]!!, "count" to it[Events.id.count()].toInt()) }
        }
    }

    fun findDeviceClassBreakdown(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            Events.select(Events.deviceClass, Events.id.count())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    Events.deviceClass.isNotNull()
                }
                .groupBy(Events.deviceClass)
                .orderBy(Events.id.count(), SortOrder.DESC)
                .map { mapOf("name" to it[Events.deviceClass]!!, "count" to it[Events.id.count()].toInt()) }
        }
    }

    fun findLanguagesBreakdown(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            Events.select(Events.language, Events.id.count())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    Events.language.isNotNull()
                }
                .groupBy(Events.language)
                .orderBy(Events.id.count(), SortOrder.DESC)
                .limit(20)
                .map { mapOf("name" to it[Events.language]!!, "count" to it[Events.id.count()].toInt()) }
        }
    }

    fun findSessionStats(projectId: Long, from: Instant, to: Instant): Map<String, Any?> {
        return transaction(database) {
            val result = Sessions
                .select(Sessions.id.count(), Sessions.durationSec.avg(), Sessions.eventCount.avg())
                .where {
                    (Sessions.projectId eq projectId) and
                    (Sessions.startedAt greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Sessions.startedAt lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    Sessions.endedAt.isNotNull()
                }
                .single()
            mapOf(
                "total_sessions" to result[Sessions.id.count()].toInt(),
                "avg_duration_sec" to result[Sessions.durationSec.avg()]?.toInt(),
                "avg_events_per_session" to result[Sessions.eventCount.avg()]?.toInt(),
            )
        }
    }

    fun findOfflineStats(projectId: Long, from: Instant, to: Instant): Map<String, Any?> {
        return transaction(database) {
            val total = Events.selectAll()
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC))
                }
                .count()
            val offline = Events.selectAll()
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq LocalDateTime.ofInstant(from, ZoneOffset.UTC)) and
                    (Events.ts lessEq LocalDateTime.ofInstant(to, ZoneOffset.UTC)) and
                    (Events.wasOffline eq true)
                }
                .count()
            mapOf(
                "total" to total.toInt(),
                "offline_count" to offline.toInt(),
                "offline_pct" to if (total > 0) (offline.toDouble() / total * 100).toInt() else 0,
            )
        }
    }

    fun startSession(sessionId: String, projectId: Long,
                      geoInfo: Map<String, String?>, deviceInfo: Map<String, String?>): Long {
        return transaction(database) {
            Sessions.insert {
                it[this.sessionId] = sessionId
                it[this.projectId] = projectId
                it[startedAt] = LocalDateTime.now(ZoneOffset.UTC)
                it[country] = geoInfo["country"]
                it[city] = geoInfo["city"]
                it[region] = geoInfo["region"]
                it[deviceClass] = deviceInfo["device_class"]
                it[browser] = deviceInfo["browser"]
                it[browserVersion] = deviceInfo["browser_version"]
                it[sessionOs] = deviceInfo["os"]
                it[sessionOsVersion] = deviceInfo["os_version"]
                it[platform] = deviceInfo["platform"]
                it[screenWidth] = deviceInfo["screen_width"]?.toIntOrNull()
                it[screenHeight] = deviceInfo["screen_height"]?.toIntOrNull()
                it[language] = deviceInfo["language"]
                it[referrer] = deviceInfo["referrer"]
            } get Sessions.id
        }
    }

    fun endSession(sessionId: String, projectId: Long, durationSec: Int, eventCount: Int) {
        transaction(database) {
            Sessions.update({ Sessions.sessionId eq sessionId and (Sessions.projectId eq projectId) }) {
                it[endedAt] = LocalDateTime.now(ZoneOffset.UTC)
                it[this.durationSec] = durationSec
                it[this.eventCount] = eventCount
            }
        }
    }

    fun findTransitions(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            val fromLdt = LocalDateTime.ofInstant(from, ZoneOffset.UTC)
            val toLdt = LocalDateTime.ofInstant(to, ZoneOffset.UTC)

            // Key each hop by a structured (from, to) Pair rather than a delimited "from→to" string:
            // the string round-trip is fragile (a delimiter char in a screen name, or a mangled
            // multibyte separator, silently drops the destination).
            val result = mutableMapOf<Pair<String, String>, Int>()
            var prevSid: String? = null
            var prevScreen: String? = null

            Events
                .select(Events.sessionId, Events.screen, Events.ts)
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq fromLdt) and
                    (Events.ts lessEq toLdt) and
                    (Events.eventName eq "screen_view") and
                    Events.screen.isNotNull() and
                    (Events.screen neq "") and
                    Events.sessionId.isNotNull()
                }
                .orderBy(Events.sessionId to SortOrder.ASC, Events.ts to SortOrder.ASC)
                .forEach { row ->
                    val sid = row[Events.sessionId]
                    val screen = row[Events.screen]!!
                    val prev = prevScreen
                    if (sid == prevSid && prev != null && screen != prev) {
                        val key = prev to screen
                        result[key] = (result[key] ?: 0) + 1
                    }
                    prevSid = sid
                    prevScreen = screen
                }

            result.entries
                .sortedByDescending { it.value }
                .take(50)
                .map { (key, count) ->
                    mapOf("from_screen" to key.first, "to_screen" to key.second, "count" to count)
                }
        }
    }

    fun findDailySessions(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            val fromLdt = LocalDateTime.ofInstant(from, ZoneOffset.UTC)
            val toLdt = LocalDateTime.ofInstant(to, ZoneOffset.UTC)

            Events
                .select(Events.ts.function("DATE").alias("day"), Events.sessionId.countDistinct())
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq fromLdt) and
                    (Events.ts lessEq toLdt) and
                    Events.sessionId.isNotNull()
                }
                .groupBy(Events.ts.function("DATE"))
                .orderBy(Events.ts.function("DATE") to SortOrder.ASC)
                .map {
                    mapOf(
                        "day" to it[Events.ts.function("DATE")]!!.toString(),
                        "total" to it[Events.sessionId.countDistinct()].toInt()
                    )
                }
        }
    }

    fun findRetention(projectId: Long, from: Instant, to: Instant): List<Map<String, Any?>> {
        return transaction(database) {
            val fromLdt = LocalDateTime.ofInstant(from, ZoneOffset.UTC)
            val toLdt = LocalDateTime.ofInstant(to, ZoneOffset.UTC)

            // Get session first-appearance dates
            val sessionFirstDays = Events
                .select(Events.sessionId, Events.ts.function("DATE").alias("first_day"))
                .where {
                    (Events.projectId eq projectId) and
                    (Events.ts greaterEq fromLdt) and
                    (Events.ts lessEq toLdt) and
                    Events.sessionId.isNotNull()
                }
                .groupBy(Events.sessionId)
                .map { it[Events.sessionId] to it[Events.ts.function("DATE")]!!.toString() }

            // Group by cohort date
            val cohorts = sessionFirstDays.groupBy({ it.second }, { it.first })
            val offsets = listOf(1 to "day1", 3 to "day3", 7 to "day7", 14 to "day14", 30 to "day30")
            val result = mutableListOf<Map<String, Any?>>()

            for ((cohortDate, sessionIds) in cohorts) {
                val size = sessionIds.size
                val row = mutableMapOf<String, Any?>("cohort_date" to cohortDate, "size" to size)
                for ((offset, key) in offsets) {
                    val targetDate = java.time.LocalDate.parse(cohortDate).plusDays(offset.toLong())
                    if (targetDate.isAfter(java.time.LocalDate.now())) {
                        row[key] = null
                        continue
                    }
                    val returned = Events
                        .select(Events.sessionId.countDistinct())
                        .where {
                            (Events.projectId eq projectId) and
                            (Events.ts.function("DATE") eq targetDate.atStartOfDay()) and
                            (Events.sessionId inList sessionIds)
                        }
                        .single()[Events.sessionId.countDistinct()].toInt()
                    row[key] = if (size > 0) Math.round(returned.toDouble() / size * 10000) / 10000.0 else 0.0
                }
                result.add(row)
            }

            result
        }
    }
}
