package com.z3itt.dualis.download

/**
 * Prefer streams YouTube does not throttle (~50-100 KB/s).
 * ANDROID/iOS URLs usually have no `n=` parameter. itag 140 (AAC 128k) is enough for stems.
 */
object YoutubeAudioPick {
    fun score(
        url: String,
        itag: Int,
        bitrate: Int,
        codec: String?,
        formatName: String?,
        progressive: Boolean,
    ): Int {
        var score = 0
        if (progressive) score += 500
        if (!hasThrottleParam(url)) score += 1000
        if (itag == 140 || itag == 139) score += 300
        val codecText = listOf(codec, formatName).joinToString(" ").lowercase()
        if ("mp4a" in codecText || "m4a" in codecText || "aac" in codecText) score += 120
        score += when {
            bitrate in 96..192 -> 150
            bitrate in 64..256 -> 80
            bitrate > 0 -> 20
            else -> 0
        }
        return score
    }

    fun hasThrottleParam(url: String): Boolean {
        val query = url.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return false
        return query.split('&').any { part ->
            part.startsWith("n=") && part.length > 2
        }
    }

    fun formatSpeed(bytesPerSec: Double): String {
        if (bytesPerSec < 20_000) return ""
        val kb = bytesPerSec / 1000.0
        val mb = bytesPerSec / 1_000_000.0
        return if (mb < 1) "%.0f KB/s".format(kb) else "%.1f MB/s".format(mb)
    }
}
