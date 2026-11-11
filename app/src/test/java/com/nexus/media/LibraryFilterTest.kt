package com.nexus.media

import com.nexus.media.data.local.entities.MediaEntity
import com.nexus.media.data.repository.LibraryFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {
    private fun song(path: String, dur: Long = 120_000, music: Boolean = true, title: String = "Lagu") =
        MediaEntity("a_1", "content://x", title, "A", "B", dur, "audio", null, path, 0, isMusic = music)

    private val f = LibraryFilter()

    @Test fun keepsNormalMusic() = assertTrue(f.passes(song("/storage/emulated/0/Music"), emptySet(), emptySet()))
    @Test fun dropsShortAudio() = assertFalse(f.passes(song("/storage/emulated/0/Music", dur = 5_000), emptySet(), emptySet()))
    @Test fun dropsDubbingKeyword() = assertFalse(f.passes(song("/storage/emulated/0/Download/ElevenLabs"), emptySet(), emptySet()))
    @Test fun dropsNonMusicFlag() = assertFalse(f.passes(song("/storage/emulated/0/Music", music = false), emptySet(), emptySet()))
    @Test fun dropsHiddenId() = assertFalse(f.passes(song("/storage/emulated/0/Music"), emptySet(), setOf("a_1")))
    @Test fun dropsHiddenFolder() = assertFalse(f.passes(song("/x"), setOf("/x"), emptySet()))
}
