package com.z3itt.dualis

import com.z3itt.dualis.ml.InferGc
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferGcTest {
    @Test
    fun collectsEveryEighthChunk() {
        assertFalse(InferGc.shouldCollect(1, 512L * 1024 * 1024))
        assertFalse(InferGc.shouldCollect(7, 512L * 1024 * 1024))
        assertTrue(InferGc.shouldCollect(8, 512L * 1024 * 1024))
        assertTrue(InferGc.shouldCollect(16, 512L * 1024 * 1024))
    }

    @Test
    fun collectsWhenHeapIsLow() {
        assertTrue(InferGc.shouldCollect(1, 8L * 1024 * 1024))
        assertFalse(InferGc.shouldCollect(1, 256L * 1024 * 1024))
    }
}
