package com.nexus.media.player.audio

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

private val tagRe = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

/** Parses .lrc (synced) or plain text. Plain lines get timeMs = -1. */
fun parseLyrics(raw: String): List<LyricLine> {
    val out = mutableListOf<LyricLine>()
    for (line in raw.lines()) {
        val tags = tagRe.findAll(line).toList()
        val text = tagRe.replace(line, "").trim()
        if (tags.isEmpty()) {
            if (text.isNotEmpty() && !text.startsWith("[")) out.add(LyricLine(-1, text))
        } else {
            for (t in tags) {
                val m = t.groupValues[1].toLong()
                val s = t.groupValues[2].toLong()
                val f = t.groupValues[3]
                val ms = if (f.isEmpty()) 0L else f.padEnd(3, '0').take(3).toLong()
                out.add(LyricLine((m * 60 + s) * 1000 + ms, text))
            }
        }
    }
    return if (out.any { it.timeMs >= 0 }) out.filter { it.timeMs >= 0 }.sortedBy { it.timeMs } else out
}

/** Looks for <song>.lrc / <song>.txt next to the audio file (works on Android 10 and below). */
fun loadSidecarLyrics(ctx: Context, mediaUri: Uri): String? {
    val path = runCatching {
        ctx.contentResolver.query(mediaUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: return null
    val base = path.substringBeforeLast('.')
    for (ext in listOf("lrc", "txt")) {
        val f = File("$base.$ext")
        if (f.isFile && f.canRead()) return runCatching { f.readText() }.getOrNull()
    }
    return null
}
