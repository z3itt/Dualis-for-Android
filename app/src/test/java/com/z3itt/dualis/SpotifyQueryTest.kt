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
    fun youtubeSearchParsesEmDashTitle() {
        assertEquals(
            "ytsearch1:Introvert ReoNa",
            SpotifyQuery.youtubeSearchQuery("Introvert \u2014 ReoNa", "Unknown artist"),
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
    fun extractTrackIdsKeepsFirstOccurrenceOrder() {
        val html = """
            spotify:track:4iV5W9uYEdYUVa79Axb7Rh
            https://open.spotify.com/track/7ouMYWpwJ422jRcKU4soKr
            "uri":"spotify:track:4iV5W9uYEdYUVa79Axb7Rh"
            spotify:track:3n3Ppam7vgaVa1iaRUc9Lp
        """.trimIndent()
        assertEquals(
            listOf(
                "4iV5W9uYEdYUVa79Axb7Rh",
                "3n3Ppam7vgaVa1iaRUc9Lp",
                "7ouMYWpwJ422jRcKU4soKr",
            ),
            SpotifyQuery.extractTrackIds(html),
        )
    }

    @Test
    fun embedUrlUsesPlaylistAndAlbumPaths() {
        assertEquals(
            "https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M",
            SpotifyQuery.embedUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"),
        )
        assertEquals(
            "https://open.spotify.com/embed/album/5NTmsVSXhyEceR35rKz4S4",
            SpotifyQuery.embedUrl("https://open.spotify.com/intl-tr/album/5NTmsVSXhyEceR35rKz4S4"),
        )
        assertEquals(null, SpotifyQuery.embedUrl("https://open.spotify.com/track/abc"))
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
