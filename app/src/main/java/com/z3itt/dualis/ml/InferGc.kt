package com.z3itt.dualis.ml

object InferGc {
    const val CHUNK_INTERVAL = 8
    const val LOW_FREE_BYTES = 96L * 1024L * 1024L

    fun heapFreeBytes(runtime: Runtime = Runtime.getRuntime()): Long {
        val used = runtime.totalMemory() - runtime.freeMemory()
        return runtime.maxMemory() - used
    }

    fun shouldCollect(chunkIdx: Int, heapFreeBytes: Long): Boolean {
        if (heapFreeBytes < LOW_FREE_BYTES) return true
        return chunkIdx > 0 && chunkIdx % CHUNK_INTERVAL == 0
    }

    fun maybeCollect(chunkIdx: Int) {
        if (shouldCollect(chunkIdx, heapFreeBytes())) {
            System.gc()
        }
    }
}
