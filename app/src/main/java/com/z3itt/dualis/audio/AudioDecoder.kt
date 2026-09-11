package com.z3itt.dualis.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

class AudioDecoder(private val context: Context) {
    fun decodePath(path: String): DecodedAudio {
        val file = File(path)
        return try {
            if (file.extension.equals("wav", ignoreCase = true)) {
                resample(WavIo.readWav(file), WavIo.TARGET_RATE)
            } else {
                decodeWithCodec(path)
            }
        } catch (err: OutOfMemoryError) {
            throw IllegalStateException("Not enough memory to decode this file. Try a shorter track.", err)
        }
    }

    fun extractCover(path: String, dest: File): Boolean {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val pic = retriever.embeddedPicture
            retriever.release()
            if (pic != null) {
                dest.writeBytes(pic)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun titleFromPath(path: String): String {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            retriever.release()
            title?.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension
        } catch (_: Exception) {
            File(path).nameWithoutExtension
        }
    }

    fun artistFromPath(path: String): String {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            retriever.release()
            artist?.takeIf { it.isNotBlank() } ?: "Local"
        } catch (_: Exception) {
            "Local"
        }
    }

    private fun decodeWithCodec(path: String): DecodedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No audio track in file")
        extractor.selectTrack(trackIndex)
        var format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Missing mime")
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
        val grower = PcmGrower(PcmGrower.estimatedSamples(durationUs, sampleRate, channelCount))
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        format = codec.outputFormat
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex) ?: continue
                        if (info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            outBuf.order(ByteOrder.nativeOrder())
                            when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val floats = FloatArray(info.size / 4)
                                    outBuf.asFloatBuffer().get(floats)
                                    grower.addFloats(floats, floats.size)
                                }
                                else -> {
                                    val shorts = ShortArray(info.size / 2)
                                    outBuf.asShortBuffer().get(shorts)
                                    grower.addPcm16(shorts, shorts.size)
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }

        val interleaved = grower.toArray()
        val frames = (interleaved.size / channelCount).coerceAtLeast(0)
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        for (i in 0 until frames) {
            left[i] = interleaved[i * channelCount]
            right[i] = if (channelCount > 1) interleaved[i * channelCount + 1] else left[i]
        }
        return resample(DecodedAudio(sampleRate, left, right), WavIo.TARGET_RATE)
    }

    private fun resample(audio: DecodedAudio, target: Int): DecodedAudio {
        if (audio.sampleRate == target) return audio
        val ratio = target.toDouble() / audio.sampleRate
        val outLen = (audio.left.size * ratio).roundToInt().coerceAtLeast(1)
        val left = FloatArray(outLen)
        val right = FloatArray(outLen)
        val last = audio.left.lastIndex.coerceAtLeast(0)
        if (audio.left.isEmpty()) return DecodedAudio(target, left, right)
        for (i in 0 until outLen) {
            val src = i / ratio
            val i0 = src.toInt().coerceIn(0, last)
            val i1 = (i0 + 1).coerceAtMost(last)
            val t = (src - i0).toFloat()
            left[i] = audio.left[i0] * (1 - t) + audio.left[i1] * t
            right[i] = audio.right[i0] * (1 - t) + audio.right[i1] * t
        }
        return DecodedAudio(target, left, right)
    }
}
