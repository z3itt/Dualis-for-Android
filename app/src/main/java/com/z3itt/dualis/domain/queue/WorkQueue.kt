package com.z3itt.dualis.domain.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkItem(
    val trackId: String,
    val query: String,
)

/**
 * One job at a time. Matches desktop `queue.rs`.
 * Retry / prioritize push an existing item to the front.
 */
class WorkQueue {
    private val pending = ArrayDeque<WorkItem>()
    private val queued = linkedSetOf<String>()
    private val cancelled = linkedSetOf<String>()
    private val lock = Any()
    private val pendingIdsState = MutableStateFlow<List<String>>(emptyList())

    val pendingIds: StateFlow<List<String>> = pendingIdsState.asStateFlow()

    fun enqueue(item: WorkItem, front: Boolean) {
        synchronized(lock) {
            cancelled.remove(item.trackId)
            if (queued.contains(item.trackId)) {
                if (front) {
                    val existing = pending.find { it.trackId == item.trackId }
                    if (existing != null) {
                        pending.remove(existing)
                        pending.addFirst(existing)
                    }
                }
                publishLocked()
                return
            }
            queued.add(item.trackId)
            if (front) pending.addFirst(item) else pending.addLast(item)
            publishLocked()
        }
    }

    fun finish(trackId: String) {
        synchronized(lock) {
            queued.remove(trackId)
            pending.removeAll { it.trackId == trackId }
            cancelled.remove(trackId)
            publishLocked()
        }
    }

    /** Drop a pending or in-flight job so it will not write the track back. */
    fun cancel(trackId: String) {
        synchronized(lock) {
            cancelled.add(trackId)
            queued.remove(trackId)
            pending.removeAll { it.trackId == trackId }
            publishLocked()
        }
    }

    fun isCancelled(trackId: String): Boolean = synchronized(lock) { cancelled.contains(trackId) }

    fun pop(): WorkItem? = synchronized(lock) {
        val item = pending.removeFirstOrNull()
        publishLocked()
        item
    }

    fun waiting(): Int = synchronized(lock) { pending.size }

    fun snapshotIds(): List<String> = synchronized(lock) { pending.map { it.trackId } }

    fun isQueued(trackId: String): Boolean = synchronized(lock) { queued.contains(trackId) }

    fun isIdle(): Boolean = synchronized(lock) { pending.isEmpty() && queued.isEmpty() }

    private fun publishLocked() {
        pendingIdsState.value = pending.map { it.trackId }
    }
}
