package com.quietmetrix.server.ingest

import com.quietmetrix.server.domain.TrackEventRequest
import com.quietmetrix.server.persistence.EventRepository
import com.quietmetrix.server.persistence.tables.EventsInbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(IngestChannel::class.java)

class IngestChannel(
    private val eventRepository: EventRepository,
    private val eventValidator: EventValidator,
    private val eventNormalizer: EventNormalizer,
) {
    private val channel = Channel<IngestItem>(Channel.BUFFERED)
    private val json = Json { encodeDefaults = true }
    private var consumerJob: Job? = null
    private var processorJob: Job? = null

    fun start(scope: CoroutineScope) {
        consumerJob = scope.launch(Dispatchers.IO) {
            for (item in channel) {
                try {
                    val event = eventNormalizer.normalize(item.request, item.projectId, item.clientIp, item.installHash)
                    eventRepository.insert(event)
                } catch (e: Exception) {
                    logger.error("Failed to process event for project ${item.projectId}", e)
                }
            }
        }
        processorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10_000)
                processInbox()
            }
        }
    }

    fun stop() {
        consumerJob?.cancel()
        processorJob?.cancel()
        channel.close()
    }

    suspend fun enqueue(projectId: String, request: TrackEventRequest, clientIp: String? = null, installHash: String? = null) {
        channel.send(IngestItem(projectId, request, clientIp, false, installHash))
    }

    suspend fun enqueueBatch(projectId: String, request: TrackEventRequest, clientIp: String? = null, installHash: String? = null) {
        channel.send(IngestItem(projectId, request, clientIp, true, installHash))
    }

    private suspend fun processInbox() = withContext(Dispatchers.IO) {
        try {
            transaction {
                val rows = EventsInbox.selectAll()
                    .where { EventsInbox.processed eq false }
                    .limit(100)
                    .toList()

                for (row in rows) {
                    try {
                        val payload = row[EventsInbox.payload]
                        val request = json.decodeFromString(TrackEventRequest.serializer(), payload)
                        val event = eventNormalizer.normalize(
                            request,
                            row[EventsInbox.projectId].toString(),
                            null
                        )
                        eventRepository.insert(event)
                        EventsInbox.update({ EventsInbox.id eq row[EventsInbox.id] }) {
                            it[processed] = true
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to process inbox item ${row[EventsInbox.id]}", e)
                        EventsInbox.update({ EventsInbox.id eq row[EventsInbox.id] }) {
                            it[processed] = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Inbox processor error", e)
        }
    }
}

data class IngestItem(
    val projectId: String,
    val request: TrackEventRequest,
    val clientIp: String?,
    val batch: Boolean,
    val installHash: String? = null,
)
