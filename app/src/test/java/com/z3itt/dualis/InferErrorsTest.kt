package com.z3itt.dualis.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferErrorsTest {
    @Test
    fun oomDetectorCatchesCommonStrings() {
        assertTrue(InferErrors.isOom("CUDA_ERROR_MEMORY allocation failed"))
        assertTrue(InferErrors.isOom("vkAllocateMemory out of memory"))
        assertFalse(InferErrors.isOom("invalid input shape"))
    }

    @Test
    fun nnapiOrConvErrorTriggersCpuFallback() {
        val err = "Non-zero status code returned while running Conv node. Name:'Conv_0' Status Message: absl::container_internal::raw_hash_map<>::at"
        assertTrue(InferErrors.shouldFallbackToCpu(err))
        assertFalse(InferErrors.isOom(err))
        assertTrue(InferErrors.shouldFallbackToCpu("NNAPI failed to compile"))
        assertTrue(InferErrors.shouldFallbackToCpu("QNN SetupBackend failed for libQnnHtp.so"))
    }

    @Test
    fun jvmOutOfMemoryErrorTriggersCpuFallback() {
        val err = OutOfMemoryError("Failed to allocate a 268435456 byte allocation")
        assertTrue(InferErrors.shouldFallbackToCpu(err))
        assertEquals("GPU memory exhausted, retrying on CPU", InferErrors.cpuFallbackMessage(err))
    }
}
