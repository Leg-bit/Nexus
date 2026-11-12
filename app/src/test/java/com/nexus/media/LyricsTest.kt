package com.nexus.media

import com.nexus.media.player.audio.LyricLine
import com.nexus.media.player.audio.LyricsRepository
import com.nexus.media.player.audio.estimateTimings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test fun estimatedTimingsAreIncreasingAndInsideTheSong() {
        val lines = List(10) { LyricLine(-1, "baris nomor $it dengan teks") }
        val out = estimateTimings(lines, 200_000)
        assertEquals(10, out.size)
        assertTrue(out.zipWithNext().all { (a, b) -> a.timeMs < b.timeMs })
        assertTrue(out.first().timeMs > 0 && out.last().timeMs < 200_000)
    }

    @Test fun cleansTitles() {
        assertEquals("Merry Christmas Please Don't Call", LyricsRepository.cleanTitle("01 - Merry Christmas Please Don't Call (Official Audio)"))
        assertEquals("Song Name", LyricsRepository.cleanTitle("Song_Name [Lyrics]"))
    }

    @Test fun cleansArtists() {
        assertNull(LyricsRepository.cleanArtist("Artis tidak diketahui"))
        assertEquals("Chappell Roan", LyricsRepository.cleanArtist("Chappell Roan feat. Someone"))
        assertEquals("A", LyricsRepository.cleanArtist("A, B"))
    }
}

class LyricsParserTest {
    @Test fun mergesBilingualLinesAndAppliesOffset() {
        val l = com.nexus.media.player.audio.parseLyrics("[offset:500]\n[00:10.00]こんにちは\n[00:10.00]Halo\n[00:20.00]Line")
        assertEquals(2, l.size)
        assertEquals(9500L, l[0].timeMs)
        assertEquals("こんにちは\nHalo", l[0].text)
    }

    @Test fun stripsWordTags() {
        val l = com.nexus.media.player.audio.parseLyrics("[00:01.00]<00:01.00>Hello <00:01.50>world")
        assertEquals("Hello world", l[0].text)
    }

    @Test fun detectsRtl() {
        assertTrue(com.nexus.media.player.audio.isRtlText("مرحبا بالعالم"))
        assertTrue(!com.nexus.media.player.audio.isRtlText("Hello"))
    }

    @Test fun decodesUtf16WithBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Halo".toByteArray(Charsets.UTF_16LE)
        assertEquals("Halo", com.nexus.media.player.audio.decodeLyricsBytes(bytes))
    }
}
