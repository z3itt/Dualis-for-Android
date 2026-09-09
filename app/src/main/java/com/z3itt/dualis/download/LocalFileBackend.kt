package com.z3itt.dualis.download

import android.content.Context
import android.net.Uri
import com.z3itt.dualis.audio.AudioDecoder
import java.io.File

class LocalFileBackend(private val context: Context) {
    fun copyFromUri(uri: Uri, destFile: File): File {
        destFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open $uri")
        return destFile
    }

    fun copyFromPath(path: String, destFile: File): File {
        File(path).copyTo(destFile, overwrite = true)
        return destFile
    }

    fun metadata(path: String): Pair<String, String> {
        val decoder = AudioDecoder(context)
        return decoder.titleFromPath(path) to decoder.artistFromPath(path)
    }
}
