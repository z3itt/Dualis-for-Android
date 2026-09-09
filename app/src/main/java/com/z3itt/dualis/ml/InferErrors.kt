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
            "failed to compile",
        ).any { text.contains(it) }
    }

    fun isOom(message: String): Boolean =
        shouldFallbackToCpu(message) && message.lowercase().contains("memory")

    fun cpuFallbackMessage(message: String): String =
        if (isOom(message)) {
            "GPU memory exhausted, retrying on CPU"
        } else {
            "GPU inference failed, retrying on CPU"
        }
}
