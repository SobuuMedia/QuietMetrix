package com.quietmetrix.analytics.internal.transport

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object EventQueue {
    private val queue = ArrayDeque<EnqueuedEvent>()
    private var maxSize: Int = 1000
    private val mutex = Mutex()

    fun configure(maxSize: Int) {
        this.maxSize = maxSize
    }

    suspend fun enqueue(event: EnqueuedEvent): Boolean {
        mutex.withLock {
            if (queue.size >= maxSize) {
                queue.removeFirst()
            }
            queue.addLast(event)
        }
        return true
    }

    suspend fun drain(maxBatchSize: Int = 100): List<EnqueuedEvent> {
        return mutex.withLock {
            val batch = queue.take(maxBatchSize)
            repeat(batch.size) { queue.removeFirst() }
            batch
        }
    }

    suspend fun reEnqueue(events: List<EnqueuedEvent>) {
        mutex.withLock {
            for (event in events.reversed()) {
                queue.addFirst(event)
            }
        }
    }

    suspend fun size(): Int = mutex.withLock { queue.size }

    suspend fun isEmpty(): Boolean = mutex.withLock { queue.isEmpty() }

    suspend fun clear() {
        mutex.withLock { queue.clear() }
    }

    suspend fun persistToStore(): List<EnqueuedEvent> {
        return mutex.withLock { queue.toList() }
    }
}
