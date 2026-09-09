package com.z3itt.dualis.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.z3itt.dualis.domain.model.Playlist
import com.z3itt.dualis.domain.model.Track
import com.z3itt.dualis.domain.model.TrackStatus

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val sourceUrl: String?,
    val sourceKind: String,
    val sourcePath: String?,
    val vocalsPath: String?,
    val instrumentalPath: String?,
    val coverPath: String?,
    val durationMs: Long?,
    val sampleRate: Long?,
    val status: String,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val playlistId: String?,
    val playlistIndex: Long?,
    val ytdlpQuery: String?,
    val peaksJson: String?,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val sourceUrl: String?,
    val sourceKind: String,
    val coverPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    sourceUrl = sourceUrl,
    sourceKind = sourceKind,
    sourcePath = sourcePath,
    vocalsPath = vocalsPath,
    instrumentalPath = instrumentalPath,
    coverPath = coverPath,
    durationMs = durationMs,
    sampleRate = sampleRate,
    status = TrackStatus.fromRaw(status),
    error = error,
    createdAt = createdAt,
    updatedAt = updatedAt,
    peaks = peaksJson?.let { json ->
        json.trim().removePrefix("[").removeSuffix("]")
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .takeIf { it.isNotEmpty() }
    },
    playlistId = playlistId,
    playlistIndex = playlistIndex,
    ytdlpQuery = ytdlpQuery,
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    sourceUrl = sourceUrl,
    sourceKind = sourceKind,
    sourcePath = sourcePath,
    vocalsPath = vocalsPath,
    instrumentalPath = instrumentalPath,
    coverPath = coverPath,
    durationMs = durationMs,
    sampleRate = sampleRate,
    status = status.raw(),
    error = error,
    createdAt = createdAt,
    updatedAt = updatedAt,
    playlistId = playlistId,
    playlistIndex = playlistIndex,
    ytdlpQuery = ytdlpQuery,
    peaksJson = peaks?.joinToString(prefix = "[", postfix = "]"),
)

fun PlaylistEntity.toDomain(trackCount: Long, readyCount: Long): Playlist = Playlist(
    id = id,
    title = title,
    artist = artist,
    sourceUrl = sourceUrl,
    sourceKind = sourceKind,
    coverPath = coverPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    trackCount = trackCount,
    readyCount = readyCount,
)
