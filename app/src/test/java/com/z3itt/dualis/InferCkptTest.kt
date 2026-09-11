package com.z3itt.dualis.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InferCkptTest {
    @Test
    fun roundTripRestoresOffsetAndSamples() {
        val dir = File.createTempFile("ckpt", "dir").apply {
            delete()
            mkdirs()
        }
        try {
            val ckpt = InferCheckpoint(
                modelId = "uvr-mdx-kara-2",
                nSamples = 4,
                chunkIdx = 8,
                offset = 3,
                left = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f),
                right = floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f),
            )
            InferCkpt.save(dir, ckpt)
            val loaded = InferCkpt.load(dir, "uvr-mdx-kara-2", 4)!!
            assertEquals(8, loaded.chunkIdx)
            assertEquals(3, loaded.offset)
            assertEquals(0.3f, loaded.left[2], 0.0001f)
            assertEquals(0.8f, loaded.right[3], 0.0001f)
            assertTrue(InferCkpt.shouldSave(8))
            assertNull(InferCkpt.load(dir, "kim-vocal-2", 4))
        } finally {
            dir.deleteRecursively()
        }
    }
}
