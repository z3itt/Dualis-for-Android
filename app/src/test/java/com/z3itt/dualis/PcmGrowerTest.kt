package com.z3itt.dualis.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmGrowerTest {
    @Test
    fun addPcm16GrowsWithoutBoxing() {
        val grower = PcmGrower(4)
        grower.addPcm16(shortArrayOf(0, 32767, -32768, 16384, 0, 0, 0, 0), 4)
        assertEquals(4, grower.size)
        val out = grower.toArray()
        assertEquals(0f, out[0], 0.0001f)
        assertTrue(out[1] > 0.99f)
        assertTrue(out[2] < -0.99f)
    }

    @Test
    fun estimatedSamplesUsesDurationAndCapsLongFiles() {
        val threeMin = PcmGrower.estimatedSamples(180_000_000L, 44_100, 2)
        assertEquals(44_100 * 2 * 180 + 44_100 * 2, threeMin)
        val huge = PcmGrower.estimatedSamples(86_400_000_000L, 48_000, 2)
        assertEquals(48_000 * 2 * 60 * 20 + 48_000 * 2, huge)
    }
}
