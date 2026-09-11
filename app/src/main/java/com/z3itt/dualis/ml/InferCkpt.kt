package com.z3itt.dualis.ml

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class InferCheckpoint(
    val modelId: String,
    val nSamples: Int,
    val chunkIdx: Int,
    val offset: Int,
    val left: FloatArray,
    val right: FloatArray,
)

/**
 * Survives process death mid-infer. Written atomically so a kill during save
 * falls back to the previous complete checkpoint.
 */
object InferCkpt {
    const val FILE_NAME = "infer-ckpt.bin"
    private const val MAGIC = 0x444C434B // DLCK
    private const val VERSION = 1

    fun file(destDir: File): File = File(destDir, FILE_NAME)

    fun shouldSave(chunkIdx: Int, interval: Int = InferGc.CHUNK_INTERVAL): Boolean =
        chunkIdx > 0 && chunkIdx % interval == 0

    fun save(destDir: File, ckpt: InferCheckpoint) {
        destDir.mkdirs()
        val target = file(destDir)
        val tmp = File(destDir, "$FILE_NAME.tmp")
        tmp.delete()
        DataOutputStream(FileOutputStream(tmp).buffered(256 * 1024)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(ckpt.modelId)
            out.writeInt(ckpt.nSamples)
            out.writeInt(ckpt.chunkIdx)
            out.writeInt(ckpt.offset)
            writeFloats(out, ckpt.left, ckpt.nSamples)
            writeFloats(out, ckpt.right, ckpt.nSamples)
            out.flush()
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun load(destDir: File, modelId: String, nSamples: Int): InferCheckpoint? {
        val file = file(destDir)
        if (!file.isFile || file.length() < 24L) return null
        return runCatching {
            DataInputStream(FileInputStream(file).buffered(256 * 1024)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return@use null
                val savedModel = input.readUTF()
                val savedSamples = input.readInt()
                val chunkIdx = input.readInt()
                val offset = input.readInt()
                if (savedModel != modelId || savedSamples != nSamples) return@use null
                if (chunkIdx <= 0 || offset <= 0 || offset >= nSamples) return@use null
                val left = FloatArray(nSamples)
                val right = FloatArray(nSamples)
                readFloats(input, left, nSamples)
                readFloats(input, right, nSamples)
                InferCheckpoint(savedModel, savedSamples, chunkIdx, offset, left, right)
            }
        }.getOrNull()
    }

    fun clear(destDir: File) {
        file(destDir).delete()
        File(destDir, "$FILE_NAME.tmp").delete()
    }

    private fun writeFloats(out: DataOutputStream, data: FloatArray, n: Int) {
        val count = minOf(n, data.size)
        for (i in 0 until count) out.writeFloat(data[i])
        repeat(n - count) { out.writeFloat(0f) }
    }

    private fun readFloats(input: DataInputStream, dest: FloatArray, n: Int) {
        val count = minOf(n, dest.size)
        for (i in 0 until count) dest[i] = input.readFloat()
    }
}
