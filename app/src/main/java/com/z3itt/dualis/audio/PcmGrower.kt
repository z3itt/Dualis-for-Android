package com.z3itt.dualis.audio

/**
 * Interleaved PCM without boxing. ArrayList<Float> for a 3 minute stereo track
 * can exceed the app heap even when the phone still has gigabytes free.
 */
class PcmGrower(initial: Int) {
    private var data = FloatArray(initial.coerceAtLeast(4096))
    var size: Int = 0
        private set

    fun addFloats(src: FloatArray, count: Int) {
        val n = count.coerceIn(0, src.size)
        ensure(size + n)
        src.copyInto(data, size, 0, n)
        size += n
    }

    fun addPcm16(src: ShortArray, count: Int) {
        val n = count.coerceIn(0, src.size)
        ensure(size + n)
        val dest = data
        var i = 0
        var o = size
        while (i < n) {
            dest[o] = src[i] / 32768f
            i += 1
            o += 1
        }
        size = o
    }

    fun toArray(): FloatArray = if (size == data.size) data else data.copyOf(size)

    private fun ensure(need: Int) {
        if (need <= data.size) return
        var cap = data.size
        while (cap < need) {
            cap = (cap * 2).coerceAtLeast(need)
        }
        data = data.copyOf(cap)
    }

    companion object {
        fun estimatedSamples(durationUs: Long, sampleRate: Int, channels: Int): Int {
            val rate = sampleRate.coerceAtLeast(8_000)
            val ch = channels.coerceAtLeast(1)
            if (durationUs <= 0L) return rate * ch * 30
            val samples = (durationUs / 1_000_000.0 * rate * ch).toInt()
            return samples.coerceIn(rate * ch, rate * ch * 60 * 20) + rate * ch
        }
    }
}
