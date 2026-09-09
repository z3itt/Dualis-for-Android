package com.z3itt.dualis.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z3itt.dualis.domain.library.LibraryRules
import com.z3itt.dualis.domain.model.LibrarySort
import com.z3itt.dualis.domain.model.LoopMode
import com.z3itt.dualis.domain.model.Track
import com.z3itt.dualis.domain.model.TrackStatus
import com.z3itt.dualis.ui.DualisViewModel
import com.z3itt.dualis.ui.UiState
import com.z3itt.dualis.ui.components.BrandMark
import com.z3itt.dualis.ui.components.CoverArt
import com.z3itt.dualis.ui.components.StageChips
import com.z3itt.dualis.ui.components.StemModeToggle
import com.z3itt.dualis.ui.components.Waveform
import com.z3itt.dualis.ui.theme.DarkCard
import com.z3itt.dualis.ui.theme.DualisOrange
import com.z3itt.dualis.ui.theme.LightCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PanelShape = RoundedCornerShape(16.dp)
private val Pill = CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualisScreen(vm: DualisViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.ingestLocalUris(uris)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val track = state.tracks.find { it.id in state.selectedIds && it.status == TrackStatus.READY }
            ?: state.tracks.find { it.id == state.currentId }
        if (uri != null && track != null) vm.exportStems(track, uri)
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snack.showSnackbar(it)
            vm.dismissToast()
        }
    }

    val card = if (state.themeDark) DarkCard else LightCard
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                card = card,
                onIngest = vm::setIngestText,
                onSubmit = vm::ingestLinks,
                onFiles = { filePicker.launch(arrayOf("audio/*")) },
                onTheme = vm::setThemeDark,
                onModel = vm::setModel,
                onFormat = vm::setDownloadFormat,
                onAbout = { vm.openAbout(true) },
                onSettings = vm::openSettings,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { JobQueueCard(state, card, vm::retry, vm::dismissError) }
                item { LibraryCard(state, card, vm, exportPicker::launch) }
            }
            PlayerBar(state, card, vm)
        }
        SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
        if (state.aboutOpen) {
            AboutSheet(onDismiss = { vm.openAbout(false) }, dark = state.themeDark)
        }
        if (state.queueOpen) {
            PlayQueueSheet(state, vm)
        }
    }
}

@Composable
private fun SurfaceCard(card: Color, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, PanelShape)
            .clip(PanelShape)
            .background(card)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), PanelShape)
            .padding(16.dp),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopBar(
    state: UiState,
    card: Color,
    onIngest: (String) -> Unit,
    onSubmit: () -> Unit,
    onFiles: () -> Unit,
    onTheme: (Boolean) -> Unit,
    onModel: (String) -> Unit,
    onFormat: (String) -> Unit,
    onAbout: () -> Unit,
    onSettings: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SurfaceCard(card, Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(dark = state.themeDark, size = 48.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("DUΛLIS", fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, fontSize = 14.sp)
                Text("Local vocal separation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Settings" },
                ) { Icon(Icons.Default.MoreVert, contentDescription = "Settings") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Text("Separation model", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.runtime.models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(if (model.id == state.runtime.selectedModel) "● ${model.name}" else model.name) },
                            onClick = { onModel(model.id); menu = false },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (state.themeDark) Icons.Default.DarkMode else Icons.Default.LightMode, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.themeDark) "Dark mode" else "Light mode")
                                Spacer(Modifier.width(8.dp))
                                Switch(checked = !state.themeDark, onCheckedChange = { onTheme(!it) })
                            }
                        },
                        onClick = { onTheme(!state.themeDark) },
                    )
                    listOf("auto" to "Auto (recommended)", "best" to "Best audio", "any" to "Any available", "fast" to "Fast / low bandwidth").forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(if (state.runtime.downloadFormat == id) "● $label" else label) },
                            onClick = { onFormat(id); menu = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Row { Icon(Icons.Default.Info, null); Spacer(Modifier.width(8.dp)); Text("About") } },
                        onClick = { menu = false; onAbout() },
                    )
                    Text("developed by z3itt", modifier = Modifier.padding(16.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.ingestText,
            onValueChange = onIngest,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste Spotify, YouTube Music, or playlist links", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            shape = Pill,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DualisOrange,
                cursorColor = DualisOrange,
            ),
        )
        if (state.runtime.firstRunHint) {
            Text(
                "The first stem job downloads Kim Vocal 2 (about 60–80 MB) onto this device.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (state.ingestError != null) {
            Text(state.ingestError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSubmit,
                enabled = !state.ingestBusy,
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = DualisOrange),
                modifier = Modifier.height(48.dp),
            ) {
                Text(if (state.ingestBusy) "Working…" else "Separate")
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.ingestBusy) CircularProgressIndicator(Modifier.size(14.dp), color = DualisOrange, strokeWidth = 2.dp)
                    else Icon(Icons.Default.NorthEast, contentDescription = null, tint = DualisOrange, modifier = Modifier.size(16.dp))
                }
            }
            OutlinedButton(onClick = onFiles, shape = Pill, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Open local audio files")
                Spacer(Modifier.width(6.dp))
                Text("Files")
            }
        }
    }
}

