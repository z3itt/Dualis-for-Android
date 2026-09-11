package com.z3itt.dualis.domain.ingest

object LinkParser {
    private val urlFinder = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val videoId = Regex(
        """(?:v=|/watch/|/embed/|/shorts/|/live/|youtu\.be/)([A-Za-z0-9_-]{11})""",
        RegexOption.IGNORE_CASE,
    )
    private val playlistId = Regex("""[?&]list=([A-Za-z0-9_-]{10,})""", RegexOption.IGNORE_CASE)

    fun splitInputs(value: String): List<String> {
        val cleaned = sanitize(value)
        val urls = urlFinder.findAll(cleaned)
            .map { it.value.trimEnd('.', ',', ';', ')', ']') }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (urls.isNotEmpty()) return urls
        return cleaned.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun looksLikePlaylist(value: String): Boolean {
        val lower = canonicalizeYoutube(value).lowercase()
        if (lower.contains("/watch") && (lower.contains("v=") || lower.contains("/watch/"))) {
            return false
        }
        return lower.contains("/playlist") ||
            lower.contains("list=") ||
            lower.contains("/browse/vl") ||
            lower.contains("open.spotify.com/playlist") ||
            lower.contains("open.spotify.com/album") ||
            lower.contains("spotify:playlist:") ||
            lower.contains("spotify:album:")
    }

    fun looksLikeUrl(value: String): Boolean {
        val lower = sanitize(value).trim().lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("spotify:") ||
            lower.contains("youtube.com") ||
            lower.contains("youtu.be") ||
            lower.contains("open.spotify.com")
    }

    fun sourceKind(value: String): String {
        val lower = value.lowercase()
        return when {
            lower.contains("spotify") -> "spotify"
            lower.contains("music.youtube") -> "ytmusic"
            lower.contains("youtube") || lower.contains("youtu.be") -> "youtube"
            else -> "url"
        }
    }

    fun canonicalizeSpotify(input: String): String {
        val cleaned = sanitize(input).trim()
        when {
            cleaned.startsWith("spotify:track:") ->
                return "https://open.spotify.com/track/${cleaned.removePrefix("spotify:track:")}"
            cleaned.startsWith("spotify:playlist:") ->
                return "https://open.spotify.com/playlist/${cleaned.removePrefix("spotify:playlist:")}"
            cleaned.startsWith("spotify:album:") ->
                return "https://open.spotify.com/album/${cleaned.removePrefix("spotify:album:")}"
        }
        val url = extractUrl(cleaned) ?: cleaned
        return url.split("?").first()
    }

    fun youtubeVideoId(value: String): String? =
        videoId.find(sanitize(value))?.groupValues?.get(1)

    fun youtubeThumbnailUrls(videoId: String): List<String> = listOf(
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
        "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
    )

    fun coverCandidates(sourceUrl: String?, ytdlpQuery: String?): List<String> {
        val id = listOfNotNull(sourceUrl, ytdlpQuery)
            .firstNotNullOfOrNull { youtubeVideoId(it) }
            ?: return emptyList()
        return youtubeThumbnailUrls(id)
    }

    fun canonicalizeYoutube(input: String): String {
        val raw = extractUrl(sanitize(input)) ?: sanitize(input).trim()
        val withScheme = when {
            raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true) -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.contains("youtube.com", ignoreCase = true) ||
                raw.contains("youtu.be", ignoreCase = true) -> "https://$raw"
            else -> raw
        }
        val vid = youtubeVideoId(withScheme)
        val list = playlistId.find(withScheme)?.groupValues?.get(1)
        val watchLike = withScheme.contains("/watch", ignoreCase = true) ||
            withScheme.contains("youtu.be/", ignoreCase = true) ||
            withScheme.contains("/shorts/", ignoreCase = true) ||
            withScheme.contains("/live/", ignoreCase = true) ||
            withScheme.contains("/embed/", ignoreCase = true)
        if (vid != null && (watchLike || list == null || isYoutubeMix(list))) {
            return "https://www.youtube.com/watch?v=$vid"
        }
        if (list != null && !watchLike) {
            return "https://www.youtube.com/playlist?list=$list"
        }
        return stripTracking(
            withScheme
                .replace("https://music.youtube.com", "https://www.youtube.com", ignoreCase = true)
                .replace("http://music.youtube.com", "https://www.youtube.com", ignoreCase = true),
        )
    }

    private fun isYoutubeMix(list: String): Boolean {
        val id = list.uppercase()
        return id.startsWith("RD") || id.startsWith("UL") || id.startsWith("UU")
    }

    private fun extractUrl(value: String): String? =
        urlFinder.find(value)?.value?.trimEnd('.', ',', ';', ')', ']')

    private fun stripTracking(url: String): String {
        val qIndex = url.indexOf('?')
        if (qIndex < 0) return url
        val base = url.substring(0, qIndex)
        val kept = url.substring(qIndex + 1).split('&').filter { param ->
            val key = param.substringBefore('=').lowercase()
            key.isNotEmpty() && key !in TRACKING_KEYS && !key.startsWith("utm_")
        }
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }

    private fun sanitize(value: String): String =
        value
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace('\u00A0', ' ')
            .replace('\u2028', '\n')
            .replace('\u2029', '\n')

    private val TRACKING_KEYS = setOf(
        "si", "feature", "pp", "spm", "ab_channel",
    )
}
