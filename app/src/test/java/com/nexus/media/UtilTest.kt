package com.nexus.media

import com.nexus.media.player.audio.parseLyrics
import com.nexus.media.ui.dec
import com.nexus.media.ui.enc
import com.nexus.media.ui.fmtTime
import com.nexus.media.ui.folderName
import org.junit.Assert.assertEquals
import org.junit.Test

class UtilTest {
    @Test fun formatsTime() {
        assertEquals("01:05", fmtTime(65_000))
        assertEquals("1:01:05", fmtTime(3_665_000))
        assertEquals("00:00", fmtTime(-5))
    }

    @Test fun encodesRouteArgs() {
        val s = "Musik/Album é & 100%"
        assertEquals(s, dec(enc(s)))
    }

    @Test fun folderNameIsLastSegment() {
        assertEquals("Music", folderName("/storage/emulated/0/Music/"))
    }

    @Test fun parsesSyncedLyrics() {
        val l = parseLyrics("[ar:x]\n[00:01.50]Hai\n[00:03.00]Dunia")
        assertEquals(2, l.size)
        assertEquals(1500L, l[0].timeMs)
        assertEquals("Dunia", l[1].text)
    }

    @Test fun parsesPlainLyrics() {
        val l = parseLyrics("baris satu\nbaris dua")
        assertEquals(2, l.size)
        assertEquals(-1L, l[0].timeMs)
    }
}
