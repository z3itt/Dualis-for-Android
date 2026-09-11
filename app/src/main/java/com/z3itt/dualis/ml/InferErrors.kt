package com.z3itt.dualis.ml

object InferErrors {
    fun shouldFallbackToCpu(message: String): Boolean {
        val text = message.lowercase()
        return listOf(
            "out of memory",
            "oom",
            "cuda_error_memory",
            "failed to allocate",
            "cublas",
            "vkallocate",
            "dxgi_error",
            "enomem",
            "resource exhausted",
            "d3d12",
            "non-zero status code",
            "running conv node",
            "running gemm node",
            "raw_hash_map",
            "webgpu",
            "webgpu_buffer",
            "sequential_executor",
            "executekernel",
            "all inputs must be tensors",
            "nnapi",
            "qnn",
            "htp",
            "hexagon",
            "libqnn",
            "setupbackend",
            "failed to compile",
            "outofmemory",
        ).any { text.contains(it) }
    }

    fun isOom(message: String): Boolean {
        val text = message.lowercase()
        return shouldFallbackToCpu(message) &&
            (text.contains("memory") || text.contains("oom"))
    }

    fun shouldFallbackToCpu(error: Throwable): Boolean =
        error is OutOfMemoryError || shouldFallbackToCpu(error.message ?: error.toString())

    fun cpuFallbackMessage(error: Throwable): String =
        if (error is OutOfMemoryError) {
            "GPU memory exhausted, retrying on CPU"
        } else {
            cpuFallbackMessage(error.message ?: error.toString())
        }

    fun cpuFallbackMessage(message: String): String =
        if (isOom(message)) {
            "GPU memory exhausted, retrying on CPU"
        } else {
            "Accelerator failed, retrying on CPU"
        }
}
