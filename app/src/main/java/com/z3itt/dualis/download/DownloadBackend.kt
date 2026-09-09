package com.z3itt.dualis.download

import java.io.File

data class ResolvedItem(
    val title: String,
    val artist: String,
    val sourceUrl: String,
    val sourceKind: String,
    val ytdlpQuery: String,
    val coverUrl: String? = null,
)

data class PlaylistMeta(
    val title: String,
    val artist: String,
    val sourceUrl: String,
    val sourceKind: String,
    val coverUrl: String? = null,
)

data class ResolvedBatch(
    val playlist: PlaylistMeta?,
    val items: List<ResolvedItem>,
)

interface DownloadBackend {
    suspend fun resolve(input: String): ResolvedBatch
    suspend fun download(
        query: String,
        destFile: File,
        onProgress: (Float, String) -> Unit,
    ): File
}

class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
