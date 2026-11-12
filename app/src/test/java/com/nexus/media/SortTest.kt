package com.nexus.media

import com.nexus.media.data.local.entities.MediaEntity
import com.nexus.media.data.repository.SortSetting
import com.nexus.media.data.repository.naturalCompare
import com.nexus.media.data.repository.sortMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortTest {
    private fun m(id: String, title: String, dur: Long = 1000, added: Long = 0, size: Long = 0, artist: String = "") =
        MediaEntity(id, "content://x/$id", title, artist, "", dur, "audio", null, "/Music", added, size = size)

    @Test fun naturalOrderUnderstandsNumbers() {
        assertTrue(naturalCompare("Track 2", "Track 10") < 0)
        assertTrue(naturalCompare("abc", "ABD") < 0)
        assertEquals(0, naturalCompare("Same", "same"))
    }

    @Test fun sortsByNameBothWays() {
        val l = listOf(m("1", "b"), m("2", "A"), m("3", "c"))
        assertEquals(listOf("2", "1", "3"), sortMedia(l, SortSetting("name", true)).map { it.id })
        assertEquals(listOf("3", "1", "2"), sortMedia(l, SortSetting("name", false)).map { it.id })
    }

    @Test fun sortsByDateDurationSize() {
        val l = listOf(m("1", "x", dur = 30, added = 300, size = 5), m("2", "y", dur = 10, added = 100, size = 9), m("3", "z", dur = 20, added = 200, size = 1))
        assertEquals(listOf("1", "3", "2"), sortMedia(l, SortSetting("date", false)).map { it.id })
        assertEquals(listOf("2", "3", "1"), sortMedia(l, SortSetting("duration", true)).map { it.id })
        assertEquals(listOf("2", "1", "3"), sortMedia(l, SortSetting("size", false)).map { it.id })
    }

    @Test fun artistSortFallsBackToTitle() {
        val l = listOf(m("1", "b", artist = "X"), m("2", "a", artist = "X"), m("3", "c", artist = "A"))
        assertEquals(listOf("3", "2", "1"), sortMedia(l, SortSetting("artist", true)).map { it.id })
    }
}
