package com.z3itt.dualis.domain.library

import com.z3itt.dualis.domain.model.JobEvent
import com.z3itt.dualis.domain.model.LibrarySort
import com.z3itt.dualis.domain.model.Track
import com.z3itt.dualis.domain.model.TrackStatus

object LibraryRules {
    val STAGES = listOf("download", "decode", "infer", "export")

    fun stageIndex(stage: String): Int {
        val normalized = if (stage == "separate") "infer" else stage
        val index = STAGES.indexOf(normalized)
        return if (index < 0) 0 else index
    }

    fun sortTracks(tracks: List<Track>, sort: LibrarySort): List<Track> =
        when (sort) {
            LibrarySort.TITLE -> tracks.sortedBy { it.title.lowercase() }
            LibrarySort.ARTIST -> tracks.sortedBy { it.artist.lowercase() }
            LibrarySort.DURATION -> tracks.sortedByDescending { it.durationMs ?: 0L }
            LibrarySort.STATUS -> tracks.sortedBy { it.status.raw() }
            LibrarySort.RECENT -> tracks.sortedByDescending { it.createdAt }
        }

    fun filterTracks(tracks: List<Track>, query: String): List<Track> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return tracks
        return tracks.filter { "${it.title} ${it.artist}".lowercase().contains(needle) }
    }

    fun standaloneTracks(tracks: List<Track>): List<Track> =
        tracks.filter { it.playlistId.isNullOrBlank() }

    fun tracksInPlaylist(tracks: List<Track>, playlistId: String): List<Track> =
        tracks.filter { it.playlistId == playlistId }.sortedBy { it.playlistIndex ?: 0L }

    fun parseEtaSeconds(event: JobEvent): Float? {
        if (event.etaSeconds != null && event.etaSeconds.isFinite()) return event.etaSeconds
        val match = Regex("ETA\\s+(\\d+(?:\\.\\d+)?)s", RegexOption.IGNORE_CASE).find(event.message)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    fun formatTime(seconds: Float): String {
        if (!seconds.isFinite() || seconds < 0) return "0:00"
        val m = seconds.toInt() / 60
        val s = seconds.toInt() % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    fun formatEta(seconds: Float?): String {
        if (seconds == null || !seconds.isFinite() || seconds <= 0) return ""
        if (seconds < 60) return "${seconds.toInt()}s"
        val minutes = (seconds / 60).toInt()
        val rest = (seconds % 60).toInt()
        return if (rest > 0) "${minutes}m ${rest}s" else "${minutes}m"
    }

    fun isLive(status: TrackStatus) =
        status == TrackStatus.DOWNLOADING ||
            status == TrackStatus.DOWNLOADED ||
            status == TrackStatus.SEPARATING
}
