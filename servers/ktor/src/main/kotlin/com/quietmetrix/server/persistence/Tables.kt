package com.quietmetrix.server.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

object Projects : Table("projects") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val ownerUserId = long("owner_user_id").references(Users.id)
    val writeKeyHash = varchar("write_key_hash", 255).index()
    val readKeyHash = varchar("read_key_hash", 255)
    val planId = varchar("plan_id", 50).nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

object EventsInbox : Table("events_inbox") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val payload = org.jetbrains.exposed.sql.json.jsonb("payload", org.jetbrains.exposed.sql.DatabaseConfig().h2Mode)
    val receivedAt = datetime("received_at")
    val processed = bool("processed").default(false)

    override val primaryKey = PrimaryKey(id)
}

object Events : Table("events") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val eventName = varchar("event_name", 255)
    val screen = varchar("screen", 255).nullable()
    val props = org.jetbrains.exposed.sql.json.jsonb("props", org.jetbrains.exposed.sql.DatabaseConfig().h2Mode).nullable()
    val sessionId = varchar("session_id", 128).nullable()
    val ts = datetime("ts")
    val wasOffline = bool("was_offline").default(false)
    val country = char("country", 2).nullable()
    val deviceClass = varchar("device_class", 20).nullable()
    val language = varchar("language", 10).nullable()
    val platform = varchar("platform", 20).nullable()
    val sdkVersion = varchar("sdk_version", 20).nullable()
    val receivedAt = datetime("received_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(true, listOf(projectId, ts))
        index(false, listOf(projectId, eventName, ts))
    }
}

object EventCountsDaily : Table("event_counts_daily") {
    val projectId = long("project_id").references(Projects.id)
    val day = org.jetbrains.exposed.sql.javatime.date("day")
    val eventName = varchar("event_name", 255)
    val count = long("count").default(0L)

    override val primaryKey = PrimaryKey(projectId, day, eventName)
}

object UsageCounters : Table("usage_counters") {
    val projectId = long("project_id").references(Projects.id)
    val period = varchar("period", 6)
    val eventsCount = long("events_count").default(0L)

    override val primaryKey = PrimaryKey(projectId, period)
}