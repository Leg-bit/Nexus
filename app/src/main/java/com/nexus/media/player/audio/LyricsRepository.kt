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

/** Online lyrics lookup via LRCLIB (free, no API key). Results are cached on disk. */
object LyricsRepository {
    suspend fun online(ctx: Context, item: MediaEntity): String? = withContext(Dispatchers.IO) {
        val cache = File(ctx.cacheDir, "lyrics/${item.id}.txt")
        if (cache.isFile) return@withContext cache.readText()
        val raw = runCatching { query(item) }.getOrNull()
        if (raw != null) {
            cache.parentFile?.mkdirs()
            cache.writeText(raw)
        }
        raw
    }

    fun clearCache(ctx: Context) { File(ctx.cacheDir, "lyrics").deleteRecursively() }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "NexusMedia/0.3")
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }

    private fun pick(o: JSONObject): String? {
        val synced = o.optString("syncedLyrics")
        if (synced.isNotBlank() && synced != "null") return synced
        val plain = o.optString("plainLyrics")
        return if (plain.isNotBlank() && plain != "null") plain else null
    }

    private fun query(item: MediaEntity): String? {
        val artist = item.artist.takeIf { it.isNotBlank() && !it.startsWith("Artis tidak") }
        val title = Uri.encode(item.title)
        if (artist != null) {
            val u = "https://lrclib.net/api/get?track_name=$title&artist_name=${Uri.encode(artist)}&duration=${item.duration / 1000}"
            get(u)?.let { pick(JSONObject(it)) }?.let { return it }
        }
        val q = Uri.encode(listOfNotNull(item.title, artist).joinToString(" "))
        val arr = JSONArray(get("https://lrclib.net/api/search?q=$q") ?: return null)
        for (i in 0 until minOf(arr.length(), 6)) {
            val o = arr.getJSONObject(i)
            // accept only hits whose length matches the file (avoids wrong lyrics)
            if (abs(o.optDouble("duration", 0.0) * 1000 - item.duration) < 5000) pick(o)?.let { return it }
        }
        return null
    }
}
