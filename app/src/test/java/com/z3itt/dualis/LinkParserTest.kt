package com.z3itt.dualis.domain.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LinkParserTest {
    @Test
    fun canonicalizesYoutubeMusicShareLinkWithHyphenId() {
        assertEquals(
            "https://www.youtube.com/watch?v=-CxLJd6yRUk",
            LinkParser.canonicalizeYoutube(
                "https://music.youtube.com/watch?v=-CxLJd6yRUk&si=CpJAhurj5l97yWM9",
            ),
        )
    }

    @Test
    fun extractsUrlFromShareText() {
        assertEquals(
            listOf("https://music.youtube.com/watch?v=-CxLJd6yRUk&si=CpJAhurj5l97yWM9"),
            LinkParser.splitInputs(
                "Artist - Title https://music.youtube.com/watch?v=-CxLJd6yRUk&si=CpJAhurj5l97yWM9",
            ),
        )
    }

    @Test
    fun watchWithListIsStillAVideo() {
        assertFalse(
            LinkParser.looksLikePlaylist(
                "https://music.youtube.com/watch?v=abc123abc12&list=RDAMVMabc123abc12",
            ),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=abc123abc12",
            LinkParser.canonicalizeYoutube(
                "https://music.youtube.com/watch?v=abc123abc12&list=RDAMVMabc123abc12",
            ),
        )
    }

    @Test
    fun playlistPageKeepsListId() {
        assertEquals(
            "https://www.youtube.com/playlist?list=PLrEnWoR732-DtKgaDdnPkezM_nDidBU9H",
            LinkParser.canonicalizeYoutube(
                "https://music.youtube.com/playlist?list=PLrEnWoR732-DtKgaDdnPkezM_nDidBU9H",
            ),
        )
    }

    @Test
    fun readsVideoIdFromYoutubeMusicShare() {
        assertEquals(
            "-CxLJd6yRUk",
            LinkParser.youtubeVideoId(
                "https://music.youtube.com/watch?v=-CxLJd6yRUk&si=kNHP_YyHdT0jz_tX",
            ),
        )
    }

    @Test
    fun coverCandidatesPreferHqThumbnail() {
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/-CxLJd6yRUk/hqdefault.jpg",
                "https://i.ytimg.com/vi/-CxLJd6yRUk/sddefault.jpg",
                "https://i.ytimg.com/vi/-CxLJd6yRUk/mqdefault.jpg",
            ),
            LinkParser.coverCandidates(
                "https://music.youtube.com/watch?v=-CxLJd6yRUk&si=abc",
                null,
            ),
        )
    }

    @Test
    fun youtuBeAndShortsBecomeWatchUrls() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9wgCcc",
            LinkParser.canonicalizeYoutube("https://youtu.be/dQw4w9wgCcc?si=abc"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9wgCcc",
            LinkParser.canonicalizeYoutube("https://www.youtube.com/shorts/dQw4w9wgCcc"),
        )
    }
}
