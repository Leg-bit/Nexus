package com.nexus.media.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.nexus.media.data.local.database.NexusDatabase
import com.nexus.media.data.local.entities.*
import com.nexus.media.data.preferences.UserPreferences
import com.nexus.media.media.scanner.MediaScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MediaRepository(
    private val ctx: Context,
    private val db: NexusDatabase,
    private val prefs: UserPreferences,
    private val scanner: MediaScanner,
    private val scope: CoroutineScope
) {
    private fun Flow<List<MediaEntity>>.visible(): Flow<List<MediaEntity>> =
        combine(this, prefs.hiddenFolders, prefs.hiddenIds, prefs.filter) { l, hf, hi, f ->
            l.filter { f.passes(it, hf, hi) }
        }

    val music: Flow<List<MediaEntity>> = db.media().observe("audio").visible()
    val videos: Flow<List<MediaEntity>> = db.media().observe("video").visible()
    val all: Flow<List<MediaEntity>> = db.media().observeAll().visible()
    val duplicates: Flow<List<MediaEntity>> = db.media().duplicates()
    fun search(q: String): Flow<List<MediaEntity>> =
        if (q.isBlank()) flowOf(emptyList()) else db.media().search(q.trim()).visible()

    val isScanning = MutableStateFlow(false)

    fun rescan() {
        if (!isScanning.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                val list = ArrayList<MediaEntity>()
                list += scanner.scanAudio()
                list += scanner.scanVideo()
                for (t in prefs.customFolders.value) {
                    runCatching { list += scanner.scanTree(Uri.parse(t)) }
                }
                db.media().replaceAll(list.distinctBy { it.id })
            } catch (e: Exception) {
                // keep the previous index when a scan fails
            } finally {
                isScanning.value = false
            }
        }
    }

    private var observer: ContentObserver? = null

    /** Automatic media discovery: rescan (debounced) when MediaStore changes. */
    fun startObserving() {
        if (observer != null) return
        val handler = Handler(Looper.getMainLooper())
        val run = Runnable { rescan() }
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(run)
                handler.postDelayed(run, 3000)
            }
        }
        observer = obs
        ctx.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, obs)
        ctx.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, obs)
    }

    // ---- playlists ----
    fun playlists() = db.playlists().observe()
    fun playlistMedia(id: Long) = db.playlists().observeMedia(id)

    suspend fun createPlaylist(name: String): Long {
        val n = System.currentTimeMillis()
        return db.playlists().insert(PlaylistEntity(name = name.trim(), createdAt = n, updatedAt = n))
    }

    suspend fun deletePlaylist(id: Long) {
        db.playlists().clearRefs(id)
        db.playlists().delete(id)
    }

    suspend fun addToPlaylist(pid: Long, mediaId: String) {
        val d = db.playlists()
        d.addRef(PlaylistMediaCrossRef(pid, mediaId, d.nextPos(pid)))
        d.touch(pid, System.currentTimeMillis())
    }

    suspend fun removeFromPlaylist(pid: Long, mediaId: String) = db.playlists().removeRef(pid, mediaId)
}
