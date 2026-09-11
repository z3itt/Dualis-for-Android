package com.z3itt.dualis.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.z3itt.dualis.domain.model.LoopMode
import com.z3itt.dualis.domain.model.StemMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.abs

class DualStemPlayer(context: Context) {
    private val attrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    val vocals: ExoPlayer = ExoPlayer.Builder(context).setAudioAttributes(attrs, true).build().also {
        it.setWakeMode(C.WAKE_MODE_LOCAL)
        it.setHandleAudioBecomingNoisy(true)
    }
    val instrumental: ExoPlayer = ExoPlayer.Builder(context).setAudioAttributes(attrs, false).build().also {
        it.setWakeMode(C.WAKE_MODE_LOCAL)
    }

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    var durationMs: Long = 0
        private set
    var mode: StemMode = StemMode.ORIGINAL
        private set
    var volume: Float = 0.9f
        private set
    var loopMode: LoopMode = LoopMode.OFF
        private set
    var onEnded: (() -> Unit)? = null
    private var ignoreEnded = false

    init {
        vocals.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    refreshDuration()
                }
                if (playbackState == Player.STATE_ENDED && loopMode != LoopMode.SONG && !ignoreEnded) {
                    _playing.value = false
                    onEnded?.invoke()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playing.value = isPlaying
                if (isPlaying) instrumental.play() else instrumental.pause()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (abs(instrumental.currentPosition - vocals.currentPosition) > 40) {
                    instrumental.seekTo(vocals.currentPosition)
                }
            }
        })
        instrumental.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) refreshDuration()
            }
        })
    }

    fun load(
        vocalsFile: File,
        instFile: File,
        title: String = vocalsFile.nameWithoutExtension,
        artist: String = "Dualis",
        coverPath: String? = null,
        mediaId: String = vocalsFile.absolutePath,
        knownDurationMs: Long? = null,
    ) {
        ignoreEnded = true
        if (knownDurationMs != null && knownDurationMs > 0) durationMs = knownDurationMs
        val meta = metadata(title, artist, coverPath, durationMs.takeIf { it > 0 })
        vocals.setMediaItem(mediaItem(vocalsFile, mediaId, meta), true)
        instrumental.setMediaItem(mediaItem(instFile, "$mediaId:inst", meta), true)
        vocals.prepare()
        instrumental.prepare()
        applyVolumes()
        refreshDuration()
        ignoreEnded = false
    }

    fun play() {
        syncIfNeeded()
        applyVolumes()
        vocals.play()
        instrumental.play()
        _playing.value = true
    }

    fun pause() {
        vocals.pause()
        instrumental.pause()
        _playing.value = false
    }

    fun toggle() {
        if (_playing.value) pause() else play()
    }

    fun stop() {
        ignoreEnded = true
        vocals.pause()
        instrumental.pause()
        vocals.seekTo(0)
        instrumental.seekTo(0)
        _playing.value = false
        ignoreEnded = false
    }

    fun clear() {
        stop()
        ignoreEnded = true
        vocals.clearMediaItems()
        instrumental.clearMediaItems()
        durationMs = 0
        ignoreEnded = false
    }

    fun seek(ms: Long) {
        vocals.seekTo(ms)
        instrumental.seekTo(ms)
    }

    fun currentPosition(): Long = vocals.currentPosition.coerceAtLeast(0)

    fun currentMediaId(): String? = vocals.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }

    fun hasMedia(): Boolean = vocals.mediaItemCount > 0 && currentMediaId() != null

    fun setStemMode(mode: StemMode) {
        this.mode = mode
        applyVolumes()
    }

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyVolumes()
    }

    fun setLoopMode(mode: LoopMode) {
        loopMode = mode
        val song = mode == LoopMode.SONG
        vocals.repeatMode = if (song) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        instrumental.repeatMode = if (song) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun release() {
        vocals.release()
        instrumental.release()
    }

    private fun applyVolumes() {
        val master = volume
        when (mode) {
            StemMode.ORIGINAL -> {
                vocals.volume = master
                instrumental.volume = master
            }
            StemMode.VOCALS -> {
                vocals.volume = master
                instrumental.volume = 0f
            }
            StemMode.INSTRUMENTAL -> {
                vocals.volume = 0f
                instrumental.volume = master
            }
        }
    }

    private fun refreshDuration() {
        val left = vocals.duration
        val right = instrumental.duration
        durationMs = when {
            left > 0 && right > 0 -> minOf(left, right)
            left > 0 -> left
            right > 0 -> right
            else -> 0
        }
    }

    private fun syncIfNeeded() {
        val drift = abs(vocals.currentPosition - instrumental.currentPosition)
        if (drift > 40) {
            instrumental.seekTo(vocals.currentPosition)
        }
        refreshDuration()
    }

    private fun mediaItem(file: File, id: String, metadata: MediaMetadata): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(metadata)
            .build()

    private fun metadata(
        title: String,
        artist: String,
        coverPath: String?,
        durationMs: Long?,
    ): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setSubtitle(artist)
            .setAlbumTitle("Dualis")
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
        if (durationMs != null && durationMs > 0) {
            builder.setDurationMs(durationMs)
        }
        val cover = coverPath?.let { File(it) }?.takeIf { it.isFile && it.length() > 64L }
        if (cover != null) {
            builder.setArtworkUri(Uri.fromFile(cover))
            runCatching {
                val bytes = cover.readBytes()
                if (bytes.size in 64..1_500_000) {
                    builder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
        }
        return builder.build()
    }
}
