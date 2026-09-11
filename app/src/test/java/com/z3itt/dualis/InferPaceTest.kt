package com.z3itt.dualis.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InferPaceTest {
    @Test
    fun hidesWarmupThenTracksRecentChunks() {
        val pace = InferPace(window = 3, warmupChunks = 1)
        assertNull(pace.etaSeconds(1, 8f, 37))
        val eta = pace.etaSeconds(2, 10f, 36)
        assertEquals(72f, eta!!, 0.2f)
        val slower = pace.etaSeconds(3, 14f, 35)
        assertTrue(slower!! > eta)
    }
}
