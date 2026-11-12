package com.nexus.media.player.audio

import android.content.Context
import android.net.Uri
import com.nexus.media.data.local.entities.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Online lyrics lookup via LRCLIB (free, no API key), cached on disk.
 * Titles are cleaned ("(Official Video)", "Artist - Title", feat., track numbers) and several queries are tried;
 * a candidate is only accepted when title and duration match, and synced lyrics are preferred over plain text.
 */
object LyricsRepository {
    private const val DIR = "lyrics2"
    private const val PLAIN_RETRY_MS = 6 * 3600_000L

    suspend fun online(ctx: Context, item: MediaEntity, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, DIR)
        val synced = File(dir, "${item.id}.lrc")
        val plain = File(dir, "${item.id}.txt")
        if (!force) {
            if (synced.isFile) return@withContext synced.readText()
            // plain text is re-checked every few hours in case a synced version appears
            if (plain.isFile && System.currentTimeMillis() - plain.lastModified() < PLAIN_RETRY_MS) return@withContext plain.readText()
        }
        val found = runCatching { query(item) }.getOrNull()
        if (found != null) {
            dir.mkdirs()
            if (found.second) { synced.writeText(found.first); plain.delete() } else plain.writeText(found.first)
            return@withContext found.first
        }
        if (plain.isFile) plain.readText() else null
    }

    fun clearCache(ctx: Context) {
        File(ctx.cacheDir, DIR).deleteRecursively()
        File(ctx.cacheDir, "lyrics").deleteRecursively()
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "NexusMedia/0.4")
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }

    private class Cand(val title: String, val artist: String, val durationSec: Double, val synced: String, val plain: String)

    private fun nz(s: String) = if (s == "null") "" else s
    private fun cand(o: JSONObject) = Cand(
        o.optString("trackName"), o.optString("artistName"), o.optDouble("duration", 0.0),
        nz(o.optString("syncedLyrics")), nz(o.optString("plainLyrics"))
    )

    private fun words(s: String): Set<String> =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

    private fun wordSim(a: String, b: String): Double {
        val x = words(a)
        val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

    // character bigrams: works for Japanese/Chinese/Thai titles that have no spaces
    private fun bigrams(s: String): Set<String> {
        val t = s.lowercase().filter { it.isLetterOrDigit() }
        return when {
            t.isEmpty() -> emptySet()
            t.length < 2 -> setOf(t)
            else -> (0 until t.length - 1).map { t.substring(it, it + 2) }.toSet()
        }
    }

    private fun bigramSim(a: String, b: String): Double {
        val x = bigrams(a)
        val y = bigrams(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 2.0 * x.intersect(y).size / (x.size + y.size)
    }

    private fun sim(a: String, b: String): Double = maxOf(wordSim(a, b), bigramSim(a, b))

    fun cleanTitle(raw: String): String {
        var t = raw.replace('_', ' ')
        t = t.replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
        t = t.replace(Regex("(?i)\\b(official|lyrics?|lyric video|music video|video|audio|hd|hq|remastered|visualizer)\\b"), " ")
        t = t.replace(Regex("^\\s*\\d{1,3}\\s*[-._)]\\s*"), "")
        return t.replace(Regex("\\s+"), " ").trim().trim('-', ' ')
    }

    fun cleanArtist(raw: String): String? {
        if (raw.isBlank() || raw.startsWith("Artis tidak") || raw.contains("unknown", ignoreCase = true)) return null
        val first = raw.split(Regex("\\s*[,;&/]\\s*|\\s+(?i:feat\\.?|ft\\.?|x)\\s+"))[0].trim()
        return first.ifBlank { null }
    }

    private fun choose(cands: List<Cand>, title: String, artist: String?, durationMs: Long): Cand? {
        var best: Cand? = null
        var bestScore = 0.0
        for (c in cands) {
            if (c.synced.isBlank() && c.plain.isBlank()) continue
            val diff = abs(c.durationSec * 1000 - durationMs)
            val fit = when {
                diff <= 3000 -> 1.0
                diff <= 8000 -> 0.6
                diff <= 15000 -> 0.3
                else -> 0.0
            }
            if (fit == 0.0) continue
            val ts = sim(c.title, title)
            if (ts < 0.5) continue
            val score = ts * 2 + fit + (if (c.synced.isNotBlank()) 0.7 else 0.0) +
                (if (artist != null) sim(c.artist, artist) * 0.5 else 0.0)
            if (score > bestScore) { best = c; bestScore = score }
        }
        return best
    }

    private fun result(c: Cand): Pair<String, Boolean> =
        if (c.synced.isNotBlank()) c.synced to true else c.plain to false

    private fun query(item: MediaEntity): Pair<String, Boolean>? {
        var title = cleanTitle(item.title)
        var artist = cleanArtist(item.artist)
        if (artist == null && " - " in title) {          // "Artist - Title" style names
            val p = title.split(" - ", limit = 2)
            artist = cleanArtist(p[0].trim())
            title = p[1].trim()
        }
        if (title.isBlank()) return null
        val secs = item.duration / 1000
        val cands = mutableListOf<Cand>()

        if (artist != null) {
            get("https://lrclib.net/api/get?track_name=${Uri.encode(title)}&artist_name=${Uri.encode(artist)}&duration=$secs")
                ?.let { cands += cand(JSONObject(it)) }
            choose(cands, title, artist, item.duration)?.takeIf { it.synced.isNotBlank() }?.let { return result(it) }
        }
        val queries = buildList {
            if (artist != null) add("track_name=${Uri.encode(title)}&artist_name=${Uri.encode(artist)}")
            add("track_name=${Uri.encode(title)}")
            add("q=${Uri.encode(listOfNotNull(title, artist).joinToString(" "))}")
        }
        for (q in queries) {
            val body = get("https://lrclib.net/api/search?$q") ?: continue
            val arr = JSONArray(body)
            for (i in 0 until minOf(arr.length(), 20)) cands += cand(arr.getJSONObject(i))
            choose(cands, title, artist, item.duration)?.takeIf { it.synced.isNotBlank() }?.let { return result(it) }
        }
        return choose(cands, title, artist, item.duration)?.let { result(it) }
    }
}
