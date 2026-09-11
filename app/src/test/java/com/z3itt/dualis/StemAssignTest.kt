package com.z3itt.dualis.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class StemAssignTest {
    @Test
    fun vocalModelsKeepPrimaryAsVocals() {
        val mixL = floatArrayOf(1f, 1f)
        val mixR = floatArrayOf(1f, 1f)
        val primaryL = floatArrayOf(0.4f, 0.4f)
        val primaryR = floatArrayOf(0.4f, 0.4f)
        val (vocals, inst) = StemAssign.vocalsFromPrimary(
            mixL, mixR, primaryL, primaryR, PrimaryStem.VOCALS,
        )
        assertArrayEquals(floatArrayOf(0.4f, 0.4f), vocals.first, 0.0001f)
        assertArrayEquals(floatArrayOf(0.6f, 0.6f), inst.first, 0.0001f)
        assertArrayEquals(floatArrayOf(0.6f, 0.6f), inst.second, 0.0001f)
    }

    @Test
    fun karaokeModelsTreatPrimaryAsInstrumental() {
        val mixL = floatArrayOf(1f, 1f)
        val mixR = floatArrayOf(1f, 1f)
        val primaryL = floatArrayOf(0.7f, 0.7f)
        val primaryR = floatArrayOf(0.7f, 0.7f)
        val (vocals, inst) = StemAssign.vocalsFromPrimary(
            mixL, mixR, primaryL, primaryR, PrimaryStem.INSTRUMENTAL,
        )
        assertArrayEquals(floatArrayOf(0.3f, 0.3f), vocals.first, 0.0001f)
        assertArrayEquals(floatArrayOf(0.7f, 0.7f), inst.first, 0.0001f)
        assertArrayEquals(floatArrayOf(0.3f, 0.3f), vocals.second, 0.0001f)
    }
}
