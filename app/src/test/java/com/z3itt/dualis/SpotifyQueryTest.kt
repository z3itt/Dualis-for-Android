package com.z3itt.dualis.domain.ingest

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyQueryTest {
    @Test
    fun youtubeSearchUsesTitleAndArtist() {
        assertEquals("ytsearch1:Introvert ReoNa", SpotifyQuery.youtubeSearchQuery("Introvert", "ReoNa"))
    }

    @Test
    fun youtubeSearchParsesCombinedTitle() {
        assertEquals(
            "ytsearch1:SAVANA Artist Name",
            SpotifyQuery.youtubeSearchQuery("SAVANA · Artist Name · Spotify", "Unknown artist"),
        )
    }

    @Test
    fun youtubeSearchParsesLyricsBy() {
        assertEquals(
            "ytsearch1:Nightcall Kavinsky",
            SpotifyQuery.youtubeSearchQuery("Nightcall - song and lyrics by Kavinsky | Spotify", "Spotify"),
        )
    }

    @Test
    fun retryUsesStoredYoutubeSearch() {
        assertEquals(
            "ytsearch1:Introvert ReoNa",
            SpotifyQuery.resolveDownloadQuery(
                "spotify",
                "Introvert",
                "ReoNa",
                "https://open.spotify.com/track/abc",
                "ytsearch1:Introvert ReoNa",
            ),
        )
    }

    @Test
    fun retryRebuildsSpotifySearchWithoutStoredQuery() {
        assertEquals(
            "ytsearch1:Introvert ReoNa",
            SpotifyQuery.resolveDownloadQuery(
                "spotify",
                "Introvert",
                "ReoNa",
                "https://open.spotify.com/track/abc",
                null,
            ),
        )
    }

    @Test
    fun youtubeRetryKeepsSourceUrlWhenNoStoredQuery() {
        assertEquals(
            "https://youtu.be/xyz",
            SpotifyQuery.resolveDownloadQuery(
                "youtube",
                "Song",
                "Artist",
                "https://youtu.be/xyz",
                null,
            ),
        )
    }
}
