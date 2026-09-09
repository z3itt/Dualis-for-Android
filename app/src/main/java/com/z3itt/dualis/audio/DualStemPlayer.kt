package com.z3itt.dualis.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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

    val vocals: ExoPlayer = ExoPlayer.Builder(context).setAudioAttributes(attrs, true).build()
    val instrumental: ExoPlayer = ExoPlayer.Builder(context).setAudioAttributes(attrs, false).build()

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

    init {
        vocals.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && loopMode != LoopMode.SONG) {
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
                instrumental.seekTo(vocals.currentPosition)
            }
        })
    }

    fun load(vocalsFile: File, instFile: File) {
        stop()
        vocals.setMediaItem(MediaItem.fromUri(vocalsFile.absolutePath))
        instrumental.setMediaItem(MediaItem.fromUri(instFile.absolutePath))
        vocals.prepare()
        instrumental.prepare()
        applyVolumes()
        durationMs = maxOf(vocals.duration.coerceAtLeast(0), instrumental.duration.coerceAtLeast(0))
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
        vocals.stop()
        instrumental.stop()
        _playing.value = false
    }

    fun seek(ms: Long) {
        vocals.seekTo(ms)
        instrumental.seekTo(ms)
    }

    fun currentPosition(): Long = vocals.currentPosition.coerceAtLeast(0)

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

    private fun syncIfNeeded() {
        val drift = abs(vocals.currentPosition - instrumental.currentPosition)
        if (drift > 40) {
            instrumental.seekTo(vocals.currentPosition)
        }
        durationMs = minOf(
            vocals.duration.takeIf { it > 0 } ?: Long.MAX_VALUE,
            instrumental.duration.takeIf { it > 0 } ?: Long.MAX_VALUE,
        ).takeIf { it != Long.MAX_VALUE } ?: maxOf(vocals.duration, instrumental.duration).coerceAtLeast(0)
    }
}
