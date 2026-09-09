package com.z3itt.dualis.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class AudioDecoder(private val context: Context) {
    fun decodePath(path: String): DecodedAudio {
        val file = File(path)
        if (file.extension.equals("wav", ignoreCase = true)) {
            val wav = WavIo.readWav(file)
            return resample(wav, WavIo.TARGET_RATE)
        }
        return decodeWithCodec(path)
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
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
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
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Missing mime")
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val pcm = ArrayList<Short>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
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
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex)!!
                if (info.size > 0) {
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val shorts = info.size / 2
                    val arr = ShortArray(shorts)
                    outBuf.order(ByteOrder.nativeOrder()).asShortBuffer().get(arr)
                    pcm.addAll(arr.toList())
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()

        val frames = pcm.size / channelCount
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        for (i in 0 until frames) {
            left[i] = pcm[i * channelCount] / 32768f
            right[i] = if (channelCount > 1) pcm[i * channelCount + 1] / 32768f else left[i]
        }
        return resample(DecodedAudio(sampleRate, left, right), WavIo.TARGET_RATE)
    }

    private fun resample(audio: DecodedAudio, target: Int): DecodedAudio {
        if (audio.sampleRate == target) return audio
        val ratio = target.toDouble() / audio.sampleRate
        val outLen = (audio.left.size * ratio).roundToInt().coerceAtLeast(1)
        val left = FloatArray(outLen)
        val right = FloatArray(outLen)
        val last = audio.left.lastIndex
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
