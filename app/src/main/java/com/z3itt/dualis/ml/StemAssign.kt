package com.z3itt.dualis.ml

enum class PrimaryStem { VOCALS, INSTRUMENTAL }

/**
 * MDX vocal nets emit vocals. Karaoke nets emit accompaniment.
 * Residual is always mix minus the primary output.
 */
object StemAssign {
    fun vocalsFromPrimary(
        mixL: FloatArray,
        mixR: FloatArray,
        primaryL: FloatArray,
        primaryR: FloatArray,
        primaryStem: PrimaryStem,
    ): Pair<Pair<FloatArray, FloatArray>, Pair<FloatArray, FloatArray>> {
        val n = mixL.size
        val residualL = FloatArray(n)
        val residualR = FloatArray(n)
        for (i in 0 until n) {
            residualL[i] = mixL[i] - primaryL.getOrElse(i) { 0f }
            residualR[i] = mixR[i] - primaryR.getOrElse(i) { 0f }
        }
        return if (primaryStem == PrimaryStem.INSTRUMENTAL) {
            (residualL to residualR) to (primaryL.copyOf(n) to primaryR.copyOf(n))
        } else {
            (primaryL.copyOf(n) to primaryR.copyOf(n)) to (residualL to residualR)
        }
    }
}
