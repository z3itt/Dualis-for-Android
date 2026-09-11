package com.z3itt.dualis.ml

/**
 * Chunk 1 is NNAPI/CPU warmup. Later chunks slow down as the SoC throttles.
 * Pace from a short window after warmup so ETA tracks heat, not the first burst.
 */
class InferPace(
    private val window: Int = 6,
    private val warmupChunks: Int = 1,
) {
    private val samples = ArrayDeque<Float>()
    private var lastElapsed = 0f

    fun etaSeconds(chunkIdx: Int, elapsedSec: Float, remainingChunks: Int): Float? {
        val dt = (elapsedSec - lastElapsed).coerceAtLeast(0.001f)
        lastElapsed = elapsedSec
        if (chunkIdx <= warmupChunks) return null
        samples.addLast(dt)
        while (samples.size > window) samples.removeFirst()
        if (remainingChunks <= 0) return 0f
        val avg = samples.average().toFloat()
        return avg * remainingChunks
    }
}
