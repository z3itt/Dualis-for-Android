package com.z3itt.dualis.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.z3itt.dualis.audio.DecodedAudio
import com.z3itt.dualis.audio.WavIo
import java.io.File
import java.nio.FloatBuffer

data class LoadedModel(
    val session: OrtSession,
    val env: OrtEnvironment,
    val epName: String,
    var config: ModelConfig,
    var architecture: Architecture,
    val inputName: String,
    val modelId: String,
)

data class SeparationResult(
    val vocalsPath: String,
    val instrumentalPath: String,
    val peaksPath: String,
    val durationMs: Long,
    val sampleRate: Int,
    val executionProvider: String,
)

class StemSeparator {
    fun loadModel(modelFile: File, spec: ModelSpec, preferNnapi: Boolean): LoadedModel {
        val env = OrtEnvironment.getEnvironment()
        if (preferNnapi) {
            try {
                return tryLoad(env, modelFile, spec, nnapi = true)
            } catch (err: Exception) {
                // fall through to CPU
            }
        }
        return tryLoad(env, modelFile, spec, nnapi = false)
    }

    private fun tryLoad(env: OrtEnvironment, modelFile: File, spec: ModelSpec, nnapi: Boolean): LoadedModel {
        val opts = OrtSession.SessionOptions()
        opts.setOptimizationLevel(
            if (nnapi) OrtSession.SessionOptions.OptLevel.BASIC_OPT
            else OrtSession.SessionOptions.OptLevel.ALL_OPT,
        )
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        opts.setIntraOpNumThreads(threads)
        var ep = "CPU"
        if (nnapi) {
            opts.addNnapi()
            ep = "NNAPI"
        }
        val session = env.createSession(modelFile.absolutePath, opts)
        val inputName = session.inputNames.firstOrNull() ?: "input"
        val (arch, cfg) = applyInputShape(session, spec)
        return LoadedModel(session, env, ep, cfg, arch, inputName, spec.id)
    }

    fun applyInputShape(session: OrtSession, spec: ModelSpec): Pair<Architecture, ModelConfig> {
        var cfg = spec.config
        var arch = spec.architecture
        val info = session.inputInfo[session.inputNames.firstOrNull()] ?: return arch to cfg
        val tensor = info.info as? TensorInfo ?: return arch to cfg
        val dims = tensor.shape
        when {
            dims.size == 4 && dims[1] == 4L && dims[2] > 8 && dims[3] > 8 -> {
                arch = Architecture.MDX
                if (dims[2] > 0) cfg = cfg.copy(dimF = dims[2].toInt())
                if (dims[3] > 0) cfg = cfg.copy(dimT = dims[3].toInt())
                cfg = if (spec.config.nFft >= cfg.dimF * 2) {
                    cfg.copy(nFft = spec.config.nFft)
                } else {
                    cfg.copy(nFft = cfg.dimF * 2)
                }
            }
            dims.size == 3 && dims[1] == 2L && dims[2] > 64 -> {
                arch = Architecture.ROFORMER
                cfg = cfg.copy(waveformLen = dims[2].toInt(), layout = WaveLayout.CHANNEL_FIRST)
            }
            dims.size == 3 && dims[2] == 2L && dims[1] > 64 -> {
                arch = Architecture.ROFORMER
                cfg = cfg.copy(waveformLen = dims[1].toInt(), layout = WaveLayout.CHANNEL_LAST)
            }
        }
        return arch to cfg
    }

    fun separateFile(
        model: LoadedModel,
        decoded: DecodedAudio,
        destDir: File,
        onProgress: (Float, String, Float?) -> Unit,
    ): SeparationResult {
        destDir.mkdirs()
        return when (model.architecture) {
            Architecture.ROFORMER -> runRoformer(model, decoded, destDir, onProgress)
            Architecture.MDX -> runMdx(model, decoded, destDir, onProgress)
        }
    }

    fun close(model: LoadedModel) {
        try {
            model.session.close()
        } catch (_: Exception) {
        }
    }

