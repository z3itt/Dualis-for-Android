package com.z3itt.dualis.ml

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.cos

data class Complex32(var re: Float, var im: Float) {
    operator fun times(scale: Float) = Complex32(re * scale, im * scale)
}

/**
 * Matches torch.stft used by UVR MDX-Net: Hann (periodic), center=True, onesided.
 * Ported from desktop `dsp/stft.rs`.
 */
class StftEngine(private val nFft: Int, private val hop: Int) {
    private val window: FloatArray = hannPeriodic(nFft)
    private val fft = FloatFFT_1D(nFft.toLong())

    fun nBins(): Int = nFft / 2 + 1

    fun stft(samples: FloatArray): Array<Array<Complex32>> {
        val pad = nFft / 2
        val padded = reflectPad(samples, pad)
        val nFrames = if (padded.size < nFft) 0 else 1 + (padded.size - nFft) / hop
        val nBins = nBins()
        val frames = Array(nFrames) { Array(nBins) { Complex32(0f, 0f) } }
        val input = FloatArray(nFft)
        for (i in 0 until nFrames) {
            val start = i * hop
            for (n in 0 until nFft) {
                input[n] = padded[start + n] * window[n]
            }
            fft.realForward(input)
            unpackOnesided(input, frames[i])
        }
        return frames
    }

    fun istft(frames: Array<Array<Complex32>>, outLen: Int): FloatArray {
        val pad = nFft / 2
        val paddedLen = (outLen + 2 * pad).coerceAtLeast(nFft)
        val acc = FloatArray(paddedLen)
        val windowSum = FloatArray(paddedLen)
        val spectrum = FloatArray(nFft)
        val scale = 1f / nFft
        val nBins = nBins()
        for ((i, frame) in frames.withIndex()) {
            packOnesided(frame, spectrum, nBins)
            sanitizePacked(spectrum)
            fft.realInverse(spectrum, false)
            val start = i * hop
            for (n in 0 until nFft) {
                val idx = start + n
                if (idx >= acc.size) break
                val w = window[n]
                acc[idx] += spectrum[n] * w * scale
                windowSum[idx] += w * w
            }
        }
        for (i in acc.indices) {
            if (windowSum[i] > 1e-8f) acc[i] /= windowSum[i]
        }
        val end = (pad + outLen).coerceAtMost(acc.size)
        return acc.copyOfRange(pad, end)
    }

    companion object {
        fun overlapAdd(dst: FloatArray, src: FloatArray, offset: Int, fade: Int) {
            for (i in src.indices) {
                val idx = offset + i
                if (idx >= dst.size) break
                if (fade > 0 && i < fade && offset > 0) {
                    val t = i.toFloat() / fade
                    dst[idx] = dst[idx] * (1 - t) + src[i] * t
                } else {
                    dst[idx] = src[i]
                }
            }
        }

        private fun hannPeriodic(n: Int): FloatArray =
            FloatArray(n) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / n)).toFloat() }

        private fun reflectPad(samples: FloatArray, pad: Int): FloatArray {
            if (samples.isEmpty()) return FloatArray(pad * 2)
            val out = FloatArray(samples.size + pad * 2)
            for (i in 0 until pad) out[i] = samples[reflectIndex(i, samples.size, true)]
            samples.copyInto(out, pad)
            for (i in 0 until pad) {
                out[pad + samples.size + i] = samples[reflectIndex(i, samples.size, false)]
            }
            return out
        }

        private fun reflectIndex(i: Int, len: Int, front: Boolean): Int {
            if (len == 1) return 0
            return if (front) {
                (i + 1) % (len - 1)
            } else {
                len - 2 - (i % (len - 1))
            }
        }

        /** JTransforms realForward packing for even n. */
        private fun unpackOnesided(packed: FloatArray, dest: Array<Complex32>) {
            val n = packed.size
            dest[0] = Complex32(packed[0], 0f)
            val nyquist = n / 2
            dest[nyquist] = Complex32(packed[1], 0f)
            for (k in 1 until nyquist) {
                dest[k] = Complex32(packed[2 * k], packed[2 * k + 1])
            }
        }

        private fun packOnesided(frame: Array<Complex32>, dest: FloatArray, nBins: Int) {
            dest.fill(0f)
            dest[0] = frame.getOrNull(0)?.re ?: 0f
            val nyquist = dest.size / 2
            dest[1] = frame.getOrNull(nyquist)?.re ?: 0f
            for (k in 1 until nyquist) {
                val c = frame.getOrNull(k) ?: Complex32(0f, 0f)
                dest[2 * k] = c.re
                dest[2 * k + 1] = c.im
            }
        }

        private fun sanitizePacked(spectrum: FloatArray) {
            // DC imag is unused (packed[0] is Re DC). Nyquist imag unused (packed[1] is Re Nyquist).
            // Keep purely real by construction.
        }
    }
}
