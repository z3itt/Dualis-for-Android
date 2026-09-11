package com.z3itt.dualis.audio

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import com.z3itt.dualis.domain.model.LoopMode
import com.z3itt.dualis.domain.model.StemMode
import java.util.IdentityHashMap

internal class SessionPlayer(
    real: ExoPlayer,
    private val hub: PlaybackHub,
) : ForwardingPlayer(real) {
    private val extras = IdentityHashMap<Player.Listener, Player.Listener>()
    private val main = Handler(Looper.getMainLooper())

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: @Player.Command Int): Boolean =
        availableCommands.contains(command)

    override fun getMediaMetadata(): MediaMetadata = decorate(super.getMediaMetadata())

    override fun getDuration(): Long = resolvedDuration(super.getDuration())

    override fun getContentDuration(): Long = resolvedDuration(super.getContentDuration())

    override fun getPlaybackState(): Int {
        val state = super.getPlaybackState()
        if (
            (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) &&
            mediaItemCount > 0 &&
            playWhenReady &&
            resolvedDuration(C.TIME_UNSET) > 0
        ) {
            return Player.STATE_READY
        }
        return state
    }

    override fun addListener(listener: Player.Listener) {
        val wrapped = object : Player.Listener by listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                listener.onAvailableCommandsChanged(this@SessionPlayer.availableCommands)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                listener.onEvents(this@SessionPlayer, events)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                listener.onPlaybackStateChanged(this@SessionPlayer.playbackState)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                listener.onRepeatModeChanged(this@SessionPlayer.repeatMode)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                listener.onShuffleModeEnabledChanged(hub.shuffle)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                listener.onMediaMetadataChanged(decorate(mediaMetadata))
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                listener.onMediaItemTransition(decorateItem(mediaItem), reason)
            }
        }
        extras[listener] = wrapped
        super.addListener(wrapped)
    }

    override fun removeListener(listener: Player.Listener) {
        val wrapped = extras.remove(listener)
        if (wrapped != null) super.removeListener(wrapped) else super.removeListener(listener)
    }

    override fun seekToNext() {
        main.post { hub.skip(1) }
    }

    override fun seekToNextMediaItem() {
        main.post { hub.skip(1) }
    }

    override fun seekToPrevious() {
        if (currentPosition > RESTART_MS) seekTo(0) else main.post { hub.skip(-1) }
    }

    override fun seekToPreviousMediaItem() {
        main.post { hub.skip(-1) }
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun getShuffleModeEnabled(): Boolean = hub.shuffle

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        hub.setShuffle(shuffleModeEnabled)
    }

    override fun getRepeatMode(): Int = when (hub.loopMode) {
        LoopMode.OFF -> Player.REPEAT_MODE_OFF
        LoopMode.QUEUE -> Player.REPEAT_MODE_ALL
        LoopMode.SONG -> Player.REPEAT_MODE_ONE
    }

    override fun setRepeatMode(repeatMode: Int) {
        hub.setLoopMode(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> LoopMode.SONG
                Player.REPEAT_MODE_ALL -> LoopMode.QUEUE
                else -> LoopMode.OFF
            },
        )
    }

    fun dispatchSessionChanged() {
        extras.keys.forEach { listener ->
            listener.onTimelineChanged(currentTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            listener.onMediaItemTransition(currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
            listener.onMediaMetadataChanged(mediaMetadata)
            listener.onPlaybackStateChanged(playbackState)
            listener.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            listener.onIsPlayingChanged(isPlaying)
            listener.onRepeatModeChanged(repeatMode)
            listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
            listener.onAvailableCommandsChanged(availableCommands)
        }
    }

    private fun resolvedDuration(actual: Long): Long {
        if (actual > 0 && actual != C.TIME_UNSET) return actual
        val meta = super.getMediaMetadata().durationMs
        if (meta != null && meta > 0 && meta != C.TIME_UNSET) return meta
        return if (hub.durationMs > 0) hub.durationMs else C.TIME_UNSET
    }

    private fun decorateItem(item: MediaItem?): MediaItem? {
        if (item == null) return null
        return item.buildUpon().setMediaMetadata(decorate(item.mediaMetadata)).build()
    }

    private fun decorate(base: MediaMetadata): MediaMetadata {
        val stem = when (hub.stemMode) {
            StemMode.ORIGINAL -> "Normal"
            StemMode.VOCALS -> "Vocal"
            StemMode.INSTRUMENTAL -> "Inst"
        }
        val artist = base.artist?.toString().orEmpty()
        val line = if (artist.isBlank()) stem else "$artist · $stem"
        val builder = base.buildUpon()
            .setArtist(line)
            .setSubtitle(stem)
        val known = base.durationMs
        val duration = when {
            known != null && known > 0 && known != C.TIME_UNSET -> known
            hub.durationMs > 0 -> hub.durationMs
            else -> C.TIME_UNSET
        }
        if (duration > 0 && duration != C.TIME_UNSET) {
            builder.setDurationMs(duration)
        }
        return builder.build()
    }

    companion object {
        private const val RESTART_MS = 3_000L
    }
}