    private fun runMdx(
        model: LoadedModel,
        decoded: DecodedAudio,
        destDir: File,
        onProgress: (Float, String, Float?) -> Unit,
    ): SeparationResult {
        val cfg = model.config
        val engine = StftEngine(cfg.nFft, cfg.hop)
        val chunk = cfg.chunkSize()
        val step = ((chunk * (1f - cfg.overlap)).toInt()).coerceAtLeast(1)
        val fade = (chunk - step).coerceAtLeast(0)
        val nSamples = decoded.left.size
        val vocalL = FloatArray(nSamples)
        val vocalR = FloatArray(nSamples)
        val totalChunks = ((nSamples + step - 1) / step).coerceAtLeast(1)
        val started = System.nanoTime()
        onProgress(0.08f, "Running ONNX inference", null)

        var offset = 0
        var chunkIdx = 0
        val left = FloatArray(chunk)
        val right = FloatArray(chunk)
        val input = FloatArray(4 * cfg.dimF * cfg.dimT)
        while (offset < nSamples) {
            left.fill(0f)
            right.fill(0f)
            val avail = minOf(nSamples - offset, chunk)
            decoded.left.copyInto(left, 0, offset, offset + avail)
            decoded.right.copyInto(right, 0, offset, offset + avail)
            val specL = engine.stft(left)
            val specR = engine.stft(right)
            val nFrames = minOf(specL.size, cfg.dimT)
            input.fill(0f)
            fun idx(c: Int, f: Int, t: Int) = ((c * cfg.dimF) + f) * cfg.dimT + t
            for (t in 0 until nFrames) {
                for (f in 0 until cfg.dimF) {
                    val l = specL.getOrNull(t)?.getOrNull(f) ?: Complex32(0f, 0f)
                    val r = specR.getOrNull(t)?.getOrNull(f) ?: Complex32(0f, 0f)
                    input[idx(0, f, t)] = l.re
                    input[idx(1, f, t)] = l.im
                    input[idx(2, f, t)] = r.re
                    input[idx(3, f, t)] = r.im
                }
            }
            val output = runTensor(model, input, longArrayOf(1, 4, cfg.dimF.toLong(), cfg.dimT.toLong()))
            val shape = output.second
            val data = output.first
            val outL = Array(specL.size) { Array(engine.nBins()) { Complex32(0f, 0f) } }
            val outR = Array(specR.size) { Array(engine.nBins()) { Complex32(0f, 0f) } }
            val dimFOut = if (shape.size >= 3) shape[2].toInt() else cfg.dimF
            val dimTOut = if (shape.size >= 4) shape[3].toInt() else cfg.dimT
            fun outIdx(c: Int, f: Int, t: Int) = ((c * dimFOut) + f) * dimTOut + t
            val dimF = minOf(cfg.dimF, dimFOut)
            val dimT = minOf(nFrames, dimTOut)
            for (t in 0 until dimT) {
                for (f in 0 until dimF) {
                    val lr = data.getOrElse(outIdx(0, f, t)) { 0f }
                    val li = data.getOrElse(outIdx(1, f, t)) { 0f }
                    val rr = data.getOrElse(outIdx(2, f, t)) { 0f }
                    val ri = data.getOrElse(outIdx(3, f, t)) { 0f }
                    if (t < outL.size && f < outL[t].size) {
                        outL[t][f] = Complex32(lr, li) * cfg.compensate
                    }
                    if (t < outR.size && f < outR[t].size) {
                        outR[t][f] = Complex32(rr, ri) * cfg.compensate
                    }
                }
            }
            val recL = engine.istft(outL, chunk)
            val recR = engine.istft(outR, chunk)
            StftEngine.overlapAdd(vocalL, recL, offset, fade)
            StftEngine.overlapAdd(vocalR, recR, offset, fade)
            chunkIdx += 1
            val pct = 0.08f + 0.82f * (chunkIdx.toFloat() / totalChunks)
            val elapsed = (System.nanoTime() - started) / 1_000_000_000f
            val eta = (elapsed / chunkIdx) * (totalChunks - chunkIdx)
            onProgress(pct, "Separating chunk $chunkIdx/$totalChunks · ETA ${eta.toInt()}s", eta)
            offset += step
            System.gc()
        }
        return writeStems(destDir, decoded, vocalL, vocalR, model.epName, onProgress)
    }

    private fun runRoformer(
        model: LoadedModel,
        decoded: DecodedAudio,
        destDir: File,
        onProgress: (Float, String, Float?) -> Unit,
    ): SeparationResult {
        val cfg = model.config
        val chunk = cfg.waveformLen.coerceAtLeast(4096)
        val step = ((chunk * (1f - cfg.overlap)).toInt()).coerceAtLeast(1)
        val fade = (chunk - step).coerceAtLeast(0)
        val nSamples = decoded.left.size
        val vocalL = FloatArray(nSamples)
        val vocalR = FloatArray(nSamples)
        val totalChunks = ((nSamples + step - 1) / step).coerceAtLeast(1)
        val started = System.nanoTime()
        onProgress(0.08f, "Running Roformer inference", null)
        val left = FloatArray(chunk)
        val right = FloatArray(chunk)
        var offset = 0
        var chunkIdx = 0
        while (offset < nSamples) {
            left.fill(0f)
            right.fill(0f)
            val avail = minOf(nSamples - offset, chunk)
            decoded.left.copyInto(left, 0, offset, offset + avail)
            decoded.right.copyInto(right, 0, offset, offset + avail)
            val input = packWaveform(left, right, cfg.layout)
            val shape = when (cfg.layout) {
                WaveLayout.CHANNEL_FIRST -> longArrayOf(1, 2, chunk.toLong())
                WaveLayout.CHANNEL_LAST -> longArrayOf(1, chunk.toLong(), 2)
            }
            val (data, outShape) = runTensor(model, input, shape)
            val (recL, recR) = unpackWaveform(data, outShape, chunk, cfg.layout)
            StftEngine.overlapAdd(vocalL, recL, offset, fade)
            StftEngine.overlapAdd(vocalR, recR, offset, fade)
            chunkIdx += 1
            val pct = 0.08f + 0.82f * (chunkIdx.toFloat() / totalChunks)
            val elapsed = (System.nanoTime() - started) / 1_000_000_000f
            val eta = (elapsed / chunkIdx) * (totalChunks - chunkIdx)
            onProgress(pct, "Roformer chunk $chunkIdx/$totalChunks · ETA ${eta.toInt()}s", eta)
            offset += step
            System.gc()
        }
        return writeStems(destDir, decoded, vocalL, vocalR, model.epName, onProgress)
    }

