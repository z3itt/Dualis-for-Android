package com.z3itt.dualis.ml

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class ModelDownloader(private val client: OkHttpClient = defaultClient()) {
    fun ensureModel(modelsDir: File, spec: ModelSpec, onProgress: (Float, String) -> Unit): File {
        val dest = File(modelsDir, spec.filename)
        if (dest.isFile && dest.length() > 1_000_000) return dest
        val tmp = File(modelsDir, "${spec.filename}.part")
        var lastError: Exception? = null
        for (url in spec.urls) {
            try {
                onProgress(0.02f, "Downloading ${spec.name} (about 60–80 MB on first run)")
                download(url, tmp, onProgress)
                tmp.renameTo(dest)
                return dest
            } catch (err: Exception) {
                lastError = err
                tmp.delete()
            }
        }
        throw lastError ?: IllegalStateException("Could not download ${spec.name}")
    }

    private fun download(url: String, dest: File, onProgress: (Float, String) -> Unit) {
        dest.parentFile?.mkdirs()
        val request = Request.Builder().url(url).header("User-Agent", "Dualis/1.0.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            val body = response.body ?: error("Empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        copied += n
                        if (total > 0) {
                            val pct = (copied.toFloat() / total).coerceIn(0f, 1f)
                            onProgress(pct * 0.15f, "Downloading model ${copied / 1_000_000} / ${total / 1_000_000} MB")
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }
}
