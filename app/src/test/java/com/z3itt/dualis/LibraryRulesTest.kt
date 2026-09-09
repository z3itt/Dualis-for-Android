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