    private fun runTensor(model: LoadedModel, input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val buffer = FloatBuffer.wrap(input)
        OnnxTensor.createTensor(model.env, buffer, shape).use { tensor ->
            model.session.run(mapOf(model.inputName to tensor)).use { result ->
                val value = result[0]
                val info = value.info as TensorInfo
                val data = (value.value as Array<*>).let { nested -> flattenFloats(nested) }
                    ?: error("Unexpected ONNX output")
                return data to info.shape
            }
        }
    }

    private fun flattenFloats(value: Any?): FloatArray? {
        when (value) {
            is FloatArray -> return value
            is Array<*> -> {
                val parts = value.mapNotNull { flattenFloats(it) }
                val total = parts.sumOf { it.size }
                val out = FloatArray(total)
                var i = 0
                for (p in parts) {
                    p.copyInto(out, i)
                    i += p.size
                }
                return out
            }
        }
        return null
    }

    private fun packWaveform(left: FloatArray, right: FloatArray, layout: WaveLayout): FloatArray {
        val n = left.size
        val input = FloatArray(n * 2)
        when (layout) {
            WaveLayout.CHANNEL_FIRST -> {
                left.copyInto(input, 0)
                right.copyInto(input, n)
            }
            WaveLayout.CHANNEL_LAST -> {
                for (i in 0 until n) {
                    input[i * 2] = left[i]
                    input[i * 2 + 1] = right[i]
                }
            }
        }
        return input
    }

    private fun unpackWaveform(output: FloatArray, shape: LongArray, chunk: Int, layout: WaveLayout): Pair<FloatArray, FloatArray> {
        val left = FloatArray(chunk)
        val right = FloatArray(chunk)
        val inferred = when {
            shape.size >= 3 && shape[1] == 2L -> WaveLayout.CHANNEL_FIRST
            shape.size >= 3 && shape.last() == 2L -> WaveLayout.CHANNEL_LAST
            else -> layout
        }
        when (inferred) {
            WaveLayout.CHANNEL_FIRST -> {
                for (i in 0 until chunk) {
                    left[i] = output.getOrElse(i) { 0f }
                    right[i] = output.getOrElse(chunk + i) { 0f }
                }
            }
            WaveLayout.CHANNEL_LAST -> {
                for (i in 0 until chunk) {
                    left[i] = output.getOrElse(i * 2) { 0f }
                    right[i] = output.getOrElse(i * 2 + 1) { 0f }
                }
            }
        }
        return left to right
    }

    private fun writeStems(
        destDir: File,
        decoded: DecodedAudio,
        vocalL: FloatArray,
        vocalR: FloatArray,
        epName: String,
        onProgress: (Float, String, Float?) -> Unit,
    ): SeparationResult {
        onProgress(0.92f, "Reconstructing stems", 0f)
        val n = decoded.left.size
        val vocalsL = vocalL.copyOf(n)
        val vocalsR = vocalR.copyOf(n)
        val instL = FloatArray(n)
        val instR = FloatArray(n)
        for (i in 0 until n) {
            instL[i] = decoded.left[i] - vocalsL[i]
            instR[i] = decoded.right[i] - vocalsR[i]
        }
        WavIo.peakNormalize(vocalsL, vocalsR, 0.98f)
        WavIo.peakNormalize(instL, instR, 0.98f)
        val vocalsPath = File(destDir, "vocals.wav")
        val instrumentalPath = File(destDir, "instrumental.wav")
        val peaksPath = File(destDir, "peaks.json")
        WavIo.writeStereoWav(vocalsPath, vocalsL, vocalsR, WavIo.TARGET_RATE)
        WavIo.writeStereoWav(instrumentalPath, instL, instR, WavIo.TARGET_RATE)
        WavIo.writePeaks(peaksPath, vocalsL, vocalsR, 160)
        onProgress(1f, "Stems ready", 0f)
        return SeparationResult(
            vocalsPath = vocalsPath.absolutePath,
            instrumentalPath = instrumentalPath.absolutePath,
            peaksPath = peaksPath.absolutePath,
            durationMs = decoded.durationMs(),
            sampleRate = WavIo.TARGET_RATE,
            executionProvider = epName,
        )
    }
}
