package com.z3itt.dualis.domain.queue

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
    private val lock = Any()

    fun enqueue(item: WorkItem, front: Boolean) {
        synchronized(lock) {
            if (queued.contains(item.trackId)) {
                if (front) {
                    val existing = pending.find { it.trackId == item.trackId }
                    if (existing != null) {
                        pending.remove(existing)
                        pending.addFirst(existing)
                    }
                }
                return
            }
            queued.add(item.trackId)
            if (front) pending.addFirst(item) else pending.addLast(item)
        }
    }

    fun finish(trackId: String) {
        synchronized(lock) {
            queued.remove(trackId)
            pending.removeAll { it.trackId == trackId }
        }
    }

    fun pop(): WorkItem? = synchronized(lock) { pending.removeFirstOrNull() }

    fun waiting(): Int = synchronized(lock) { pending.size }

    fun snapshotIds(): List<String> = synchronized(lock) { pending.map { it.trackId } }

    fun isQueued(trackId: String): Boolean = synchronized(lock) { queued.contains(trackId) }
}
