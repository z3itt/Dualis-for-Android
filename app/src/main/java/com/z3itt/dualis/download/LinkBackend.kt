package com.z3itt.dualis.download

import com.z3itt.dualis.domain.ingest.LinkParser
import com.z3itt.dualis.domain.ingest.SpotifyQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 2 link ingest. YouTube uses NewPipe Extractor (Java, FOSS).
 * Spotify uses oEmbed + page scrape, then YouTube search via title + artist.
 * Stored `ytdlpQuery` stays in `ytsearch1:` form so retries match desktop.
 */
class LinkBackend(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) : DownloadBackend {
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
            query
        }
        onProgress(0.05f, "Resolving audio stream")
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val audio = pickAudio(info.audioStreams) ?: error("No audio stream for $url")
        onProgress(0.1f, "Downloading ${info.name}")
        downloadUrl(audio.content, destFile, onProgress)
        destFile
    }

    private fun resolveSpotify(url: String): ResolvedBatch {
        if (LinkParser.looksLikePlaylist(url)) {
            throw DownloadException("Playlist ingest queues one track at a time. Open the playlist in Spotify and paste track links, or wait for playlist expansion in a later build.")
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

    private fun resolveYoutube(input: String): ResolvedBatch {
        if (LinkParser.looksLikePlaylist(input)) {
            val playlist = PlaylistInfo.getInfo(ServiceList.YouTube, input)
            val items = playlist.relatedItems.mapNotNull { item ->
                val stream = item as? StreamInfoItem ?: return@mapNotNull null
                ResolvedItem(
                    title = stream.name ?: "YouTube",
                    artist = stream.uploaderName ?: playlist.uploaderName ?: "YouTube",
                    sourceUrl = stream.url,
                    sourceKind = LinkParser.sourceKind(input),
                    ytdlpQuery = stream.url,
                    coverUrl = stream.thumbnails.maxByOrNull { it.height }?.url,
                )
            }
            if (items.isEmpty()) error("Empty playlist")
            return ResolvedBatch(
                playlist = PlaylistMeta(
                    title = playlist.name ?: "Playlist",
                    artist = playlist.uploaderName ?: "YouTube",
                    sourceUrl = input,
                    sourceKind = LinkParser.sourceKind(input),
                    coverUrl = playlist.thumbnails.maxByOrNull { it.height }?.url,
                ),
                items = items,
            )
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, input)
        return ResolvedBatch(
            playlist = null,
            items = listOf(
                ResolvedItem(
                    title = info.name ?: "YouTube",
                    artist = info.uploaderName ?: "YouTube",
                    sourceUrl = input,
                    sourceKind = LinkParser.sourceKind(input),
                    ytdlpQuery = input,
                    coverUrl = info.thumbnails.maxByOrNull { it.height }?.url,
                ),
            ),
        )
    }

    private fun searchYoutube(query: String): String {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, emptyList(), "")
        extractor.fetchPage()
        val first = extractor.initialPage.items.firstOrNull()
            ?: error("No YouTube result for $query")
        return first.url
    }

    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        return streams
            .sortedWith(compareByDescending<AudioStream> { it.bitrate }.thenBy { it.format?.suffix ?: "" })
            .firstOrNull()
    }

    private fun downloadUrl(url: String, dest: File, onProgress: (Float, String) -> Unit) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        copied += n
                        if (total > 0) {
                            val pct = 0.1f + 0.8f * (copied.toFloat() / total)
                            onProgress(pct, "Downloading ${(copied / 1_000_000)} MB")
                        }
                    }
                }
            }
        }
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
        private const val USER_AGENT = "Mozilla/5.0 Dualis/1.0.0"
        private val initialized = AtomicBoolean(false)

        fun ensureNewPipe(http: OkHttpClient) {
            if (initialized.compareAndSet(false, true)) {
                NewPipe.init(OkHttpDownloader(http), Localization.DEFAULT)
            }
        }
    }
}

private class OkHttpDownloader(private val http: OkHttpClient) : Downloader() {
    override fun execute(request: NpRequest): Response {
        val builder = Request.Builder().url(request.url())
        request.headers().forEach { (key, values) ->
            values.forEach { builder.addHeader(key, it) }
        }
        val body = request.dataToSend()?.let {
            okhttp3.RequestBody.create(null, it)
        }
        builder.method(request.httpMethod(), body)
        http.newCall(builder.build()).execute().use { response ->
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
