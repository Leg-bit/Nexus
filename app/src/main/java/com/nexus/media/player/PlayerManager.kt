package com.nexus.media.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.compose.ui.graphics.toArgb
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nexus.media.data.local.database.NexusDatabase
import com.nexus.media.data.local.entities.MediaEntity
import com.nexus.media.data.local.entities.PlaybackHistoryEntity
import com.nexus.media.data.preferences.UserPreferences
import com.nexus.media.player.audio.EqualizerManager
import com.nexus.media.player.service.PlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val current: MediaEntity? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MediaEntity> = emptyList(),
    val currentIndex: Int = -1
)

/** Single audio ExoPlayer shared by the UI and PlaybackService (MediaSession). */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerManager(
    private val ctx: Context,
    private val db: NexusDatabase,
    private val prefs: UserPreferences,
    private val eq: EqualizerManager,
    private val scope: CoroutineScope,
    private val dac: com.nexus.media.player.audio.UsbDacManager
) {
    val player: ExoPlayer = ExoPlayer.Builder(ctx, androidx.media3.exoplayer.DefaultRenderersFactory(ctx).setEnableAudioFloatOutput(prefs.bitPerfect))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true // audio focus management
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var queueItems: List<MediaEntity> = emptyList()
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _sleepEndsAt = MutableStateFlow<Long?>(null)
    val sleepEndsAt: StateFlow<Long?> = _sleepEndsAt.asStateFlow()
    private var sleepJob: Job? = null
    private var headroom = 1f
    private var rgFactor = 1f
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** Tint for the media notification, taken from the current cover (dark enough for white text). */
    @Volatile var notifColor: Int = 0xFF1E3A8A.toInt()
    var onAccentChanged: (() -> Unit)? = null

    init {
        eq.onHeadroom = { f -> headroom = f; if (f <= 0.001f) applyVolume() else rampVolume() }
        applyVolume()
        player.shuffleModeEnabled = prefs.shuffle
        player.repeatMode = prefs.repeatMode
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) { publish() }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { id ->
                    scope.launch {
                        db.history().upsert(PlaybackHistoryEntity(mediaId = id, playbackPosition = 0, lastPlayed = System.currentTimeMillis()))
                    }
                }
                saveQueueState()
                updateAccent(mediaItem?.mediaId)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) { eq.attach(audioSessionId) }

            override fun onTracksChanged(tracks: Tracks) {
                rgFactor = if (prefs.replayGain && !prefs.bitPerfect) readReplayGain(tracks) else 1f
                applyVolume()
                applyBitPerfect()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) saveQueueState() }
        })
        eq.attach(player.audioSessionId)
    }

    private fun publish() {
        val idx = player.currentMediaItemIndex
        _state.value = PlayerUiState(
            current = queueItems.getOrNull(idx),
            isPlaying = player.isPlaying,
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode,
            queue = queueItems,
            currentIndex = idx
        )
    }

    private fun saveQueueState() {
        if (queueItems.isEmpty()) return
        prefs.lastQueue = queueItems.joinToString(",") { it.id }
        prefs.lastIndex = player.currentMediaItemIndex
        prefs.lastPosition = player.currentPosition
    }

    private fun toItem(m: MediaEntity): MediaItem = MediaItem.Builder()
        .setMediaId(m.id)
        .setUri(m.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(m.title)
                .setArtist(m.artist)
                .setAlbumTitle(m.album)
                .setArtworkUri(m.artworkUri?.let { Uri.parse(it) })
                .build()
        )
        .build()

    private fun ensureService() {
        // if the service was stopped (notification dismissed / app swiped away) the old controller is dead: reconnect
        val f = controllerFuture
        val alive = f != null && (!f.isDone || runCatching { f.get().isConnected }.getOrDefault(false))
        if (alive) return
        f?.let { MediaController.releaseFuture(it) }
        ctx.startService(Intent(ctx, PlaybackService::class.java))
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(ctx, token).buildAsync()
    }

    fun play(items: List<MediaEntity>, start: Int, startPosMs: Long = 0L) {
        if (items.isEmpty()) return
        ensureService()
        queueItems = items
        player.setMediaItems(items.map { toItem(it) }, start.coerceIn(items.indices), startPosMs)
        player.prepare()
        player.play()
        publish()
    }

    fun playShuffled(items: List<MediaEntity>) {
        if (items.isEmpty()) return
        setShuffle(true)
        play(items, items.indices.random())
    }

    fun addToQueue(item: MediaEntity) {
        if (queueItems.isEmpty()) { play(listOf(item), 0); return }
        queueItems = queueItems + item
        player.addMediaItem(toItem(item))
        publish()
    }

    fun playNext(item: MediaEntity) {
        if (queueItems.isEmpty()) { play(listOf(item), 0); return }
        val i = player.currentMediaItemIndex + 1
        queueItems = queueItems.toMutableList().apply { add(i, item) }
        player.addMediaItem(i, toItem(item))
        publish()
    }

    fun removeAt(i: Int) {
        if (i !in queueItems.indices) return
        queueItems = queueItems.toMutableList().apply { removeAt(i) }
        player.removeMediaItem(i)
        publish()
    }

    fun jumpTo(i: Int) { player.seekTo(i, 0L); player.play() }

    fun toggle() {
        ensureService()
        if (player.isPlaying) player.pause() else resume()
    }

    /** Starts playback from any state: restarts a finished queue and re-prepares an idle player. */
    fun resume() {
        if (player.mediaItemCount == 0) return
        when (player.playbackState) {
            Player.STATE_ENDED -> player.seekTo(0, 0L)
            Player.STATE_IDLE -> player.prepare()
        }
        player.play()
    }

    private fun updateAccent(id: String?) {
        val item = queueItems.firstOrNull { it.id == id } ?: return
        scope.launch {
            val bmp = com.nexus.media.ui.ArtworkCache.load(ctx, item)
            val c = if (bmp != null) com.nexus.media.ui.dominantColor(bmp) else com.nexus.media.core.theme.ElectricBlue
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(c.toArgb(), hsv)
            hsv[1] = hsv[1].coerceAtLeast(0.55f)
            hsv[2] = 0.40f
            notifColor = android.graphics.Color.HSVToColor(hsv)
            withContext(Dispatchers.Main) { onAccentChanged?.invoke() }
        }
    }

    private var lastPrevAt = 0L

    /** Next song (wraps to the first one) and always starts playing, like other players. */
    fun next() {
        if (player.mediaItemCount == 0) return
        ensureService()
        if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0, 0L)
        player.play()
    }

    /** Past 3 s: restart the song; press again within 1.5 s (or before 3 s): go to the previous song. */
    fun previous() {
        if (player.mediaItemCount == 0) return
        ensureService()
        val now = android.os.SystemClock.uptimeMillis()
        val again = now - lastPrevAt < 1500
        lastPrevAt = now
        if ((player.currentPosition > 3000 && !again) || !player.hasPreviousMediaItem()) player.seekTo(0)
        else player.seekToPreviousMediaItem()
        player.play()
    }
    fun seekTo(ms: Long) { player.seekTo(ms) }

    private var rampJob: Job? = null
    private fun target() = if (prefs.bitPerfect) 1f else (prefs.playerVolume * headroom * rgFactor).coerceIn(0f, 1f)

    /** Match the USB DAC mixer to the current track (Android 14+); older versions only get the high-quality settings. */
    fun applyBitPerfect() { dac.apply(player.audioFormat?.sampleRate ?: -1) }

    /** Called when the bit-perfect switch changes: drop/restore effects, volume scaling and the DAC mixer mode. */
    fun onBitPerfectChanged() {
        eq.attach(player.audioSessionId)
        rgFactor = if (prefs.replayGain && !prefs.bitPerfect) readReplayGain(player.currentTracks) else 1f
        applyVolume()
        applyBitPerfect()
    }

    /** ReplayGain: reads REPLAYGAIN_TRACK_GAIN from the file tags; only attenuates (never boosts above full volume). */
    private fun readReplayGain(tracks: Tracks): Float {
        val re = Regex("REPLAYGAIN_TRACK_GAIN\\D*?(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        for (g in tracks.groups) {
            if (g.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until g.length) {
                val md = g.getTrackFormat(i).metadata ?: continue
                for (k in 0 until md.length()) {
                    val db = re.find(md.get(k).toString())?.groupValues?.get(1)?.toFloatOrNull() ?: continue
                    return Math.pow(10.0, minOf(db, 0f) / 20.0).toFloat().coerceIn(0.05f, 1f)
                }
            }
        }
        return 1f
    }

    /** Instant volume (init, user volume slider, mute). */
    private fun applyVolume() { rampJob?.cancel(); player.volume = target() }

    /** Smooth volume change (~0.3 s) so equalizer changes never cause a sudden jump. */
    private fun rampVolume() {
        rampJob?.cancel()
        rampJob = scope.launch(Dispatchers.Main) {
            val from = player.volume
            val t = target()
            for (i in 1..10) { player.volume = from + (t - from) * i / 10f; delay(30) }
            player.volume = target()
        }
    }

    fun setVolume(v: Float) { prefs.playerVolume = v; applyVolume() }

    fun setShuffle(on: Boolean) { player.shuffleModeEnabled = on; prefs.shuffle = on }

    fun cycleRepeat() {
        val n = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = n
        prefs.repeatMode = n
    }

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val ms = minutes * 60_000L
        _sleepEndsAt.value = System.currentTimeMillis() + ms
        sleepJob = scope.launch {
            delay(ms)
            withContext(Dispatchers.Main) { player.pause() }
            _sleepEndsAt.value = null
        }
    }

    fun cancelSleepTimer() { sleepJob?.cancel(); _sleepEndsAt.value = null }

    /** Playback restoration: reload the last queue (paused) after the app restarts. */
    suspend fun restore() {
        if (player.mediaItemCount > 0) return
        val ids = prefs.lastQueue.split(",").filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        val byId = ids.chunked(500).flatMap { db.media().getMany(it) }.associateBy { it.id }
        val items = ids.mapNotNull { byId[it] }
        if (items.isEmpty()) return
        withContext(Dispatchers.Main) {
            queueItems = items
            player.setMediaItems(items.map { toItem(it) }, prefs.lastIndex.coerceIn(items.indices), prefs.lastPosition)
            player.prepare()
            publish()
        }
    }
}