@Composable
private fun SectionBadge(number: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF171717)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, Pill)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JobQueueCard(
    state: UiState,
    card: Color,
    onRetry: (Track) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val live = state.tracks.filter { LibraryRules.isLive(it.status) }
    val waiting = state.tracks.filter { it.status == TrackStatus.QUEUED }
    val failed = state.tracks.filter { it.status == TrackStatus.ERROR && it.id !in state.hiddenErrors }.take(8)
    SurfaceCard(card) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                SectionBadge("1", "Queue")
                Spacer(Modifier.height(8.dp))
                Text("Active jobs", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                "${live.size} running" + if (waiting.isNotEmpty()) " · ${waiting.size} waiting" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (live.isEmpty() && waiting.isEmpty() && failed.isEmpty()) {
            Text(
                "Downloads, decode, inference, and export show up here with live progress.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (waiting.isNotEmpty()) {
                Text(
                    "${waiting.size} track${if (waiting.size == 1) "" else "s"} will process one at a time.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            live.forEach { track ->
                val job = state.jobs[track.id]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                        .padding(bottom = 8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(track.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        CircularProgressIndicator(Modifier.size(16.dp), color = DualisOrange, strokeWidth = 2.dp)
                    }
                    StageChips(job?.stage ?: track.status.raw())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(job?.message ?: track.status.raw(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val eta = job?.let { LibraryRules.formatEta(LibraryRules.parseEtaSeconds(it)) }.orEmpty()
                        if (eta.isNotEmpty()) Text(eta, fontSize = 12.sp, color = DualisOrange, fontWeight = FontWeight.Medium)
                    }
                    LinearProgressIndicator(
                        progress = { job?.progress ?: 0.05f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(Pill),
                        color = DualisOrange,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            failed.forEach { track ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF1F1))
                        .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(track.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        IconButton(onClick = { onDismiss(track.id) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Dismiss error for ${track.title}" }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss error for ${track.title}")
                        }
                    }
                    Text(track.error ?: "Failed", fontSize = 12.sp, color = Color(0xFFB91C1C))
                    TextButton(onClick = { onRetry(track) }) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryCard(
    state: UiState,
    card: Color,
    vm: DualisViewModel,
    onExport: (Uri?) -> Unit,
) {
    val playlist = state.playlists.find { it.id == state.openPlaylistId }
    val visible = if (playlist != null) {
        LibraryRules.tracksInPlaylist(state.tracks, playlist.id)
    } else {
        LibraryRules.standaloneTracks(state.tracks)
    }
    val filtered = if (playlist != null) {
        LibraryRules.filterTracks(visible, state.query)
    } else {
        LibraryRules.sortTracks(LibraryRules.filterTracks(visible, state.query), state.sort)
    }
    val playlists = if (playlist != null) emptyList() else state.playlists.filter {
        "${it.title} ${it.artist}".lowercase().contains(state.query.trim().lowercase())
    }
    SurfaceCard(card) {
        SectionBadge("2", "Library")
        Spacer(Modifier.height(8.dp))
        if (playlist != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.openPlaylist(null) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back to library" }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Back to library")
                }
                Column {
                    Text(playlist.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("${playlist.artist} · ${playlist.readyCount}/${playlist.trackCount} ready", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text("Your library", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text(if (playlist != null) "Search playlist" else "Search library") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = Pill,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                LibrarySort.RECENT to "Recent",
                LibrarySort.TITLE to "Title",
                LibrarySort.ARTIST to "Artist",
                LibrarySort.DURATION to "Length",
                LibrarySort.STATUS to "Status",
            ).forEach { (id, label) ->
                val on = state.sort == id
                Text(
                    label,
                    modifier = Modifier
                        .clip(Pill)
                        .background(if (on) Color(0xFF171717) else Color.Transparent)
                        .clickable { vm.setSort(id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
        if (state.selectedIds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { onExport(null) }, shape = Pill, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Export selected stems")
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
                OutlinedButton(onClick = vm::deleteSelected, shape = Pill, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        playlists.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.openPlaylist(item.id) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(item.coverPath, item.title)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${item.artist} · ${item.readyCount}/${item.trackCount} ready", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (filtered.isEmpty() && playlists.isEmpty()) {
            Text("Paste a link or open a local audio file to start.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        filtered.forEach { track ->
            TrackRow(track, state, vm)
        }
    }
}

@Composable
private fun TrackRow(track: Track, state: UiState, vm: DualisViewModel) {
    val selected = track.id in state.selectedIds
    val current = track.id == state.currentId
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) DualisOrange.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { vm.playTrack(track) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { vm.toggleSelected(track.id) },
            modifier = Modifier.semantics { contentDescription = "Select ${track.title}" },
        )
        CoverArt(track.coverPath, track.title, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(track.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            track.status.name,
            color = if (track.status == TrackStatus.READY) DualisOrange else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = { vm.addToQueue(track.id) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Add ${track.title} to queue" }) {
            Icon(Icons.Default.QueueMusic, contentDescription = "Add ${track.title} to queue")
        }
        IconButton(onClick = { vm.deleteTrack(track.id) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Delete ${track.title}" }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${track.title}")
        }
    }
}

@Composable
private fun PlayerBar(state: UiState, card: Color, vm: DualisViewModel) {
    val current = state.tracks.find { it.id == state.currentId }
    SurfaceCard(card, Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(current?.coverPath, current?.title ?: "Dualis", size = 52.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(current?.title ?: "Nothing playing", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(current?.artist ?: "Load a ready stem to begin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { vm.setShuffle(!state.shuffle) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = if (state.shuffle) "Disable shuffle" else "Enable shuffle" },
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = if (state.shuffle) DualisOrange else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.skip(-1) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous track" }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track")
            }
            IconButton(
                onClick = vm::togglePlay,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF171717))
                    .semantics { contentDescription = if (state.playing) "Pause" else "Play" },
            ) {
                Icon(
                    if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.playing) "Pause" else "Play",
                    tint = Color.White,
                )
            }
            IconButton(onClick = { vm.skip(1) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Next track" }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next track")
            }
            IconButton(
                onClick = vm::cycleLoopMode,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = when (state.loopMode) {
                        LoopMode.SONG -> "Repeat song"
                        LoopMode.QUEUE -> "Repeat queue"
                        LoopMode.OFF -> "Repeat off"
                    }
                },
            ) {
                Icon(
                    if (state.loopMode == LoopMode.SONG) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = null,
                    tint = if (state.loopMode != LoopMode.OFF) DualisOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        StemModeToggle(state.stemMode, vm::setStemMode, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(LibraryRules.formatTime(state.currentTime), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
            Waveform(
                bars = current?.peaks ?: List(64) { 0.12f },
                progress = if (state.duration > 0) state.currentTime / state.duration else 0f,
                onSeek = { vm.seek(it * state.duration) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(LibraryRules.formatTime(state.duration), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { vm.setQueueOpen(true) }, shape = Pill, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.QueueMusic, contentDescription = "Play queue")
                Spacer(Modifier.width(4.dp))
                Text("Queue ${state.playQueue.size.takeIf { it > 0 } ?: ""}".trim())
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.VolumeUp, contentDescription = "Master volume", tint = DualisOrange)
            Slider(
                value = state.volume,
                onValueChange = vm::setVolume,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = SliderDefaults.colors(thumbColor = DualisOrange, activeTrackColor = DualisOrange),
            )
        }
        Text(
            "${state.runtime.compiledProviders.joinToString(" / ")} · 32-bit float WAV · runs fully on your machine",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit, dark: Boolean) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(dark = dark, size = 72.dp)
            Spacer(Modifier.height(12.dp))
            Text("DUΛLIS", fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            Text("On-device vocal and instrumental stem separation", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("Version 1.0.0 (Android)", fontSize = 13.sp)
            Text("Sibling of desktop Dualis 1.0.2", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("License: GPL-3.0-or-later", fontSize = 13.sp)
            Text("developed by z3itt", fontSize = 13.sp)
            Text("info@z3itt.com", fontSize = 13.sp, color = DualisOrange)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/z3itt/Dualis")))
            }) { Text("Desktop source on GitHub") }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://z3itt.com")))
            }) { Text("z3itt.com") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayQueueSheet(state: UiState, vm: DualisViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.setQueueOpen(false) }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Play queue", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Row {
                    TextButton(onClick = vm::clearQueue) { Text("Clear") }
                    IconButton(onClick = { vm.setQueueOpen(false) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Close queue" }) {
                        Icon(Icons.Default.Close, contentDescription = "Close queue")
                    }
                }
            }
            val queued = state.playQueue.mapNotNull { id -> state.tracks.find { it.id == id } }
            if (queued.isEmpty()) {
                Text("Queue is empty. Add ready tracks from the library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                queued.forEachIndexed { index, track ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", modifier = Modifier.width(20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CoverArt(track.coverPath, track.title, size = 40.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).clickable { vm.playTrack(track) }) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { vm.removeFromQueue(track.id) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Remove ${track.title}" }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${track.title}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
