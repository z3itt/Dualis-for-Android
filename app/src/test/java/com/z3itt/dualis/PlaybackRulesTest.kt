package com.z3itt.dualis.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRulesTest {
    @Test
    fun nextWrapsWhenRequested() {
        val ids = listOf("a", "b", "c")
        assertEquals("a", PlaybackRules.nextInOrder(ids, "c", 1, wrap = true))
        assertEquals(null, PlaybackRules.nextInOrder(ids, "c", 1, wrap = false))
        assertEquals("c", PlaybackRules.nextInOrder(ids, "a", -1, wrap = true))
    }

    @Test
    fun queueContextPrefersReadyQueuedIds() {
        val (ids, context) = PlaybackRules.playbackIds(
            readyIds = listOf("a", "b", "c"),
            queue = listOf("c", "a"),
            currentId = "c",
            currentPlaylistId = null,
            playlistReadyIds = emptyList(),
            lockedContext = "",
        )
        assertEquals(listOf("c", "a"), ids)
        assertEquals("queue", context)
    }
}
