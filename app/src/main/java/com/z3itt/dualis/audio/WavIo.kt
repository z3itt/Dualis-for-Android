package com.z3itt.dualis.audio

data class DecodedAudio(
    val sampleRate: Int,
    val left: FloatArray,
    val right: FloatArray,
) {
    fun durationMs(): Long {
        if (sampleRate == 0) return 0
        return left.size.toLong() * 1000L / sampleRate
    }
}

object WavIo {
    const val TARGET_RATE = 44_100
    const val WAV_BITS: Short = 32

    fun writeStereoWav(file: java.io.File, left: FloatArray, right: FloatArray, sampleRate: Int) {
        val n = minOf(left.size, right.size)
        val dataBytes = n * 8
        java.io.FileOutputStream(file).use { out ->
            val header = java.io.ByteArrayOutputStream()
            val le = java.io.DataOutputStream(header)
            fun writeString(s: String) = s.forEach { le.writeByte(it.code) }
            fun writeInt(v: Int) {
                le.writeByte(v and 0xff)
                le.writeByte(v shr 8 and 0xff)
                le.writeByte(v shr 16 and 0xff)
                le.writeByte(v shr 24 and 0xff)
            }
            fun writeShort(v: Int) {
                le.writeByte(v and 0xff)
                le.writeByte(v shr 8 and 0xff)
            }
            writeString("RIFF")
            writeInt(36 + dataBytes)
            writeString("WAVE")
            writeString("fmt ")
            writeInt(16)
            writeShort(3) // IEEE float
            writeShort(2)
            writeInt(sampleRate)
            writeInt(sampleRate * 8)
            writeShort(8)
            writeShort(32)
            writeString("data")
            writeInt(dataBytes)
            out.write(header.toByteArray())
            val buf = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) {
                buf.clear()
                buf.putFloat(left[i])
                buf.putFloat(right[i])
                out.write(buf.array())
            }
        }
    }

    fun readWav(file: java.io.File): DecodedAudio {
        val bytes = file.readBytes()
        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        require(bytes.copyOfRange(0, 4).decodeToString() == "RIFF") { "Not a WAV file" }
        var offset = 12
        var channels = 2
        var rate = TARGET_RATE
        var bits = 16
        var format = 1
        var dataOff = -1
        var dataLen = 0
        while (offset + 8 <= bytes.size) {
            val id = bytes.copyOfRange(offset, offset + 4).decodeToString()
            val size = java.nio.ByteBuffer.wrap(bytes, offset + 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            if (id == "fmt ") {
                format = java.nio.ByteBuffer.wrap(bytes, offset + 8, 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt()
                channels = java.nio.ByteBuffer.wrap(bytes, offset + 10, 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt()
                rate = java.nio.ByteBuffer.wrap(bytes, offset + 12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                bits = java.nio.ByteBuffer.wrap(bytes, offset + 22, 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt()
            } else if (id == "data") {
                dataOff = offset + 8
                dataLen = size
                break
            }
            offset += 8 + size + (size % 2)
        }
        require(dataOff >= 0) { "WAV has no data chunk" }
        val sampleBytes = bits / 8
        val frame = sampleBytes * channels
        val n = dataLen / frame
        val left = FloatArray(n)
        val right = FloatArray(n)
        val buf = java.nio.ByteBuffer.wrap(bytes, dataOff, dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            when {
                format == 3 && bits == 32 -> {
                    left[i] = buf.float
                    right[i] = if (channels > 1) buf.float else left[i]
                }
                bits == 16 -> {
                    left[i] = buf.short / 32768f
                    right[i] = if (channels > 1) buf.short / 32768f else left[i]
                }
                bits == 32 && format == 1 -> {
                    left[i] = buf.int / 2147483648f
                    right[i] = if (channels > 1) buf.int / 2147483648f else left[i]
                }
                else -> error("Unsupported WAV format $format / $bits-bit")
            }
        }
        return DecodedAudio(rate, left, right)
    }

    fun peakNormalize(left: FloatArray, right: FloatArray, ceiling: Float) {
        var peak = 0f
        for (s in left) peak = maxOf(peak, kotlin.math.abs(s))
        for (s in right) peak = maxOf(peak, kotlin.math.abs(s))
        if (peak <= 1e-8f) return
        val gain = ceiling / peak
        if (kotlin.math.abs(gain - 1f) < 0.01f) return
        for (i in left.indices) left[i] *= gain
        for (i in right.indices) right[i] *= gain
    }

    fun computePeaks(left: FloatArray, right: FloatArray, bars: Int): List<Float> {
        val n = minOf(left.size, right.size)
        if (n == 0 || bars == 0) return List(bars.coerceAtLeast(1)) { 0.12f }
        val size = (n / bars).coerceAtLeast(1)
        return (0 until bars).map { i ->
            val start = i * size
            val end = (start + size).coerceAtMost(n)
            var peak = 0f
            for (idx in start until end) {
                peak = maxOf(peak, kotlin.math.abs(left[idx]), kotlin.math.abs(right[idx]))
            }
            peak.coerceIn(0f, 1f)
        }
    }

    fun writePeaks(file: java.io.File, left: FloatArray, right: FloatArray, bars: Int) {
        file.writeText(computePeaks(left, right, bars).joinToString(prefix = "[", postfix = "]"))
    }
}
