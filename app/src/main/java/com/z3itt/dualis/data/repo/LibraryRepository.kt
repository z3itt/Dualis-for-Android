package com.z3itt.dualis.data.repo

import android.content.Context
import androidx.room.Room
import com.z3itt.dualis.data.db.DualisDatabase
import com.z3itt.dualis.data.db.PlaylistEntity
import com.z3itt.dualis.data.db.SettingEntity
import com.z3itt.dualis.data.db.toDomain
import com.z3itt.dualis.data.db.toEntity
import com.z3itt.dualis.domain.model.LibrarySnapshot
import com.z3itt.dualis.domain.model.Playlist
import com.z3itt.dualis.domain.model.Track
import com.z3itt.dualis.ml.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File

class LibraryRepository(context: Context) {
    private val appContext = context.applicationContext
    val db: DualisDatabase = Room.databaseBuilder(
        appContext,
        DualisDatabase::class.java,
        "dualis.db",
    ).build()

    private val dao = db.dao()

    fun tracksDir(): File = File(appContext.filesDir, "tracks").apply { mkdirs() }
    fun modelsDir(): File = File(appContext.filesDir, "models").apply { mkdirs() }
    fun trackDir(id: String): File = File(tracksDir(), id).apply { mkdirs() }

    fun observeSnapshot(): Flow<LibrarySnapshot> =
        combine(dao.observeTracks(), dao.observePlaylists()) { tracks, playlists ->
            val domainTracks = tracks.map { it.toDomain() }
            LibrarySnapshot(
                tracks = domainTracks,
                playlists = playlists.map { entity ->
                    val members = domainTracks.filter { it.playlistId == entity.id }
                    entity.toDomain(
                        trackCount = members.size.toLong(),
                        readyCount = members.count { it.status.raw() == "ready" }.toLong(),
                    )
                },
            )
        }

    suspend fun snapshot(): LibrarySnapshot {
        val tracks = dao.listTracks().map { it.toDomain() }
        val playlists = dao.listPlaylists().map { entity ->
            val members = tracks.filter { it.playlistId == entity.id }
            entity.toDomain(members.size.toLong(), members.count { it.status.raw() == "ready" }.toLong())
        }
        return LibrarySnapshot(tracks, playlists)
    }

    suspend fun getTrack(id: String): Track? = dao.getTrack(id)?.toDomain()
    suspend fun upsert(track: Track) = dao.upsertTrack(track.toEntity())
    suspend fun deleteTrack(id: String) {
        val dir = trackDir(id)
        dao.deleteTrack(id)
        dir.deleteRecursively()
    }

    suspend fun deleteTracks(ids: List<String>) {
        ids.forEach { deleteTrack(it) }
    }

    suspend fun upsertPlaylist(playlist: Playlist) {
        dao.upsertPlaylist(
            PlaylistEntity(
                id = playlist.id,
                title = playlist.title,
                artist = playlist.artist,
                sourceUrl = playlist.sourceUrl,
                sourceKind = playlist.sourceKind,
                coverPath = playlist.coverPath,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
            ),
        )
    }

    suspend fun deletePlaylist(id: String) {
        val members = dao.listTracks().filter { it.playlistId == id }
        members.forEach { deleteTrack(it.id) }
        dao.deletePlaylist(id)
    }

    suspend fun getSetting(key: String, default: String): String = dao.getSetting(key) ?: default
    suspend fun setSetting(key: String, value: String) = dao.upsertSetting(SettingEntity(key, value))

    suspend fun selectedModelId(): String = getSetting(SETTING_MODEL, ModelCatalog.DEFAULT_MODEL_ID)
    suspend fun downloadFormat(): String = getSetting(SETTING_FORMAT, "auto")

    suspend fun pendingTracks(): List<Track> = dao.pendingTracks().map { it.toDomain() }

    companion object {
        const val SETTING_MODEL = "model_id"
        const val SETTING_FORMAT = "download_format"
        const val SETTING_THEME = "theme"
    }
}
