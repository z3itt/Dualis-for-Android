package com.z3itt.dualis.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z3itt.dualis.domain.library.LibraryRules
import com.z3itt.dualis.domain.model.LibrarySort
import com.z3itt.dualis.domain.model.LoopMode
import com.z3itt.dualis.domain.model.StemMode
import com.z3itt.dualis.domain.model.Track
import com.z3itt.dualis.domain.model.TrackStatus
import com.z3itt.dualis.ui.DualisViewModel
import com.z3itt.dualis.ui.MainTab
import com.z3itt.dualis.ui.UiState
import com.z3itt.dualis.ui.components.BrandMark
import com.z3itt.dualis.ui.components.CoverArt
import com.z3itt.dualis.ui.components.StageChips
import com.z3itt.dualis.ui.components.StemModeToggle
import com.z3itt.dualis.ui.components.Waveform
import com.z3itt.dualis.ui.theme.DarkCard
import com.z3itt.dualis.ui.theme.DualisOrange
import com.z3itt.dualis.ui.theme.DualisTheme
import com.z3itt.dualis.ui.theme.LightCard

private val PanelShape = RoundedCornerShape(16.dp)
private val Pill = CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualisScreen(vm: DualisViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.exportSelected(uri)
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snack.showSnackbar(it)
            vm.dismissToast()
        }
    }

    val card = if (state.themeDark) DarkCard else LightCard
    DualisTheme(darkTheme = state.themeDark) {
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
                onTheme = vm::setThemeDark,
                onAbout = { vm.openAbout(true) },
                onSettings = { vm.openSettings(true) },
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AnimatedContent(
                        targetState = state.mainTab,
                        transitionSpec = {
                            val forward = targetState == MainTab.JOBS
                            (
                                fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { width ->
                                        if (forward) width / 8 else -width / 8
                                    }
                            ) togetherWith (
                                fadeOut(tween(160)) +
                                    slideOutHorizontally(tween(160)) { width ->
                                        if (forward) -width / 8 else width / 8
                                    }
                            )
                        },
                        label = "mainTab",
                    ) { tab ->
                        when (tab) {
                            MainTab.LIBRARY -> LibraryCard(state, card, vm, exportPicker::launch)
                            MainTab.JOBS -> JobQueueCard(state, card, vm::retry, vm::dismissError, vm::deleteTrack)
                        }
                    }
                }
            }
            PlayerBar(state, card, vm)
            DualisTabBar(state = state, card = card, onSelect = vm::setMainTab)
        }
        SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        if (state.aboutOpen) {
            AboutSheet(onDismiss = { vm.openAbout(false) }, dark = state.themeDark)
        }
        if (state.settingsOpen) {
            SettingsSheet(state = state, vm = vm, onDismiss = { vm.openSettings(false) })
        }
        if (state.queueOpen) {
            PlayQueueSheet(state, vm)
        }
    }
    }
}

