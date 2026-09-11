package com.z3itt.dualis.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeAudioPickTest {
    @Test
    fun prefersUnthrottledAacOverHighBitrateWeb() {
        val web = YoutubeAudioPick.score(
            url = "https://rr1.googlevideo.com/videoplayback?n=obfuscatedNsig&id=a",
            itag = 251,
            bitrate = 160,
            codec = "opus",
            formatName = "webm",
            progressive = true,
        )
        val ios = YoutubeAudioPick.score(
            url = "https://rr1.googlevideo.com/videoplayback?id=a",
            itag = 140,
            bitrate = 128,
            codec = "mp4a.40.2",
            formatName = "m4a",
            progressive = true,
        )
        assertTrue(ios > web)
        assertTrue(YoutubeAudioPick.hasThrottleParam("https://x/?n=abcdefghij"))
        assertFalse(YoutubeAudioPick.hasThrottleParam("https://x/?id=a"))
    }

    @Test
    fun formatSpeedHidesTinyBursts() {
        assertEquals("", YoutubeAudioPick.formatSpeed(1_000.0))
        assertTrue(YoutubeAudioPick.formatSpeed(80_000.0).contains("KB/s"))
        assertTrue(YoutubeAudioPick.formatSpeed(2_400_000.0).contains("MB/s"))
    }
}
