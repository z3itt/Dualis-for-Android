package com.z3itt.dualis.audio

import com.z3itt.dualis.domain.model.LoopMode
import com.z3itt.dualis.domain.model.StemMode
import java.util.concurrent.CopyOnWriteArrayList

class PlaybackHub {
    var onSkip: (Int, Boolean) -> Unit = { _, _ -> }
    var onShuffleChanged: (Boolean) -> Unit = {}
    var onLoopChanged: (LoopMode) -> Unit = {}
    var onStemChanged: (StemMode) -> Unit = {}

    var shuffle: Boolean = false
        private set
    var loopMode: LoopMode = LoopMode.OFF
        private set
    var stemMode: StemMode = StemMode.ORIGINAL
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun skip(delta: Int, fromEnded: Boolean = false) {
        onSkip(delta, fromEnded)
    }

    fun setShuffle(on: Boolean) {
        if (shuffle == on) return
        shuffle = on
        onShuffleChanged(on)
        notifyChanged()
    }

    fun toggleShuffle() = setShuffle(!shuffle)

    fun setLoopMode(mode: LoopMode) {
        if (loopMode == mode) return
        loopMode = mode
        onLoopChanged(mode)
        notifyChanged()
    }

    fun cycleLoop() {
        setLoopMode(
            when (loopMode) {
                LoopMode.OFF -> LoopMode.QUEUE
                LoopMode.QUEUE -> LoopMode.SONG
                LoopMode.SONG -> LoopMode.OFF
            },
        )
    }

    fun setStemMode(mode: StemMode) {
        if (stemMode == mode) return
        stemMode = mode
        onStemChanged(mode)
        notifyChanged()
    }

    fun cycleStem() {
        setStemMode(
            when (stemMode) {
                StemMode.ORIGINAL -> StemMode.VOCALS
                StemMode.VOCALS -> StemMode.INSTRUMENTAL
                StemMode.INSTRUMENTAL -> StemMode.ORIGINAL
            },
        )
    }

    var durationMs: Long = 0
        private set
    var currentTrackId: String? = null
        private set
    var playQueue: List<String> = emptyList()
        private set

    fun setDuration(ms: Long?) {
        if (ms != null && ms > 0) durationMs = ms
    }

    fun rememberTrack(id: String?) {
        currentTrackId = id
    }

    fun rememberQueue(ids: List<String>) {
        playQueue = ids
    }

    fun notifyTrackChanged() {
        notifyChanged()
    }

    private fun notifyChanged() {
        listeners.forEach { it() }
    }
}
