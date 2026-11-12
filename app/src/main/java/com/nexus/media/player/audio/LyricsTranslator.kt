package com.nexus.media.player.audio

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TrResult(val lines: List<LyricLine>? = null, val message: String? = null, val busy: Boolean = false)

/** On-device lyric translation (ML Kit): detects the lyric language, then adds the translation under each line. */
object LyricsTranslator {
    private const val SEP = '\u0001'

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun translate(ctx: Context, id: String, lines: List<LyricLine>, target: String): TrResult =
        withContext(Dispatchers.IO) {
            if (lines.any { it.text.contains('\n') }) {
                return@withContext TrResult(message = "Lirik ini sudah dua bahasa, tidak perlu diterjemahkan.")
            }
            val texts = lines.map { it.text }
            val cache = File(ctx.cacheDir, "lyrics_tr/${id}_$target.txt")
            if (cache.isFile) {
                val saved = cache.readText().split(SEP)
                if (saved.size == texts.size) return@withContext TrResult(merge(lines, saved))
            }
            try {
                val sample = texts.joinToString(" ").take(800)
                val code = LanguageIdentification.getClient().identifyLanguage(sample).await()
                if (code == "und") return@withContext TrResult(message = "Bahasa lirik tidak terdeteksi.")
                val src = TranslateLanguage.fromLanguageTag(code)
                    ?: return@withContext TrResult(message = "Bahasa lirik ($code) belum didukung penerjemah.")
                if (src == target) return@withContext TrResult(message = "Lirik sudah berbahasa itu.")
                val translator = Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(target).build()
                )
                try {
                    translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                    val out = texts.map { if (it.isBlank()) "" else translator.translate(it).await().replace('\n', ' ') }
                    cache.parentFile?.mkdirs()
                    cache.writeText(out.joinToString(SEP.toString()))
                    TrResult(merge(lines, out))
                } finally {
                    translator.close()
                }
            } catch (e: Exception) {
                TrResult(message = "Gagal menerjemahkan: ${e.message ?: "periksa koneksi internet untuk mengunduh model bahasa"}")
            }
        }

    private fun merge(lines: List<LyricLine>, translated: List<String>): List<LyricLine> =
        lines.mapIndexed { i, l ->
            val t = translated.getOrNull(i).orEmpty().trim()
            if (t.isBlank() || t.equals(l.text, ignoreCase = true)) l else LyricLine(l.timeMs, l.text + "\n" + t)
        }

    fun clearCache(ctx: Context) { File(ctx.cacheDir, "lyrics_tr").deleteRecursively() }
}
