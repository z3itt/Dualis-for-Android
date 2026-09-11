package com.z3itt.dualis.domain.model

enum class StemMode { ORIGINAL, VOCALS, INSTRUMENTAL }

enum class LoopMode { OFF, QUEUE, SONG }

enum class TrackStatus {
    QUEUED, DOWNLOADING, DOWNLOADED, SEPARATING, READY, ERROR;

    companion object {
        fun fromRaw(raw: String): TrackStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ERROR
    }

    fun raw(): String = name.lowercase()
}

enum class LibrarySort { RECENT, TITLE, ARTIST, DURATION, STATUS }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val sourceUrl: String? = null,
    val sourceKind: String,
    val sourcePath: String? = null,
    val vocalsPath: String? = null,
    val instrumentalPath: String? = null,
    val coverPath: String? = null,
    val durationMs: Long? = null,
    val sampleRate: Long? = null,
    val status: TrackStatus,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val peaks: List<Float>? = null,
    val playlistId: String? = null,
    val playlistIndex: Long? = null,
    val ytdlpQuery: String? = null,
)

data class Playlist(
    val id: String,
    val title: String,
    val artist: String,
    val sourceUrl: String? = null,
    val sourceKind: String,
    val coverPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Long = 0,
    val readyCount: Long = 0,
)

data class LibrarySnapshot(
    val tracks: List<Track>,
    val playlists: List<Playlist>,
)

data class JobEvent(
    val trackId: String,
    val stage: String,
    val progress: Float,
    val message: String,
    val status: String,
    val etaSeconds: Float? = null,
    val failedTrack: Track? = null,
)

data class ModelInfo(
    val id: String,
    val name: String,
    val architecture: String,
    val ready: Boolean,
    val description: String,
)

data class RuntimeInfo(
    val modelPath: String? = null,
    val modelReady: Boolean = false,
    val selectedModel: String = "kim-vocal-2",
    val executionProvider: String = "CPU",
    val compiledProviders: List<String> = listOf("QNN", "NNAPI", "CPU"),
    val models: List<ModelInfo> = emptyList(),
    val downloadFormat: String = "auto",
    val firstRunHint: Boolean = false,
)
