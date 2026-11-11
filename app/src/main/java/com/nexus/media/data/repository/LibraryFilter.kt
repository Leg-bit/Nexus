package com.nexus.media.data.repository

import com.nexus.media.data.local.entities.MediaEntity

/** Which media counts as "library": keeps random audio (voice dubbing, app cache, etc.) out of the music list. */
data class LibraryFilter(
    val minDurationSec: Int = 30,
    val excludeKeywords: String = "elevenlabs,dubbing,/android/",
    val onlyMusic: Boolean = true
) {
    fun keywords(): List<String> =
        excludeKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun passes(m: MediaEntity, hiddenFolders: Set<String>, hiddenIds: Set<String>): Boolean {
        if (m.folderPath in hiddenFolders || m.id in hiddenIds) return false
        val hay = (m.folderPath + "/" + m.title + "/" + m.album).lowercase()
        if (keywords().any { hay.contains(it) }) return false
        if (m.mediaType == "audio") {
            if (m.duration < minDurationSec * 1000L) return false
            if (onlyMusic && !m.isMusic) return false
        }
        return true
    }
}
