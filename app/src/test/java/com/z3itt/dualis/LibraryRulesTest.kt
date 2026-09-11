package com.z3itt.dualis.domain.library

import com.z3itt.dualis.domain.model.LibrarySort
import com.z3itt.dualis.domain.model.Track
import com.z3itt.dualis.domain.model.TrackStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRulesTest {
    @Test
    fun stageIndexMapsSeparateToInfer() {
        assertEquals(0, LibraryRules.stageIndex("download"))
        assertEquals(2, LibraryRules.stageIndex("separate"))
        assertEquals(3, LibraryRules.stageIndex("export"))
    }

    @Test
    fun sortRecentPutsNewestFirst() {
        val tracks = listOf(track("old", 1), track("new", 10))
        assertEquals("new", LibraryRules.sortTracks(tracks, LibrarySort.RECENT).first().title)
    }

    @Test
    fun filterMatchesTitleOrArtist() {
        val tracks = listOf(track("Introvert", 1, "ReoNa"), track("Nightcall", 2, "Kavinsky"))
        assertEquals(1, LibraryRules.filterTracks(tracks, "reo").size)
    }

    @Test
    fun waitingTracksFollowQueueOrderAndSkipLive() {
        val live = track("live", 1).copy(id = "live", status = TrackStatus.SEPARATING)
        val first = track("first", 2).copy(id = "first", status = TrackStatus.QUEUED)
        val second = track("second", 3).copy(id = "second", status = TrackStatus.QUEUED)
        val waiting = LibraryRules.waitingTracks(
            listOf(live, second, first),
            listOf("live", "second", "first"),
        )
        assertEquals(listOf("second", "first"), waiting.map { it.id })
        assertEquals(3, LibraryRules.jobBadgeCount(1, waiting.size))
    }

    @Test
    fun libraryHidesFailedTracksAndJobsKeepThem() {
        val ready = track("ready", 1)
        val failed = track("fail", 2).copy(status = TrackStatus.ERROR)
        assertEquals(listOf("ready"), LibraryRules.standaloneTracks(listOf(ready, failed)).map { it.title })
        val jobs = LibraryRules.failedJobTracks(
            tracks = emptyList(),
            failedTracks = mapOf(failed.id to failed),
            hiddenIds = emptySet(),
        )
        assertEquals(listOf("fail"), jobs.map { it.title })
    }

    @Test
    fun stemFileNameStripsPathChars() {
        assertEquals("Peter Pan - vocals.wav", LibraryRules.stemFileName("Peter Pan", "vocals"))
        assertEquals("a_b - instrumental.wav", LibraryRules.stemFileName("a/b", "instrumental"))
    }

    private fun track(title: String, created: Long, artist: String = "Local") = Track(
        id = title,
        title = title,
        artist = artist,
        sourceKind = "local",
        status = TrackStatus.READY,
        createdAt = created,
        updatedAt = created,
    )
}
