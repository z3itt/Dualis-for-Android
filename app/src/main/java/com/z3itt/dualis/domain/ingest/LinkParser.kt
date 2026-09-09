package com.z3itt.dualis.domain.ingest

object LinkParser {
    fun splitInputs(value: String): List<String> =
        value.split(Regex("[\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }

    fun looksLikePlaylist(value: String): Boolean {
        val lower = value.lowercase()
        if (lower.contains("/watch") && lower.contains("v=")) return false
        return lower.contains("/playlist") ||
            lower.contains("list=") ||
            lower.contains("/browse/vl") ||
            lower.contains("open.spotify.com/playlist") ||
            lower.contains("open.spotify.com/album") ||
            lower.contains("spotify:playlist:") ||
            lower.contains("spotify:album:")
    }

    fun looksLikeUrl(value: String): Boolean {
        val lower = value.trim().lowercase()
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
        when {
            input.startsWith("spotify:track:") ->
                return "https://open.spotify.com/track/${input.removePrefix("spotify:track:")}"
            input.startsWith("spotify:playlist:") ->
                return "https://open.spotify.com/playlist/${input.removePrefix("spotify:playlist:")}"
            input.startsWith("spotify:album:") ->
                return "https://open.spotify.com/album/${input.removePrefix("spotify:album:")}"
        }
        return input.split("?").first()
    }
}
