package com.nexus.media.player.audio

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

private val tagRe = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
private val offsetRe = Regex("\\[offset:\\s*([+-]?\\d+)\\s*\\]", RegexOption.IGNORE_CASE)
private val wordTagRe = Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>")

/**
 * Parses .lrc (synced) or plain text in any language.
 *  - [offset:] tag is applied, enhanced-LRC word tags <mm:ss.xx> are stripped
 *  - lines sharing one timestamp (original + translation) are merged into one entry ("orig\ntranslation")
 *  - plain lines get timeMs = -1
 */
fun parseLyrics(raw0: String): List<LyricLine> {
    val raw = raw0.removePrefix("\uFEFF")
    val offset = offsetRe.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val out = mutableListOf<LyricLine>()
    for (line in raw.lines()) {
        val tags = tagRe.findAll(line).toList()
        val text = wordTagRe.replace(tagRe.replace(line, ""), "").trim()
        if (tags.isEmpty()) {
            if (text.isNotEmpty() && !text.startsWith("[")) out.add(LyricLine(-1, text))
        } else {
            for (t in tags) {
                val m = t.groupValues[1].toLong()
                val s = t.groupValues[2].toLong()
                val f = t.groupValues[3]
                val ms = if (f.isEmpty()) 0L else f.padEnd(3, '0').take(3).toLong()
                out.add(LyricLine(((m * 60 + s) * 1000 + ms - offset).coerceAtLeast(0L), text))
            }
        }
    }
    if (out.none { it.timeMs >= 0 }) return out
    val merged = mutableListOf<LyricLine>()
    for (l in out.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }) {
        val last = merged.lastOrNull()
        if (last != null && last.timeMs == l.timeMs && last.text.isNotEmpty() && l.text.isNotEmpty()) {
            merged[merged.lastIndex] = LyricLine(last.timeMs, last.text + "\n" + l.text)
        } else merged.add(l)
    }
    return merged
}

/** True when the first strong character is right-to-left (Arabic, Hebrew, Persian, Urdu...). */
fun isRtlText(s: String): Boolean {
    for (ch in s) {
        val d = Character.getDirectionality(ch)
        if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true
        if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false
    }
    return false
}

/** Decodes lyric files: UTF-8 (with/without BOM), UTF-16 (BOM), otherwise Windows-1252. */
fun decodeLyricsBytes(b: ByteArray): String {
    if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
    if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
    if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
    val dec = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    return try {
        dec.decode(java.nio.ByteBuffer.wrap(b)).toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        String(b, charset("windows-1252"))
    }
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
        if (f.isFile && f.canRead()) return runCatching { decodeLyricsBytes(f.readBytes()) }.getOrNull()
    }
    return null
}

/**
 * Plain lyrics (no timestamps) get estimated timings spread over the song, weighted by line length,
 * so the Spotify-style highlight animation still runs (approximately).
 */
fun estimateTimings(lines: List<LyricLine>, durationMs: Long): List<LyricLine> {
    if (lines.isEmpty() || durationMs <= 0) return lines
    val lead = minOf(15_000L, (durationMs * 0.07).toLong())
    val tail = (durationMs * 0.07).toLong()
    val span = (durationMs - lead - tail).coerceAtLeast(lines.size * 1500L)
    val weights = lines.map { maxOf(it.text.length, 10).toDouble() }
    val total = weights.sum()
    var acc = 0.0
    return lines.mapIndexed { i, l ->
        val t = lead + (acc / total * span).toLong()
        acc += weights[i]
        LyricLine(t, l.text)
    }
}
