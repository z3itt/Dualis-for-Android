package com.z3itt.dualis.domain.playback

/**
 * Playback list / shuffle / loop semantics from desktop `src/store/app.ts`.
 */
object PlaybackRules {
    fun shuffledIds(ids: List<String>, random: (Int) -> Int = { (Math.random() * it).toInt() }): List<String> {
        val copy = ids.toMutableList()
        for (i in copy.lastIndex downTo 1) {
            val j = random(i + 1)
            val tmp = copy[i]
            copy[i] = copy[j]
            copy[j] = tmp
        }
        return copy
    }

    fun rotateTo(ids: List<String>, startId: String?): List<String> {
        if (startId == null) return ids
        val index = ids.indexOf(startId)
        if (index <= 0) return ids
        return ids.drop(index) + ids.take(index)
    }

    fun mergeShuffleOrder(order: List<String>, ids: List<String>): List<String> {
        val keep = order.filter { it in ids }
        val missing = ids.filter { it !in keep }
        if (missing.isEmpty()) return keep
        return keep + shuffledIds(missing)
    }

    fun nextInOrder(ids: List<String>, currentId: String?, delta: Int, wrap: Boolean): String? {
        if (ids.isEmpty()) return null
        val index = if (currentId != null) ids.indexOf(currentId) else -1
        if (index < 0) return if (wrap) ids.first() else null
        val next = index + delta
        if (next in ids.indices) return ids[next]
        if (!wrap) return null
        return if (delta > 0) ids.first() else ids.last()
    }

    fun playbackIds(
        readyIds: List<String>,
        queue: List<String>,
        currentId: String?,
        currentPlaylistId: String?,
        playlistReadyIds: List<String>,
        lockedContext: String,
    ): Pair<List<String>, String> {
        if (lockedContext.startsWith("playlist:")) {
            if (playlistReadyIds.isNotEmpty()) return playlistReadyIds to lockedContext
        }
        if (lockedContext == "library") return readyIds to "library"
        if (lockedContext == "queue") {
            val queued = queue.filter { it in readyIds }
            if (queued.isNotEmpty()) return queued to "queue"
        }
        if (!currentPlaylistId.isNullOrBlank() && playlistReadyIds.isNotEmpty()) {
            return playlistReadyIds to "playlist:$currentPlaylistId"
        }
        val queued = queue.filter { it in readyIds }
        if (queued.isNotEmpty()) return queued to "queue"
        return readyIds to "library"
    }
}
