package com.z3itt.dualis.download

import com.z3itt.dualis.domain.ingest.LinkParser
import com.z3itt.dualis.domain.ingest.SpotifyQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 2 link ingest. YouTube uses NewPipe Extractor (Java, FOSS).
 * Spotify uses oEmbed, playlist/album embed JSON, then YouTube search via title + artist.
 * Stored `ytdlpQuery` stays in `ytsearch1:` form so retries match desktop.
 */
class LinkBackend(
    private val http: OkHttpClient = defaultHttp(),
) : DownloadBackend {
    private val mediaHttp: OkHttpClient = http.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    init {
        ensureNewPipe(http)
    }

    override suspend fun resolve(input: String): ResolvedBatch = withContext(Dispatchers.IO) {
        val kind = LinkParser.sourceKind(input)
        if (kind == "spotify") {
            resolveSpotify(LinkParser.canonicalizeSpotify(input))
        } else {
            resolveYoutube(input)
        }
    }

    override suspend fun download(
        query: String,
        destFile: File,
        onProgress: (Float, String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()
        val url = if (query.startsWith("ytsearch1:")) {
            searchYoutube(query.removePrefix("ytsearch1:").trim())
        } else {
            recoverYoutubeWatchUrl(query, LinkParser.canonicalizeYoutube(query))
        }
        onProgress(0.05f, "Resolving audio stream")
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val audio = pickAudio(info.audioStreams) ?: error("No audio stream for $url")
        onProgress(0.1f, "Downloading ${info.name}")
        val videoId = info.id.ifBlank { LinkParser.youtubeVideoId(url).orEmpty() }
        val referer = if (videoId.isNotBlank()) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            "https://www.youtube.com/"
        }
        downloadUrl(audio.content, destFile, referer, onProgress)
        destFile
    }

    fun coverUrls(sourceUrl: String?, ytdlpQuery: String?, coverUrl: String? = null): List<String> {
        val urls = mutableListOf<String>()
        coverUrl?.takeIf { it.isNotBlank() }?.let(urls::add)
        urls += LinkParser.coverCandidates(sourceUrl, ytdlpQuery)
        if (urls.none { it.contains("scdn.co") || it.contains("spotifycdn") } &&
            !sourceUrl.isNullOrBlank() &&
            sourceUrl.contains("spotify", ignoreCase = true)
        ) {
            runCatching { spotifyOembed(sourceUrl).third }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(urls::add)
        }
        return urls.distinct()
    }

    fun saveCover(dest: File, urls: List<String>): Boolean {
        if (dest.isFile && dest.length() > 64L) return true
        dest.parentFile?.mkdirs()
        for (url in urls) {
            if (url.isBlank()) continue
            if (downloadCover(url, dest)) return true
        }
        return dest.isFile && dest.length() > 64L
    }

    private fun downloadCover(url: String, dest: File): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/*")
                .header("Referer", "https://www.youtube.com/")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val bytes = body.bytes()
                if (bytes.size < 64) return false
                dest.writeBytes(bytes)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveSpotify(url: String): ResolvedBatch {
        if (url.contains("/playlist/") || url.contains("/album/")) {
            return expandSpotifyCollection(url)
        }
        val meta = spotifyTrackMeta(url)
        val (title, artist, ytdlpQuery) = SpotifyQuery.spotifyItem(meta.first, meta.second, url)
        return ResolvedBatch(
            playlist = null,
            items = listOf(
                ResolvedItem(
                    title = title,
                    artist = artist,
                    sourceUrl = url,
                    sourceKind = "spotify",
                    ytdlpQuery = ytdlpQuery,
                    coverUrl = meta.third,
                ),
            ),
        )
    }

    private fun expandSpotifyCollection(url: String): ResolvedBatch {
        val tracks = scrapeSpotifyTracks(url)
        val meta = runCatching { spotifyOembed(url) }.getOrNull()
        if (tracks.isEmpty()) {
            val title = meta?.first?.takeIf { it.isNotBlank() } ?: "this collection"
            throw DownloadException(
                "Could not read Spotify tracks from \"$title\". Open the playlist, copy individual track links, or paste the YouTube Music playlist URL.",
            )
        }
        val kind = if (url.contains("/album/")) "spotify-album" else "spotify-playlist"
        return ResolvedBatch(
            playlist = PlaylistMeta(
                title = meta?.first?.takeIf { it.isNotBlank() } ?: "Spotify playlist",
                artist = meta?.second?.takeIf { it.isNotBlank() } ?: "Spotify",
                sourceUrl = url,
                sourceKind = kind,
                coverUrl = meta?.third,
            ),
            items = tracks,
        )
    }

    private fun scrapeSpotifyTracks(url: String): List<ResolvedItem> {
        val pageHtml = runCatching { fetchHtml(url) }.getOrDefault("")
        val embedHtml = SpotifyQuery.embedUrl(url)
            ?.let { runCatching { fetchHtml(it) }.getOrDefault("") }
            .orEmpty()
        val named = tracksFromNextData(embedHtml).ifEmpty { tracksFromNextData(pageHtml) }
        if (named.isNotEmpty()) return named.take(MAX_SPOTIFY_COLLECTION)
        val ids = SpotifyQuery.extractTrackIds(pageHtml).ifEmpty { SpotifyQuery.extractTrackIds(embedHtml) }
        val items = mutableListOf<ResolvedItem>()
        val seen = mutableSetOf<String>()
        for (id in ids) {
            if (!seen.add(id)) continue
            resolvedSpotifyTrack(id)?.let { items.add(it) }
            if (items.size >= MAX_SPOTIFY_COLLECTION) break
        }
        return items
    }

    private fun resolvedSpotifyTrack(id: String): ResolvedItem? {
        val trackUrl = "https://open.spotify.com/track/$id"
        val meta = runCatching { spotifyTrackMeta(trackUrl) }.getOrNull() ?: return null
        val (title, artist, ytdlpQuery) = SpotifyQuery.spotifyItem(meta.first, meta.second, trackUrl)
        return ResolvedItem(
            title = title,
            artist = artist,
            sourceUrl = trackUrl,
            sourceKind = "spotify",
            ytdlpQuery = ytdlpQuery,
            coverUrl = meta.third,
        )
    }

    private fun tracksFromNextData(html: String): List<ResolvedItem> {
        val marker = html.indexOf("id=\"__NEXT_DATA__\"")
        if (marker < 0) return emptyList()
        val jsonStart = html.indexOf('>', marker) + 1
        val jsonEnd = html.indexOf("</script>", jsonStart)
        if (jsonStart <= 0 || jsonEnd <= jsonStart) return emptyList()
        return runCatching {
            val list = findTrackList(JSONObject(html.substring(jsonStart, jsonEnd)))
                ?: return@runCatching emptyList()
            val items = mutableListOf<ResolvedItem>()
            val seen = mutableSetOf<String>()
            for (i in 0 until list.length()) {
                val obj = list.optJSONObject(i) ?: continue
                val id = obj.optString("uri").removePrefix("spotify:track:")
                if (id.length != 22 || !seen.add(id)) continue
                val title = obj.optString("title")
                if (title.isBlank()) continue
                val artist = obj.optString("subtitle").ifBlank { "Unknown artist" }
                val trackUrl = "https://open.spotify.com/track/$id"
                val (cleanTitle, cleanArtist, query) = SpotifyQuery.spotifyItem(title, artist, trackUrl)
                items.add(
                    ResolvedItem(
                        title = cleanTitle,
                        artist = cleanArtist,
                        sourceUrl = trackUrl,
                        sourceKind = "spotify",
                        ytdlpQuery = query,
                    ),
                )
                if (items.size >= MAX_SPOTIFY_COLLECTION) break
            }
            items
        }.getOrDefault(emptyList())
    }

    private fun findTrackList(node: JSONObject): JSONArray? {
        node.optJSONArray("trackList")?.takeIf { it.length() > 0 }?.let { return it }
        val keys = node.keys()
        while (keys.hasNext()) {
            when (val child = node.opt(keys.next())) {
                is JSONObject -> findTrackList(child)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until child.length()) {
                        child.optJSONObject(i)?.let { findTrackList(it) }?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun resolveYoutube(original: String): ResolvedBatch {
        val canonical = LinkParser.canonicalizeYoutube(original)
        val watch = recoverYoutubeWatchUrl(original, canonical)
        return try {
            resolveYoutubeNewPipe(original, watch)
        } catch (first: Exception) {
            try {
                resolveYoutubeOembed(original, watch)
            } catch (second: Exception) {
                throw DownloadException(
                    "Could not read this YouTube Music link. Paste the track share URL again, or use youtube.com/watch.",
                    first,
                )
            }
        }
    }

    private fun resolveYoutubeNewPipe(original: String, watch: String): ResolvedBatch {
        if (LinkParser.looksLikePlaylist(watch) || LinkParser.looksLikePlaylist(original)) {
            val playlist = PlaylistInfo.getInfo(ServiceList.YouTube, watch)
            val items = playlist.relatedItems.mapNotNull { item ->
                val stream = item as? StreamInfoItem ?: return@mapNotNull null
                ResolvedItem(
                    title = stream.name ?: "YouTube",
                    artist = stream.uploaderName ?: playlist.uploaderName ?: "YouTube",
                    sourceUrl = stream.url,
                    sourceKind = LinkParser.sourceKind(original),
                    ytdlpQuery = LinkParser.canonicalizeYoutube(stream.url),
                    coverUrl = stream.thumbnails.maxByOrNull { it.height }?.url,
                )
            }
            if (items.isEmpty()) error("Empty playlist")
            return ResolvedBatch(
                playlist = PlaylistMeta(
                    title = playlist.name ?: "Playlist",
                    artist = playlist.uploaderName ?: "YouTube",
                    sourceUrl = watch,
                    sourceKind = LinkParser.sourceKind(original),
                    coverUrl = playlist.thumbnails.maxByOrNull { it.height }?.url,
                ),
                items = items,
            )
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, watch)
        return ResolvedBatch(
            playlist = null,
            items = listOf(
                ResolvedItem(
                    title = info.name ?: "YouTube",
                    artist = info.uploaderName ?: "YouTube",
                    sourceUrl = watch,
                    sourceKind = LinkParser.sourceKind(original),
                    ytdlpQuery = watch,
                    coverUrl = info.thumbnails.maxByOrNull { it.height }?.url,
                ),
            ),
        )
    }

    private fun resolveYoutubeOembed(original: String, watch: String): ResolvedBatch {
        val id = LinkParser.youtubeVideoId(watch)
            ?: LinkParser.youtubeVideoId(original)
            ?: error("missing video id")
        val url = "https://www.youtube.com/watch?v=$id"
        val encoded = java.net.URLEncoder.encode(url, Charsets.UTF_8)
        val request = Request.Builder()
            .url("https://www.youtube.com/oembed?url=$encoded&format=json")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("oEmbed HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val title = json.optString("title").ifBlank { "YouTube" }
            val artist = json.optString("author_name").ifBlank { "YouTube" }
            val cover = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
            return ResolvedBatch(
                playlist = null,
                items = listOf(
                    ResolvedItem(
                        title = title,
                        artist = artist,
                        sourceUrl = url,
                        sourceKind = LinkParser.sourceKind(original),
                        ytdlpQuery = url,
                        coverUrl = cover,
                    ),
                ),
            )
        }
    }

    private fun recoverYoutubeWatchUrl(original: String, canonical: String): String {
        LinkParser.youtubeVideoId(canonical)?.let { return "https://www.youtube.com/watch?v=$it" }
        LinkParser.youtubeVideoId(original)?.let { return "https://www.youtube.com/watch?v=$it" }
        val probe = urlFinder.find(original)?.value?.trimEnd('.', ',', ';') ?: original.trim()
        if (!probe.startsWith("http://") && !probe.startsWith("https://")) return canonical
        return runCatching {
            val request = Request.Builder().url(probe).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                LinkParser.youtubeVideoId(finalUrl)?.let { return@use "https://www.youtube.com/watch?v=$it" }
                val body = response.body?.string().orEmpty()
                val fromHtml = htmlVideoId.find(body)?.groupValues?.get(1)
                    ?: htmlWatchId.find(body)?.groupValues?.get(1)
                if (fromHtml != null) return@use "https://www.youtube.com/watch?v=$fromHtml"
                val og = Jsoup.parse(body).selectFirst("meta[property=og:url]")?.attr("content").orEmpty()
                LinkParser.youtubeVideoId(og)?.let { return@use "https://www.youtube.com/watch?v=$it" }
                canonical
            }
        }.getOrDefault(canonical)
    }

    private fun searchYoutube(query: String): String {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, emptyList(), "")
        extractor.fetchPage()
        val first = extractor.initialPage.items.filterIsInstance<StreamInfoItem>().firstOrNull()
            ?: extractor.initialPage.items.firstOrNull()
            ?: error("No YouTube result for $query")
        return first.url
    }

    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        val progressive = streams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val pool = progressive.ifEmpty { streams.filter { it.isUrl } }
        return pool.maxByOrNull { stream ->
            val bitrate = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate
            YoutubeAudioPick.score(
                url = stream.content,
                itag = stream.itag,
                bitrate = bitrate,
                codec = stream.codec,
                formatName = stream.format?.name,
                progressive = stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP,
            )
        }
    }

    private fun downloadUrl(
        url: String,
        dest: File,
        referer: String,
        onProgress: (Float, String) -> Unit,
    ) {
        var response = mediaHttp.newCall(mediaRequest(url, referer, withRange = true)).execute()
        if (response.code == 403 || response.code == 416) {
            response.close()
            response = mediaHttp.newCall(mediaRequest(url, referer, withRange = false)).execute()
        }
        response.use { bodyResponse ->
            if (!bodyResponse.isSuccessful) error("HTTP ${bodyResponse.code}")
            val body = bodyResponse.body ?: error("Empty body")
            val total = body.contentLength()
            val started = System.nanoTime()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        copied += n
                        val now = System.nanoTime()
                        if (now - lastEmit < 150_000_000L && n > 0) continue
                        lastEmit = now
                        val elapsed = (now - started) / 1_000_000_000.0
                        val bps = if (elapsed > 0.25) copied / elapsed else 0.0
                        onProgress(downloadPct(copied, total), downloadLabel(copied, total, bps))
                    }
                    val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
                    val bps = if (elapsed > 0.05) copied / elapsed else 0.0
                    onProgress(
                        if (total > 0) 0.9f else 0.88f,
                        downloadLabel(copied, copied.coerceAtLeast(total), bps),
                    )
                }
            }
        }
    }

    private fun mediaRequest(url: String, referer: String, withRange: Boolean): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Referer", referer)
            .header("Origin", "https://www.youtube.com")
        if (withRange) builder.header("Range", "bytes=0-")
        return builder.build()
    }

    private fun downloadPct(copied: Long, total: Long): Float {
        return if (total > 0) {
            (0.1f + 0.8f * (copied.toFloat() / total)).coerceIn(0.1f, 0.9f)
        } else {
            (0.12f + 0.6f * (1f - 1f / (1f + copied / 3_000_000f))).coerceAtMost(0.85f)
        }
    }

    private fun downloadLabel(copied: Long, total: Long, bytesPerSec: Double): String {
        val core = if (total > 0) {
            "Downloading ${formatMb(copied)} / ${formatMb(total)} MB"
        } else {
            "Downloading ${formatMb(copied)} MB"
        }
        val speed = YoutubeAudioPick.formatSpeed(bytesPerSec)
        return if (speed.isEmpty()) core else "$core · $speed"
    }

    private fun formatMb(bytes: Long): String {
        val mb = bytes / 1_000_000.0
        return if (mb < 10) "%.1f".format(mb) else "%.0f".format(mb)
    }

    private fun spotifyTrackMeta(url: String): Triple<String, String, String?> {
        return try {
            spotifyOembed(url)
        } catch (_: Exception) {
            scrapeSpotify(url)
        }.let { meta ->
            if (SpotifyQuery.isPlaceholderArtist(meta.second)) {
                try {
                    val scraped = scrapeSpotify(url)
                    Triple(
                        if (meta.first.isBlank() || meta.first == "Unknown title") scraped.first else meta.first,
                        if (SpotifyQuery.isPlaceholderArtist(meta.second)) scraped.second else meta.second,
                        meta.third ?: scraped.third,
                    )
                } catch (_: Exception) {
                    meta
                }
            } else meta
        }
    }

    private fun spotifyOembed(url: String): Triple<String, String, String?> {
        val encoded = java.net.URLEncoder.encode(url, Charsets.UTF_8)
        val request = Request.Builder()
            .url("https://open.spotify.com/oembed?url=$encoded")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("oEmbed HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            return Triple(
                json.optString("title", "Unknown title"),
                json.optString("author_name", "Unknown artist"),
                json.optString("thumbnail_url").takeIf { it.isNotBlank() },
            )
        }
    }

    private fun scrapeSpotify(url: String): Triple<String, String, String?> {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.title().ifBlank { "Unknown title" }
            val artist = doc.selectFirst("meta[property=og:description]")?.attr("content")
                ?.split("·")?.getOrNull(1)?.trim()
                ?: "Unknown artist"
            val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")
            return Triple(title, artist, cover)
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private const val MAX_SPOTIFY_COLLECTION = 50
        private val initialized = AtomicBoolean(false)
        private val urlFinder = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private val htmlVideoId = Regex(""""videoId"\s*:\s*"([A-Za-z0-9_-]{11})"""")
        private val htmlWatchId = Regex("""watch\?v=([A-Za-z0-9_-]{11})""")

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .cookieJar(YoutubeCookieJar())
            .build()

        fun ensureNewPipe(http: OkHttpClient) {
            if (initialized.compareAndSet(false, true)) {
                YoutubeParsingHelper.setConsentAccepted(true)
                YoutubeStreamExtractor.setFetchIosClient(true)
                NewPipe.init(
                    OkHttpDownloader(http),
                    Localization.DEFAULT,
                    ContentCountry.DEFAULT,
                )
            }
        }
    }
}

private class YoutubeCookieJar : CookieJar {
    private val lock = Any()
    private val stored = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            cookies.forEach { cookie ->
                stored.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                stored.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val host = url.host
            if (host.endsWith("youtube.com") || host.endsWith("youtu.be") || host.endsWith("youtube-nocookie.com")) {
                val hasSocs = stored.any { it.name == "SOCS" && it.matches(url) }
                if (!hasSocs) {
                    stored.add(
                        Cookie.Builder().domain("youtube.com").name("SOCS").value("CAI").build(),
                    )
                }
            }
            return stored.filter { it.matches(url) }
        }
    }
}

private class OkHttpDownloader(private val http: OkHttpClient) : Downloader() {
    override fun execute(request: NpRequest): Response {
        val builder = Request.Builder().url(request.url())
        var hasUa = false
        request.headers().forEach { (key, values) ->
            if (key.equals("User-Agent", ignoreCase = true)) hasUa = true
            values.forEach { builder.addHeader(key, it) }
        }
        if (!hasUa) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
            )
        }
        val body = request.dataToSend()?.toRequestBody(null)
        builder.method(request.httpMethod(), body)
        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("Got 429", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }
}
