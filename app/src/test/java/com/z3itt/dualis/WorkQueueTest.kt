package com.z3itt.dualis.domain.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkQueueTest {
    @Test
    fun processesOneAtATimeInOrder() {
        val queue = WorkQueue()
        queue.enqueue(WorkItem("a", "q-a"), false)
        queue.enqueue(WorkItem("b", "q-b"), false)
        queue.enqueue(WorkItem("c", "q-c"), false)
        assertEquals("a", queue.pop()?.trackId)
        assertEquals(2, queue.waiting())
        assertEquals("b", queue.pop()?.trackId)
        assertEquals("c", queue.pop()?.trackId)
        assertNull(queue.pop())
    }

    @Test
    fun duplicateEnqueueIsIgnoredUnlessPrioritized() {
        val queue = WorkQueue()
        queue.enqueue(WorkItem("a", "q-a"), false)
        queue.enqueue(WorkItem("b", "q-b"), false)
        queue.enqueue(WorkItem("a", "q-a-again"), false)
        assertEquals(listOf("a", "b"), queue.snapshotIds())
        queue.enqueue(WorkItem("b", "q-b"), true)
        assertEquals(listOf("b", "a"), queue.snapshotIds())
    }

    @Test
    fun retryGoesToFront() {
        val queue = WorkQueue()
        queue.enqueue(WorkItem("a", "q-a"), false)
        queue.enqueue(WorkItem("b", "q-b"), false)
        queue.enqueue(WorkItem("retry", "ytsearch1:Introvert ReoNa"), true)
        val first = queue.pop()
        assertEquals("retry", first?.trackId)
        assertEquals("ytsearch1:Introvert ReoNa", first?.query)
        assertEquals("a", queue.pop()?.trackId)
    }

    @Test
    fun cancelDropsPendingAndBlocksInFlight() {
        val queue = WorkQueue()
        queue.enqueue(WorkItem("a", "q-a"), false)
        queue.enqueue(WorkItem("b", "q-b"), false)
        val running = queue.pop()!!
        queue.cancel(running.trackId)
        queue.cancel("b")
        assertEquals(true, queue.isCancelled(running.trackId))
        assertNull(queue.pop())
        queue.enqueue(WorkItem("a", "q-a-retry"), false)
        assertEquals(false, queue.isCancelled("a"))
        assertEquals("a", queue.pop()?.trackId)
    }

    @Test
    fun idleOnlyAfterFinish() {
        val queue = WorkQueue()
        assertEquals(true, queue.isIdle())
        queue.enqueue(WorkItem("a", "q-a"), false)
        assertEquals(false, queue.isIdle())
        val item = queue.pop()!!
        assertEquals(false, queue.isIdle())
        queue.finish(item.trackId)
        assertEquals(true, queue.isIdle())
    }

    @Test
    fun finishRemovesFromQueuedSet() {
        val queue = WorkQueue()
        queue.enqueue(WorkItem("a", "q-a"), false)
        val item = queue.pop()!!
        queue.finish(item.trackId)
        queue.enqueue(WorkItem("a", "q-a-2"), false)
        assertEquals("a", queue.pop()?.trackId)
    }

    @Test
    fun pendingIdsDropsTheRunningJob() {
        val queue = WorkQueue()
        queue.enqueue(WorkItem("a", "q-a"), false)
        queue.enqueue(WorkItem("b", "q-b"), false)
        assertEquals(listOf("a", "b"), queue.pendingIds.value)
        queue.pop()
        assertEquals(listOf("b"), queue.pendingIds.value)
        queue.finish("a")
        assertEquals(listOf("b"), queue.pendingIds.value)
    }
}
