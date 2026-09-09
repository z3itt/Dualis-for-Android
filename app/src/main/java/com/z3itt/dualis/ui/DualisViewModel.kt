package com.z3itt.dualis.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.z3itt.dualis.DualisApplication
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
    val runtime: RuntimeInfo = RuntimeInfo(),
    val toast: String? = null,
)

class DualisViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DualisApplication
    private val repo get() = app.container.repo
    private val queue get() = app.container.workQueue
    private val player get() = app.container.player

    private val jobs = MutableStateFlow<Map<String, JobEvent>>(emptyMap())
    private val ui = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = combine(repo.observeSnapshot(), jobs, ui) { snap, jobMap, extra ->
        extra.copy(tracks = snap.tracks, playlists = snap.playlists, jobs = jobMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private var shuffleOrder = emptyList<String>()
    private var shufflePlayed = emptyList<String>()
    private var shuffleContext = ""
    private var playHistory = emptyList<String>()
    private var lockedContext = ""

    init {
        viewModelScope.launch {
            val theme = repo.getSetting(LibraryRepository.SETTING_THEME, "light")
            ui.update { it.copy(themeDark = theme == "dark") }
            refreshRuntime()
        }
        viewModelScope.launch {
            JobForegroundService.bus().collect { event ->
                jobs.update { it + (event.trackId to event) }
                if (event.status == "error") {
                    ui.update { it.copy(toast = event.message) }
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
        player.onEnded = { viewModelScope.launch { skip(1, fromEnded = true) } }
        JobForegroundService.start(application)
        application.startService(Intent(application, PlaybackService::class.java))
    }

    fun setIngestText(value: String) = ui.update { it.copy(ingestText = value, ingestError = null) }
    fun setQuery(value: String) = ui.update { it.copy(query = value) }
    fun setSort(sort: LibrarySort) = ui.update { it.copy(sort = sort) }
    fun openSettings(open: Boolean) = ui.update { it.copy(settingsOpen = open) }
    fun openAbout(open: Boolean) = ui.update { it.copy(aboutOpen = open, settingsOpen = if (open) false else it.settingsOpen) }
    fun setQueueOpen(open: Boolean) = ui.update { it.copy(queueOpen = open) }
    fun dismissToast() = ui.update { it.copy(toast = null) }
    fun dismissError(id: String) = ui.update { it.copy(hiddenErrors = it.hiddenErrors + id) }

    fun setThemeDark(dark: Boolean) {
        ui.update { it.copy(themeDark = dark) }
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
                ui.update { it.copy(ingestText = "", ingestBusy = false) }
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
                ui.update { it.copy(ingestBusy = false) }
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
            ui.update { it.copy(hiddenErrors = it.hiddenErrors - track.id) }
            JobForegroundService.start(getApplication())
        }
    }

    fun prioritize(track: Track) = retry(track)

    fun deleteTrack(id: String) {
        viewModelScope.launch {
            if (ui.value.currentId == id) player.stop()
            repo.deleteTrack(id)
            ui.update {
                it.copy(
                    selectedIds = it.selectedIds - id,
                    playQueue = it.playQueue - id,
                    currentId = if (it.currentId == id) null else it.currentId,
                )
            }
        }
    }

    fun deleteSelected() {
        val ids = ui.value.selectedIds.toList()
        viewModelScope.launch { repo.deleteTracks(ids) }
        ui.update { it.copy(selectedIds = emptySet()) }
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
        viewModelScope.launch { repo.deletePlaylist(id) }
        ui.update { it.copy(openPlaylistId = if (it.openPlaylistId == id) null else it.openPlaylistId) }
    }

    fun addToQueue(id: String) = ui.update { it.copy(playQueue = (it.playQueue + id).distinct()) }
    fun removeFromQueue(id: String) = ui.update { it.copy(playQueue = it.playQueue - id) }
    fun clearQueue() = ui.update { it.copy(playQueue = emptyList()) }

    fun playTrack(track: Track) {
        if (track.status != TrackStatus.READY || track.vocalsPath == null || track.instrumentalPath == null) {
            prioritize(track)
            ui.update { it.copy(toast = "${track.title} is next in the queue. Playback starts when stems are ready.") }
            return
        }
        player.load(File(track.vocalsPath), File(track.instrumentalPath))
        player.setStemMode(ui.value.stemMode)
        player.setVolume(ui.value.volume)
        player.setLoopMode(ui.value.loopMode)
        player.play()
        ui.update { it.copy(currentId = track.id, playing = true, duration = (track.durationMs ?: 0) / 1000f) }
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
    }

    fun seek(seconds: Float) = player.seek((seconds * 1000).toLong())

    fun setVolume(volume: Float) {
        player.setVolume(volume)
        ui.update { it.copy(volume = volume) }
    }

    fun setStemMode(mode: StemMode) {
        player.setStemMode(mode)
        ui.update { it.copy(stemMode = mode) }
    }

    fun setShuffle(on: Boolean) {
        shuffleOrder = emptyList()
        shufflePlayed = emptyList()
        ui.update { it.copy(shuffle = on) }
    }

    fun cycleLoopMode() {
        val next = when (ui.value.loopMode) {
            LoopMode.OFF -> LoopMode.QUEUE
            LoopMode.QUEUE -> LoopMode.SONG
            LoopMode.SONG -> LoopMode.OFF
        }
        player.setLoopMode(next)
        ui.update { it.copy(loopMode = next) }
    }

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
        val wrap = snap.loopMode == LoopMode.QUEUE || !fromEnded
        val ordered = if (snap.shuffle) {
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

    fun exportStems(track: Track, destDir: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val vocals = track.vocalsPath ?: return@launch
            val inst = track.instrumentalPath ?: return@launch
            copyToTree(resolver, destDir, File(vocals), "${track.title} - vocals.wav")
            copyToTree(resolver, destDir, File(inst), "${track.title} - instrumental.wav")
            ui.update { it.copy(toast = "Exported stems for ${track.title}") }
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
        val ext = name.substringAfterLast('.', "bin")
        val dest = File(destDir, "source.$ext")
        withContext(Dispatchers.IO) { app.container.localFiles.copyFromUri(uri, dest) }
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
            coverPath = cover.takeIf { it.exists() }?.absolutePath,
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
            repo.upsertPlaylist(
                Playlist(
                    id = id,
                    title = meta.title,
                    artist = meta.artist,
                    sourceUrl = meta.sourceUrl,
                    sourceKind = meta.sourceKind,
                    createdAt = now(),
                    updatedAt = now(),
                ),
            )
            id
        }
        batch.items.forEachIndexed { index, item ->
            val id = UUID.randomUUID().toString()
            val track = Track(
                id = id,
                title = item.title,
                artist = item.artist,
                sourceUrl = item.sourceUrl,
                sourceKind = item.sourceKind,
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

    private fun now() = System.currentTimeMillis() / 1000
}