@Composable
private fun SurfaceCard(
    card: Color,
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
private fun TopBar(
    state: UiState,
    card: Color,
    onIngest: (String) -> Unit,
    onSubmit: () -> Unit,
    onTheme: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onSettings: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    SurfaceCard(card, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), padding = 0.dp) {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(dark = state.themeDark, size = 48.dp)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Menu" },
                ) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                if (menu) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, with(density) { 40.dp.roundToPx() }),
                        onDismissRequest = { menu = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Surface(
                            modifier = Modifier.width(168.dp),
                            shape = PanelShape,
                            color = if (state.themeDark) Color(0xFF3A3A3A) else LightCard,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 0.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        ) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    ThemeModeToggle(dark = state.themeDark, onChange = onTheme)
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { menu = false; onSettings() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Settings", fontSize = 13.sp)
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { menu = false; onAbout() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("About", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.ingestText,
                onValueChange = onIngest,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Paste a link", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                shape = Pill,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DualisOrange,
                    cursorColor = DualisOrange,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSubmit,
                enabled = !state.ingestBusy,
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = DualisOrange, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Text(if (state.ingestBusy) "Working…" else "Separate", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                if (state.ingestBusy) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.NorthEast, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (state.ingestError != null) {
            Text(
                state.ingestError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        }
    }
}

@Composable
private fun ThemeModeToggle(dark: Boolean, onChange: (Boolean) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .width(84.dp)
            .height(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
            .semantics { contentDescription = if (dark) "Dark mode" else "Light mode" },
    ) {
        val segment = maxWidth / 2
        val offset by animateDpAsState(
            targetValue = if (dark) segment else 0.dp,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "themeOffset",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(DualisOrange),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .clickable { onChange(false) }
                    .semantics { contentDescription = "Light mode" },
                contentAlignment = Alignment.Center,
            ) {
                val tint by animateColorAsState(
                    targetValue = if (!dark) Color.White else MaterialTheme.colorScheme.onSurface,
                    animationSpec = tween(180),
                    label = "lightTint",
                )
                Icon(
                    Icons.Default.LightMode,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .clickable { onChange(true) }
                    .semantics { contentDescription = "Dark mode" },
                contentAlignment = Alignment.Center,
            ) {
                val tint by animateColorAsState(
                    targetValue = if (dark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "darkTint",
                )
                Icon(
                    Icons.Default.DarkMode,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun JobQueueCard(
    state: UiState,
    card: Color,
    onRetry: (Track) -> Unit,
    onDismiss: (String) -> Unit,
    onRemoveWaiting: (String) -> Unit,
) {
    val live = state.tracks.filter { LibraryRules.isLive(it.status) }
    val waiting = LibraryRules.waitingTracks(state.tracks, state.pendingJobIds)
    val failed = LibraryRules.failedJobTracks(state.tracks, state.failedTracks, state.hiddenErrors)
    SurfaceCard(card) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Active jobs", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${live.size} running" + if (waiting.isNotEmpty()) " · ${waiting.size} waiting" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (live.isEmpty() && waiting.isEmpty() && failed.isEmpty()) {
            Text(
                "Downloads, decode, inference, and export show up here with live progress. Paste another link while one is running and it waits in this list.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
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
            if (waiting.isNotEmpty()) {
                Text(
                    "Queue",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (live.isEmpty()) 0.dp else 4.dp, bottom = 8.dp),
                )
            }
            waiting.forEachIndexed { index, track ->
                val position = live.size + index + 1
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#$position",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DualisOrange,
                        modifier = Modifier.width(36.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            if (index == 0 && live.isNotEmpty()) "Next up" else "Waiting",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(
                        onClick = { onRemoveWaiting(track.id) },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Remove ${track.title} from queue" },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove ${track.title} from queue")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            failed.forEach { track ->
                val errBg = if (state.themeDark) Color(0xFF450A0A) else Color(0xFFFFF1F1)
                val errBorder = if (state.themeDark) Color(0xFF7F1D1D) else Color(0xFFFECACA)
                val errText = if (state.themeDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(errBg)
                        .border(1.dp, errBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(track.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        IconButton(onClick = { onDismiss(track.id) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Dismiss error for ${track.title}" }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss error for ${track.title}")
                        }
                    }
                    Text(track.error ?: "Failed", fontSize = 12.sp, color = errText)
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
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DualisOrange,
                cursorColor = DualisOrange,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                        .background(if (on) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                        .clickable { vm.setSort(id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
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
            Text("Paste a link to start.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val density = LocalDensity.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) DualisOrange.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { vm.playTrack(track) }
            .padding(start = 2.dp, top = 8.dp, end = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { vm.toggleSelected(track.id) },
            modifier = Modifier
                .size(36.dp)
                .scale(0.82f)
                .semantics { contentDescription = "Select ${track.title}" },
        )
        CoverArt(track.coverPath, track.title, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(track.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (track.status == TrackStatus.SEPARATING) {
            Text(
                "Separating",
                color = DualisOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box {
            IconButton(
                onClick = { menu = true },
                modifier = Modifier.size(40.dp).semantics { contentDescription = "More for ${track.title}" },
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More for ${track.title}")
            }
            if (menu) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, with(density) { 40.dp.roundToPx() }),
                    onDismissRequest = { menu = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Surface(
                        modifier = Modifier.width(204.dp),
                        shape = PanelShape,
                        color = if (state.themeDark) Color(0xFF3A3A3A) else LightCard,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { menu = false; vm.addToQueue(track.id) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Add to queue", fontSize = 13.sp)
                            }
                            if (track.status == TrackStatus.READY && track.vocalsPath != null && track.instrumentalPath != null) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { menu = false; vm.saveStem(track, StemMode.VOCALS) }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Save vocals", fontSize = 13.sp)
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { menu = false; vm.saveStem(track, StemMode.INSTRUMENTAL) }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Save instrumental", fontSize = 13.sp)
                                }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { menu = false; vm.deleteTrack(track.id) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Delete", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(state: UiState, card: Color, vm: DualisViewModel) {
    val current = state.tracks.find { it.id == state.currentId }
    SurfaceCard(card, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(current?.coverPath, current?.title ?: "Dualis", size = 36.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current?.title ?: "Nothing playing",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    current?.artist ?: "Load a ready stem to begin",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = { vm.setQueueOpen(true) },
                modifier = Modifier.size(40.dp).semantics { contentDescription = "Play queue" },
            ) {
                BadgedBox(
                    badge = {
                        if (state.playQueue.isNotEmpty()) {
                            Badge(containerColor = DualisOrange) {
                                Text("${state.playQueue.size}", fontSize = 10.sp)
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Play queue", modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { vm.setShuffle(!state.shuffle) },
                modifier = Modifier.size(40.dp).semantics { contentDescription = if (state.shuffle) "Disable shuffle" else "Enable shuffle" },
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (state.shuffle) DualisOrange else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { vm.skip(-1) }, modifier = Modifier.size(40.dp).semantics { contentDescription = "Previous track" }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = vm::togglePlay,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .semantics { contentDescription = if (state.playing) "Pause" else "Play" },
            ) {
                Icon(
                    if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.playing) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = { vm.skip(1) }, modifier = Modifier.size(40.dp).semantics { contentDescription = "Next track" }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next track", modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = vm::cycleLoopMode,
                modifier = Modifier.size(40.dp).semantics {
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
                    modifier = Modifier.size(20.dp),
                    tint = if (state.loopMode != LoopMode.OFF) DualisOrange else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        StemModeToggle(state.stemMode, vm::setStemMode, Modifier.align(Alignment.CenterHorizontally), compact = true)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(LibraryRules.formatTime(state.currentTime), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
            Waveform(
                bars = current?.peaks ?: List(64) { 0.12f },
                progress = if (state.duration > 0) state.currentTime / state.duration else 0f,
                onSeek = { vm.seek(it * state.duration) },
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            Text(LibraryRules.formatTime(state.duration), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
        }
    }
}

@Composable
private fun DualisTabBar(state: UiState, card: Color, onSelect: (MainTab) -> Unit) {
    val live = state.tracks.count { LibraryRules.isLive(it.status) }
    val waiting = LibraryRules.waitingTracks(state.tracks, state.pendingJobIds).size
    val badge = LibraryRules.jobBadgeCount(live, waiting)
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(card)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DualisTab(
                selected = state.mainTab == MainTab.LIBRARY,
                label = "Library",
                icon = Icons.Default.LibraryMusic,
                onClick = { onSelect(MainTab.LIBRARY) },
            )
            DualisTab(
                selected = state.mainTab == MainTab.JOBS,
                label = "Active jobs",
                icon = Icons.Default.GraphicEq,
                badge = badge.takeIf { it > 0 },
                onClick = { onSelect(MainTab.JOBS) },
            )
        }
    }
}

@Composable
private fun DualisTab(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) DualisOrange else Color.Transparent,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "tabBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(180),
        label = "tabFg",
    )
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.2f) else DualisOrange),
                contentAlignment = Alignment.Center,
            ) {
                Text("$badge", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: UiState, vm: DualisViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Separation model", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "Kim Vocal 2 is the default. Karaoke 2 is optional and quicker on phones.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            state.runtime.models.forEach { model ->
                val selected = model.id == state.runtime.selectedModel
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.setModel(model.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { vm.setModel(model.id) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(model.name, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                        if (model.description.isNotBlank()) {
                            Text(model.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Download quality", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            listOf(
                "auto" to "Auto (recommended)",
                "best" to "Best audio",
                "any" to "Any available",
                "fast" to "Fast / low bandwidth",
            ).forEach { (id, label) ->
                val selected = state.runtime.downloadFormat == id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.setDownloadFormat(id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { vm.setDownloadFormat(id) },
                    )
                    Text(label, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit, dark: Boolean) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(dark = dark, size = 72.dp, discOnly = true)
            Spacer(Modifier.height(12.dp))
            Text(
                "DUΛLIS",
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "On-device vocal and instrumental stem separation",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Version 1.0.0 (Android)", fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                "Sibling of desktop Dualis 1.0.4",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("License: GPL-3.0-or-later", fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("developed by z3itt", fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                "info@z3itt.com",
                fontSize = 13.sp,
                color = DualisOrange,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/z3itt/Dualis")))
            }) { Text("Desktop source on GitHub", textAlign = TextAlign.Center) }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://z3itt.com")))
            }) { Text("z3itt.com", textAlign = TextAlign.Center) }
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
