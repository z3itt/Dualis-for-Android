package com.z3itt.dualis.domain.ingest

/**
 * Mirrors desktop `download.rs` Spotify → YouTube search rules.
 * Spotify tracks must never be handed to the downloader as open.spotify.com URLs.
 */
object SpotifyQuery {
    fun isPlaceholderArtist(artist: String): Boolean {
        val trimmed = artist.trim()
        return trimmed.isEmpty() ||
            trimmed.equals("unknown artist", ignoreCase = true) ||
            trimmed.equals("spotify", ignoreCase = true) ||
            trimmed.equals("various artists", ignoreCase = true)
    }

    fun splitTitleArtist(raw: String): Pair<String, String?> {
        val cleaned = raw.trim().removeSuffix(" | Spotify").trim()
        val lyrics = cleaned.split(" - song and lyrics by ")
        if (lyrics.size == 2) {
            return lyrics[0].trim() to lyrics[1].trim()
        }
        for (sep in listOf(" · ", " – ", " \u2014 ", " - ")) {
            val parts = cleaned.split(sep, limit = 2)
            if (parts.size == 2) {
                val title = parts[0].trim()
                val artist = parts[1]
                    .split(sep)
                    .first()
                    .trim()
                    .removeSuffix(" Spotify")
                    .trim()
                if (title.isNotEmpty() && artist.isNotEmpty() && !artist.equals("spotify", ignoreCase = true)) {
                    return title to artist
                }
            }
        }
        return cleaned to null
    }

    fun youtubeSearchQuery(title: String, artist: String): String {
        val (cleanTitle, fromTitle) = splitTitleArtist(title)
        val resolvedArtist = if (isPlaceholderArtist(artist)) fromTitle.orEmpty() else artist.trim()
        return if (resolvedArtist.isEmpty()) {
            "ytsearch1:$cleanTitle"
        } else {
            "ytsearch1:$cleanTitle $resolvedArtist"
        }
    }

    /**
     * Query handed to the download backend. Prefer the stored search string so
     * Spotify retries do not hit open.spotify.com instead of YouTube.
     */
    fun resolveDownloadQuery(
        sourceKind: String,
        title: String,
        artist: String,
        sourceUrl: String?,
        stored: String?,
    ): String {
        val query = stored?.trim().orEmpty()
        if (query.isNotEmpty()) return query
        if (sourceKind.equals("spotify", ignoreCase = true)) {
            return youtubeSearchQuery(title, artist)
        }
        return sourceUrl.orEmpty()
    }

    fun spotifyItem(title: String, artist: String, sourceUrl: String): Triple<String, String, String> {
        val (cleanTitle, fromTitle) = splitTitleArtist(title)
        val resolvedArtist = if (isPlaceholderArtist(artist)) fromTitle ?: artist else artist
        return Triple(cleanTitle, resolvedArtist, youtubeSearchQuery(cleanTitle, resolvedArtist))
    }

    /** Same patterns as desktop `extract_spotify_track_ids`. */
    fun extractTrackIds(html: String): List<String> {
        val ids = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (re in TRACK_ID_PATTERNS) {
            for (match in re.findAll(html)) {
                val id = match.groupValues[1]
                if (seen.add(id)) ids.add(id)
            }
        }
        return ids
    }

    fun embedUrl(url: String): String? {
        url.split("/playlist/").getOrNull(1)?.let { rest ->
            return "https://open.spotify.com/embed/playlist/${rest.trimEnd('/')}"
        }
        url.split("/album/").getOrNull(1)?.let { rest ->
            return "https://open.spotify.com/embed/album/${rest.trimEnd('/')}"
        }
        return null
    }

    private val TRACK_ID_PATTERNS = listOf(
        Regex("spotify:track:([A-Za-z0-9]{22})"),
        Regex("open\\.spotify\\.com/track/([A-Za-z0-9]{22})"),
        Regex("\"uri\"\\s*:\\s*\"spotify:track:([A-Za-z0-9]{22})\""),
    )
}
