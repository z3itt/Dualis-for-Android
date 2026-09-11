package com.z3itt.dualis.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/**
 * Writes stem WAVs into Music/Dualis so Files and music apps can open them.
 */
object StemExport {
    fun saveWav(context: Context, source: File, displayName: String, artist: String): Boolean {
        if (!source.isFile || source.length() < 64L) return false
        return if (Build.VERSION.SDK_INT >= 29) {
            saveMediaStore(context, source, displayName, artist)
        } else {
            saveLegacy(source, displayName)
        }
    }

    private fun saveMediaStore(
        context: Context,
        source: File,
        displayName: String,
        artist: String,
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, "Dualis")
            put(MediaStore.Audio.Media.TITLE, displayName.removeSuffix(".wav"))
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Dualis")
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> copy(source, out) } ?: return false
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun saveLegacy(source: File, displayName: String): Boolean {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Dualis")
        if (!dir.exists() && !dir.mkdirs()) return false
        val dest = File(dir, displayName)
        return try {
            dest.outputStream().use { out -> copy(source, out) }
            true
        } catch (_: Exception) {
            dest.delete()
            false
        }
    }

    private fun copy(source: File, out: OutputStream) {
        source.inputStream().use { input -> input.copyTo(out) }
        out.flush()
    }
}
