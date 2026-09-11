package com.z3itt.dualis.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.z3itt.dualis.DualisApplication
import com.z3itt.dualis.audio.StemExport
import com.z3itt.dualis.data.repo.LibraryRepository
import com.z3itt.dualis.domain.ingest.LinkParser
import com.z3itt.dualis.domain.ingest.SpotifyQuery
import com.z3itt.dualis.domain.library.LibraryRules
import com.z3itt.dualis.domain.model.JobEvent
import com.z3itt.dualis.domain.model.LibrarySort
import com.z3itt.dualis.domain.model.LoopMode
import com.z3itt.dualis.domain.model.Playlist
import com.z3itt.dualis.domain.model.RuntimeInfo
import com.z3itt.dualis.domain.model.StemMode
import com.z3itt.dualis.domain.model.Track
import com.z3itt.dualis.domain.model.TrackStatus
import com.z3itt.dualis.domain.playback.PlaybackRules
import com.z3itt.dualis.domain.queue.WorkItem
import com.z3itt.dualis.ml.ModelCatalog
import com.z3itt.dualis.service.JobForegroundService
import com.z3itt.dualis.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class UiState(
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val jobs: Map<String, JobEvent> = emptyMap(),
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val selectedIds: Set<String> = emptySet(),
    val openPlaylistId: String? = null,
    val ingestText: String = "",
    val ingestBusy: Boolean = false,
    val ingestError: String? = null,
    val settingsOpen: Boolean = false,
    val aboutOpen: Boolean = false,
    val queueOpen: Boolean = false,
    val hiddenErrors: Set<String> = emptySet(),
    val themeDark: Boolean = false,
    val currentId: String? = null,
    val playing: Boolean = false,
    val currentTime: Float = 0f,
    val duration: Float = 0f,
    val volume: Float = 0.9f,
    val stemMode: StemMode = StemMode.ORIGINAL,
    val shuffle: Boolean = false,
    val loopMode: LoopMode = LoopMode.OFF,
    val playQueue: List<String> = emptyList(),
    val pendingJobIds: List<String> = emptyList(),
    val failedTracks: Map<String, Track> = emptyMap(),
    val runtime: RuntimeInfo = RuntimeInfo(),
    val toast: String? = null,
    val mainTab: MainTab = MainTab.LIBRARY,
)

enum class MainTab { LIBRARY, JOBS }

class DualisViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DualisApplication
    private val repo get() = app.container.repo
    private val queue get() = app.container.workQueue
    private val player get() = app.container.player
    private val playback get() = app.container.playback

    private val jobs = MutableStateFlow<Map<String, JobEvent>>(emptyMap())
    private val ui = MutableStateFlow(UiState(themeDark = app.isThemeDark()))

    val state: StateFlow<UiState> = combine(
        repo.observeSnapshot(),
        jobs,
        ui,
        queue.pendingIds,
    ) { snap, jobMap, extra, pending ->
        extra.copy(
            tracks = snap.tracks,
            playlists = snap.playlists,
            jobs = jobMap,
            pendingJobIds = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private var shuffleOrder = emptyList<String>()
    private var shufflePlayed = emptyList<String>()
    private var shuffleContext = ""
    private var playHistory = emptyList<String>()
    private var lockedContext = ""

    init {
        viewModelScope.launch {
            val theme = repo.getSetting(LibraryRepository.SETTING_THEME, "light")
            val dark = theme == "dark"
            ui.update { it.copy(themeDark = dark) }
            app.persistThemeDark(dark)
            refreshRuntime()
        }
        viewModelScope.launch(Dispatchers.IO) {
            backfillMissingCovers()
        }
        viewModelScope.launch {
            JobForegroundService.bus().collect { event ->
                jobs.update { it + (event.trackId to event) }
                if (event.status == "error") {
                    val failed = event.failedTrack
                        ?: state.value.tracks.find { it.id == event.trackId }
                            ?.copy(status = TrackStatus.ERROR, error = event.message)
                    ui.update { extra ->
                        extra.copy(
                            toast = event.message,
                            failedTracks = if (failed != null) extra.failedTracks + (failed.id to failed) else extra.failedTracks,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(200)
                val current = ui.value.currentId
                if (current != null) {
                    ui.update {
                        it.copy(
                            playing = player.playing.value,
                            currentTime = player.currentPosition() / 1000f,
                            duration = player.durationMs / 1000f,
                        )
                    }
                }
            }
        }
        playback.onSkip = { delta, fromEnded -> skip(delta, fromEnded) }
        playback.onShuffleChanged = { on ->
            shuffleOrder = emptyList()
            shufflePlayed = emptyList()
            ui.update { it.copy(shuffle = on) }
        }
        playback.onLoopChanged = { mode ->
            player.setLoopMode(mode)
            ui.update { it.copy(loopMode = mode) }
        }
        playback.onStemChanged = { mode ->
            player.setStemMode(mode)
            ui.update { it.copy(stemMode = mode) }
        }
        player.onEnded = { skip(1, fromEnded = true) }
        restorePlaybackUi()
    }

    private fun rememberTrack(id: String?): String? {
        playback.rememberTrack(id)
        return id
    }

    private fun rememberQueue(ids: List<String>): List<String> {
        playback.rememberQueue(ids)
        return ids
    }

    private fun restorePlaybackUi() {
        player.setStemMode(playback.stemMode)
        player.setLoopMode(playback.loopMode)
        val mediaId = playback.currentTrackId ?: player.currentMediaId()
        if (playback.currentTrackId == null && mediaId != null) {
            playback.rememberTrack(mediaId)
        }
        val live = player.hasMedia() && mediaId != null
        val durationMs = player.durationMs.takeIf { it > 0 } ?: playback.durationMs
        ui.update {
            it.copy(
                currentId = mediaId,
                playing = live && player.playing.value,
                currentTime = if (live) player.currentPosition() / 1000f else 0f,
                duration = if (durationMs > 0) durationMs / 1000f else 0f,
                volume = player.volume,
                stemMode = playback.stemMode,
                shuffle = playback.shuffle,
                loopMode = playback.loopMode,
                playQueue = playback.playQueue,
            )
        }
    }

    fun setIngestText(value: String) = ui.update { it.copy(ingestText = value, ingestError = null) }
    fun setQuery(value: String) = ui.update { it.copy(query = value) }
    fun setMainTab(tab: MainTab) = ui.update { it.copy(mainTab = tab) }
    fun setSort(sort: LibrarySort) = ui.update { it.copy(sort = sort) }
    fun openSettings(open: Boolean) = ui.update {
        it.copy(settingsOpen = open, aboutOpen = if (open) false else it.aboutOpen)
    }
    fun openAbout(open: Boolean) = ui.update { it.copy(aboutOpen = open, settingsOpen = if (open) false else it.settingsOpen) }
    fun setQueueOpen(open: Boolean) = ui.update { it.copy(queueOpen = open) }
    fun dismissToast() = ui.update { it.copy(toast = null) }
    fun dismissError(id: String) {
        ui.update { it.copy(hiddenErrors = it.hiddenErrors + id, failedTracks = it.failedTracks - id) }
        viewModelScope.launch {
            queue.cancel(id)
            repo.deleteTrack(id)
        }
    }

    fun setThemeDark(dark: Boolean) {
        ui.update { it.copy(themeDark = dark) }
        app.persistThemeDark(dark)
        viewModelScope.launch { repo.setSetting(LibraryRepository.SETTING_THEME, if (dark) "dark" else "light") }
    }

    fun setModel(id: String) {
        viewModelScope.launch {
            repo.setSetting(LibraryRepository.SETTING_MODEL, id)
            refreshRuntime()
        }
    }

    fun setDownloadFormat(id: String) {
        viewModelScope.launch {
            repo.setSetting(LibraryRepository.SETTING_FORMAT, id)
            refreshRuntime()
        }
    }

    fun ingestLinks() {
        val inputs = LinkParser.splitInputs(ui.value.ingestText)
        if (inputs.isEmpty()) {
            ui.update { it.copy(ingestError = "Paste a Spotify, YouTube Music, or YouTube link.") }
            return
        }
        viewModelScope.launch {
            ui.update { it.copy(ingestBusy = true, ingestError = null) }
            try {
                for (input in inputs) {
                    ingestOneLink(input)
                }
                ui.update { it.copy(ingestText = "", ingestBusy = false, mainTab = MainTab.JOBS) }
            } catch (err: Exception) {
                ui.update { it.copy(ingestBusy = false, ingestError = err.message) }
            }
        }
    }

    fun ingestSharedText(text: String) {
        ui.update { it.copy(ingestText = text) }
        ingestLinks()
    }

    fun ingestLocalUris(uris: List<Uri>) {
        viewModelScope.launch {
            ui.update { it.copy(ingestBusy = true, ingestError = null) }
            try {
                for (uri in uris) ingestLocal(uri)
                ui.update { it.copy(ingestBusy = false, mainTab = MainTab.JOBS) }
            } catch (err: Exception) {
                ui.update { it.copy(ingestBusy = false, ingestError = err.message) }
            }
        }
    }

    fun retry(track: Track) {
        viewModelScope.launch {
            val query = SpotifyQuery.resolveDownloadQuery(
                track.sourceKind,
                track.title,
                track.artist,
                track.sourceUrl,
                track.ytdlpQuery,
            )
            repo.upsert(track.copy(status = TrackStatus.QUEUED, error = null, updatedAt = now()))
            queue.enqueue(WorkItem(track.id, query), front = true)
            ui.update { it.copy(hiddenErrors = it.hiddenErrors - track.id, failedTracks = it.failedTracks - track.id) }
            JobForegroundService.start(getApplication())
        }
    }

    fun prioritize(track: Track) = retry(track)

    fun deleteTrack(id: String) {
        viewModelScope.launch {
            if (ui.value.currentId == id) player.clear()
            queue.cancel(id)
            repo.deleteTrack(id)
            ui.update {
                it.copy(
                    selectedIds = it.selectedIds - id,
                    playQueue = rememberQueue(it.playQueue - id),
                    failedTracks = it.failedTracks - id,
                    currentId = rememberTrack(if (it.currentId == id) null else it.currentId),
                )
            }
        }
    }

    fun deleteSelected() {
        val ids = ui.value.selectedIds.toList()
        viewModelScope.launch {
            if (ui.value.currentId in ids) player.clear()
            ids.forEach { queue.cancel(it) }
            repo.deleteTracks(ids)
        }
        ui.update {
            it.copy(
                selectedIds = emptySet(),
                playQueue = rememberQueue(it.playQueue - ids.toSet()),
                currentId = rememberTrack(if (it.currentId in ids) null else it.currentId),
            )
        }
    }

    fun toggleSelected(id: String) {
        ui.update {
            it.copy(
                selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id,
            )
        }
    }

    fun openPlaylist(id: String?) = ui.update { it.copy(openPlaylistId = id, selectedIds = emptySet()) }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            state.value.tracks.filter { it.playlistId == id }.forEach { queue.cancel(it.id) }
            repo.deletePlaylist(id)
        }
        ui.update { it.copy(openPlaylistId = if (it.openPlaylistId == id) null else it.openPlaylistId) }
    }

    fun addToQueue(id: String) = ui.update { it.copy(playQueue = rememberQueue((it.playQueue + id).distinct())) }
    fun removeFromQueue(id: String) = ui.update { it.copy(playQueue = rememberQueue(it.playQueue - id)) }
    fun clearQueue() = ui.update { it.copy(playQueue = rememberQueue(emptyList())) }

    fun playTrack(track: Track) {
        if (track.status != TrackStatus.READY || track.vocalsPath == null || track.instrumentalPath == null) {
            prioritize(track)
            ui.update { it.copy(toast = "${track.title} is next in the queue. Playback starts when stems are ready.") }
            return
        }
        val vocalsFile = File(track.vocalsPath)
        val instFile = File(track.instrumentalPath)
        if (!vocalsFile.isFile || !instFile.isFile) {
            ui.update { it.copy(toast = "Stem files are missing. Separate this track again.") }
            return
        }
        player.load(
            vocalsFile,
            instFile,
            track.title,
            track.artist,
            track.coverPath,
            track.id,
            track.durationMs,
        )
        playback.setDuration(track.durationMs)
        player.setStemMode(playback.stemMode)
        player.setVolume(ui.value.volume)
        player.setLoopMode(playback.loopMode)
        player.play()
        ensurePlaybackService()
        playback.notifyTrackChanged()
        ui.update {
            it.copy(
                currentId = rememberTrack(track.id),
                playing = true,
                duration = (track.durationMs ?: 0) / 1000f,
            )
        }
        playHistory = (listOf(track.id) + playHistory).distinct().take(50)
    }

    fun togglePlay() {
        val current = state.value.tracks.find { it.id == ui.value.currentId }
        if (current == null) {
            val first = state.value.tracks.firstOrNull { it.status == TrackStatus.READY } ?: return
            playTrack(first)
            return
        }
        player.toggle()
        if (player.playing.value) ensurePlaybackService()
    }

    private fun ensurePlaybackService() {
        PlaybackService.start(getApplication())
    }

    fun seek(seconds: Float) = player.seek((seconds * 1000).toLong())

    fun setVolume(volume: Float) {
        player.setVolume(volume)
        ui.update { it.copy(volume = volume) }
    }

    fun setStemMode(mode: StemMode) = playback.setStemMode(mode)

    fun setShuffle(on: Boolean) = playback.setShuffle(on)

    fun cycleLoopMode() = playback.cycleLoop()

    fun skip(delta: Int, fromEnded: Boolean = false) {
        val snap = state.value
        val ready = snap.tracks.filter { it.status == TrackStatus.READY }
        val current = ready.find { it.id == snap.currentId }
        val playlistIds = current?.playlistId?.let { pid ->
            LibraryRules.tracksInPlaylist(ready, pid).map { it.id }
        }.orEmpty()
        val (ids, context) = PlaybackRules.playbackIds(
            readyIds = ready.map { it.id },
            queue = snap.playQueue,
            currentId = snap.currentId,
            currentPlaylistId = current?.playlistId,
            playlistReadyIds = playlistIds,
            lockedContext = lockedContext,
        )
        lockedContext = context
        val wrap = playback.loopMode == LoopMode.QUEUE || !fromEnded
        val ordered = if (playback.shuffle) {
            if (shuffleContext != context) {
                shuffleOrder = PlaybackRules.rotateTo(PlaybackRules.shuffledIds(ids), snap.currentId)
                shufflePlayed = snap.currentId?.let { listOf(it) }.orEmpty()
                shuffleContext = context
            } else {
                shuffleOrder = PlaybackRules.mergeShuffleOrder(shuffleOrder, ids)
            }
            shuffleOrder
        } else ids
        val nextId = PlaybackRules.nextInOrder(ordered, snap.currentId, delta, wrap) ?: return
        val next = ready.find { it.id == nextId } ?: return
        playTrack(next)
    }

    fun saveStem(track: Track, stem: StemMode) {
        val path = when (stem) {
            StemMode.VOCALS -> track.vocalsPath
            StemMode.INSTRUMENTAL -> track.instrumentalPath
            StemMode.ORIGINAL -> null
        }
        if (track.status != TrackStatus.READY || path.isNullOrBlank()) {
            ui.update { it.copy(toast = "Stems are not ready yet.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val label = if (stem == StemMode.VOCALS) "vocals" else "instrumental"
            val name = LibraryRules.stemFileName(track.title, label)
            val ok = StemExport.saveWav(getApplication(), File(path), name, track.artist)
            ui.update {
                it.copy(
                    toast = if (ok) {
                        "Saved $label to Music/Dualis"
                    } else {
                        "Could not save $label. Try Export from the selection bar."
                    },
                )
            }
        }
    }

    fun exportSelected(destDir: Uri) {
        val selected = state.value.selectedIds
        val tracks = state.value.tracks.filter {
            it.status == TrackStatus.READY &&
                it.vocalsPath != null &&
                it.instrumentalPath != null &&
                (it.id in selected || (selected.isEmpty() && it.id == state.value.currentId))
        }
        exportSelected(destDir, tracks)
    }

    private fun exportSelected(destDir: Uri, tracks: List<Track>) {
        if (tracks.isEmpty()) {
            ui.update { it.copy(toast = "Select a ready track to export.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            try {
                app.contentResolver.takePersistableUriPermission(
                    destDir,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
            tracks.forEach { track ->
                val vocals = track.vocalsPath ?: return@forEach
                val inst = track.instrumentalPath ?: return@forEach
                copyToTree(app.contentResolver, destDir, File(vocals), "${track.title} - vocals.wav")
                copyToTree(app.contentResolver, destDir, File(inst), "${track.title} - instrumental.wav")
            }
            ui.update { it.copy(toast = "Exported stems for ${tracks.size} track${if (tracks.size == 1) "" else "s"}") }
        }
    }

    private fun copyToTree(
        resolver: android.content.ContentResolver,
        tree: Uri,
        file: File,
        name: String,
    ) {
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), tree) ?: return
        val dest = doc.createFile("audio/wav", name) ?: return
        resolver.openOutputStream(dest.uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private suspend fun ingestLocal(uri: Uri) {
        val id = UUID.randomUUID().toString()
        val destDir = repo.trackDir(id)
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
        val ext = name.substringAfterLast('.', "bin").ifBlank { "bin" }
        val dest = File(destDir, "source.$ext")
        withContext(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
            app.container.localFiles.copyFromUri(uri, dest)
        }
        val meta = app.container.localFiles.metadata(dest.absolutePath)
        val cover = File(destDir, "cover.jpg")
        com.z3itt.dualis.audio.AudioDecoder(getApplication()).extractCover(dest.absolutePath, cover)
        val track = Track(
            id = id,
            title = meta.first,
            artist = meta.second,
            sourceUrl = uri.toString(),
            sourceKind = "local",
            sourcePath = dest.absolutePath,
            coverPath = cover.takeIf { it.isFile && it.length() > 64L }?.absolutePath,
            status = TrackStatus.DOWNLOADED,
            createdAt = now(),
            updatedAt = now(),
        )
        repo.upsert(track)
        queue.enqueue(WorkItem(id, uri.toString()), false)
        JobForegroundService.start(getApplication())
    }

    private suspend fun ingestOneLink(input: String) {
        val batch = app.container.linkBackend.resolve(input)
        val playlistId = batch.playlist?.let { meta ->
            val id = UUID.randomUUID().toString()
            val coverFile = File(repo.trackDir("playlist-$id"), "cover.jpg")
            val coverOk = withContext(Dispatchers.IO) {
                app.container.linkBackend.saveCover(
                    coverFile,
                    app.container.linkBackend.coverUrls(meta.sourceUrl, null, meta.coverUrl),
                )
            }
            repo.upsertPlaylist(
                Playlist(
                    id = id,
                    title = meta.title,
                    artist = meta.artist,
                    sourceUrl = meta.sourceUrl,
                    sourceKind = meta.sourceKind,
                    coverPath = coverFile.takeIf { coverOk }?.absolutePath,
                    createdAt = now(),
                    updatedAt = now(),
                ),
            )
            id
        }
        batch.items.forEachIndexed { index, item ->
            val id = UUID.randomUUID().toString()
            val coverFile = File(repo.trackDir(id), "cover.jpg")
            val coverOk = withContext(Dispatchers.IO) {
                app.container.linkBackend.saveCover(
                    coverFile,
                    app.container.linkBackend.coverUrls(item.sourceUrl, item.ytdlpQuery, item.coverUrl),
                )
            }
            val track = Track(
                id = id,
                title = item.title,
                artist = item.artist,
                sourceUrl = item.sourceUrl,
                sourceKind = item.sourceKind,
                coverPath = coverFile.takeIf { coverOk }?.absolutePath,
                status = TrackStatus.QUEUED,
                createdAt = now(),
                updatedAt = now(),
                playlistId = playlistId,
                playlistIndex = index.toLong(),
                ytdlpQuery = item.ytdlpQuery,
            )
            repo.upsert(track)
            queue.enqueue(WorkItem(id, item.ytdlpQuery), false)
        }
        JobForegroundService.start(getApplication())
    }

    private suspend fun refreshRuntime() {
        val selected = repo.selectedModelId()
        val models = ModelCatalog.catalog.map { spec ->
            val file = File(repo.modelsDir(), spec.filename)
            com.z3itt.dualis.domain.model.ModelInfo(
                id = spec.id,
                name = spec.name,
                architecture = spec.architecture.name.lowercase(),
                ready = file.isFile && file.length() > 1_000_000,
                description = spec.description,
            )
        }
        ui.update {
            it.copy(
                runtime = RuntimeInfo(
                    modelPath = models.find { m -> m.id == selected }?.let { File(repo.modelsDir(), ModelCatalog.require(it.id).filename).absolutePath },
                    modelReady = models.find { m -> m.id == selected }?.ready == true,
                    selectedModel = selected,
                    executionProvider = "NNAPI / CPU",
                    compiledProviders = listOf("NNAPI", "CPU"),
                    models = models,
                    downloadFormat = repo.downloadFormat(),
                    firstRunHint = models.none { it.ready },
                ),
            )
        }
    }

    private suspend fun backfillMissingCovers() {
        val snap = repo.snapshot()
        snap.tracks.forEach { track ->
            val dest = File(repo.trackDir(track.id), "cover.jpg")
            if (dest.isFile && dest.length() > 64L) {
                if (track.coverPath.isNullOrBlank()) {
                    repo.upsert(track.copy(coverPath = dest.absolutePath, updatedAt = now()))
                }
                return@forEach
            }
            val saved = app.container.linkBackend.saveCover(
                dest,
                app.container.linkBackend.coverUrls(track.sourceUrl, track.ytdlpQuery),
            )
            if (saved) {
                repo.upsert(track.copy(coverPath = dest.absolutePath, updatedAt = now()))
            }
        }
        snap.playlists.forEach { playlist ->
            val dest = File(repo.trackDir("playlist-${playlist.id}"), "cover.jpg")
            if (dest.isFile && dest.length() > 64L) {
                if (playlist.coverPath.isNullOrBlank()) {
                    repo.upsertPlaylist(playlist.copy(coverPath = dest.absolutePath, updatedAt = now()))
                }
                return@forEach
            }
            val saved = app.container.linkBackend.saveCover(
                dest,
                app.container.linkBackend.coverUrls(playlist.sourceUrl, null),
            )
            if (saved) {
                repo.upsertPlaylist(playlist.copy(coverPath = dest.absolutePath, updatedAt = now()))
            }
        }
    }

    private fun now() = System.currentTimeMillis() / 1000
}
