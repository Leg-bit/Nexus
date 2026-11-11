package com.nexus.media.player.video

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.media3.common.VideoSize
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nexus.media.NexusApplication
import com.nexus.media.core.theme.NexusTheme
import com.nexus.media.data.local.entities.PlaybackHistoryEntity
import com.nexus.media.di.AppContainer
import com.nexus.media.ui.fmtTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    @Inject lateinit var container: AppContainer
    private var bgAudio by mutableStateOf(false)
    private var handedOff = false
    private var userRotated = false
    private var mediaId = ""
    private var inPip by mutableStateOf(false)

    private val subPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadSubtitle(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.player.player.pause()
        bgAudio = container.prefs.videoBgAudio
        val uri = intent.data
        if (uri == null) { finish(); return }
        mediaId = intent.getStringExtra("id").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            ).build()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        // Orientation follows the video: portrait clips open portrait, landscape clips open landscape.
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (!userRotated && videoSize.width > 0 && videoSize.height > 0) {
                    requestedOrientation = if (videoSize.height > videoSize.width)
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        })
        lifecycleScope.launch {
            val p = container.db.history().position(mediaId)
            if (p != null && p > 5000) player.seekTo(p)   // resume playback
            player.play()
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            NexusTheme {
                VideoScreen(
                    player = player,
                    title = title,
                    hideControls = inPip,
                    onBack = { finish() },
                    onPip = { enterPip() },
                    onPickSubtitle = { subPicker.launch(arrayOf("*/*")) },
                    uri = uri,
                    bgAudio = bgAudio,
                    onToggleBg = { bgAudio = !bgAudio; container.prefs.videoBgAudio = bgAudio },
                    onRotate = { rotateScreen() }
                )
            }
        }
    }

    private fun loadSubtitle(sub: Uri) {
        val name = DocumentFile.fromSingleUri(this, sub)?.name.orEmpty().lowercase()
        val mime = when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val cfg = MediaItem.SubtitleConfiguration.Builder(sub)
            .setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val cur = player.currentMediaItem ?: return
        val pos = player.currentPosition
        val was = player.playWhenReady
        player.setMediaItem(cur.buildUpon().setSubtitleConfigurations(listOf(cfg)).build(), pos)
        player.prepare()
        player.playWhenReady = was
    }

    private fun rotateScreen() {
        userRotated = true
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun enterPip() {
        val vs = player.videoSize
        val ratio = if (vs.width > 0 && vs.height > 0) Rational(vs.width, vs.height) else Rational(16, 9)
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::player.isInitialized && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    private fun savePosition() {
        if (!::player.isInitialized || mediaId.isEmpty()) return
        val pos = player.currentPosition
        val dur = player.duration
        val p = if (dur > 0 && pos > dur - 10_000) 0L else pos
        container.appScope.launch {
            container.db.history().upsert(
                PlaybackHistoryEntity(mediaId = mediaId, playbackPosition = p, lastPlayed = System.currentTimeMillis())
            )
        }
    }

    override fun onPause() {
        super.onPause()
        savePosition()
    }

    override fun onStart() {
        super.onStart()
        if (handedOff && ::player.isInitialized) {
            handedOff = false
            val pos = container.player.player.currentPosition
            container.player.player.pause()
            player.seekTo(pos)
            player.play()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!::player.isInitialized || isInPictureInPictureMode) return
        if (bgAudio && !isFinishing && player.isPlaying && mediaId.isNotEmpty()) {
            // Background audio: hand the video's audio track to the shared player + MediaSession notification.
            val pos = player.currentPosition
            player.pause()
            lifecycleScope.launch {
                val e = container.db.media().getMany(listOf(mediaId)).firstOrNull()
                if (e != null) {
                    handedOff = true
                    container.player.play(listOf(e), 0, pos)
                }
            }
        } else {
            player.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) player.release()
    }
}

@Composable
private fun VideoScreen(
    player: ExoPlayer,
    title: String,
    hideControls: Boolean,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onPickSubtitle: () -> Unit,
    uri: Uri,
    bgAudio: Boolean,
    onToggleBg: () -> Unit,
    onRotate: () -> Unit
) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var hud by remember { mutableStateOf<String?>(null) }
    var subsOn by remember { mutableStateOf(true) }
    var seekTarget by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableFloatStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var subMenu by remember { mutableStateOf(false) }

    // Timeline thumbnail preview while scrubbing
    val retriever = remember { MediaMetadataRetriever().also { r -> runCatching { r.setDataSource(ctx, uri) } } }
    val lock = remember { Mutex() }
    DisposableEffect(Unit) { onDispose { runCatching { retriever.release() } } }
    val preview by produceState<Bitmap?>(null, seeking, (seekPos / 1000).toInt()) {
        if (!seeking) {
            value = null
        } else {
            value = lock.withLock {
                withContext(Dispatchers.IO) {
                    runCatching {
                        retriever.getFrameAtTime(seekPos.toLong() * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { f ->
                            Bitmap.createScaledBitmap(f, 320, (320f * f.height / f.width).toInt().coerceAtLeast(1), true)
                        }
                    }.getOrNull()
                }
            }
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!seeking) pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) { delay(4000); showControls = false }
    }
    LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { o ->
                        val d = if (o.x < size.width / 2) -10_000L else 10_000L
                        player.seekTo((player.currentPosition + d).coerceIn(0L, player.duration.coerceAtLeast(0L)))
                        hud = if (d < 0) "-10 dtk" else "+10 dtk"
                    }
                )
            }
            .pointerInput(Unit) {
                var left = true
                var acc = 0f
                detectVerticalDragGestures(
                    onDragStart = { o -> left = o.x < size.width / 2; acc = 0f },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val delta = -dy / size.height
                        if (left) {
                            val lp = act.window.attributes
                            val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                            lp.screenBrightness = (cur + delta).coerceIn(0.02f, 1f)
                            act.window.attributes = lp
                            hud = "Kecerahan ${(lp.screenBrightness * 100).toInt()}%"
                        } else {
                            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            acc += delta * max
                            val steps = acc.toInt()
                            if (steps != 0) {
                                acc -= steps
                                val v = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                hud = "Volume ${v * 100 / max}%"
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var acc = 0f
                var start = 0L
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f; start = player.currentPosition },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        acc += dx
                        val total = player.duration.coerceAtLeast(0L)
                        seekTarget = (start + (acc / size.width * 90_000).toLong()).coerceIn(0L, total)
                        hud = fmtTime(seekTarget)
                    },
                    onDragEnd = { player.seekTo(seekTarget) }
                )
            }
    ) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; this.player = player } },
            update = { it.resizeMode = resize },
            modifier = Modifier.fillMaxSize()
        )

        hud?.let {
            Text(
                it, color = Color.White,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        AnimatedVisibility(visible = showControls && !hideControls, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White) }
                    Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleBg) {
                        Icon(Icons.Rounded.Headphones, "Audio latar", tint = if (bgAudio) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    Box {
                        IconButton(onClick = { subMenu = true }) { Icon(Icons.Rounded.Subtitles, "Subtitle", tint = Color.White) }
                        DropdownMenu(expanded = subMenu, onDismissRequest = { subMenu = false }) {
                            DropdownMenuItem(text = { Text("Muat file subtitle") }, onClick = { subMenu = false; onPickSubtitle() })
                            DropdownMenuItem(
                                text = { Text(if (subsOn) "Matikan subtitle" else "Hidupkan subtitle") },
                                onClick = {
                                    subMenu = false
                                    subsOn = !subsOn
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn).build()
                                }
                            )
                        }
                    }
                    Box {
                        TextButton(onClick = { speedMenu = true }) { Text("${speed}x", color = Color.White) }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                                DropdownMenuItem(text = { Text("${s}x") }, onClick = {
                                    speed = s; player.setPlaybackSpeed(s); speedMenu = false
                                })
                            }
                        }
                    }
                    IconButton(onClick = {
                        resize = when (resize) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        hud = when (resize) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                            else -> "Stretch"
                        }
                    }) { Icon(Icons.Rounded.AspectRatio, "Rasio", tint = Color.White) }
                    IconButton(onClick = onRotate) { Icon(Icons.Rounded.ScreenRotation, "Putar layar", tint = Color.White) }
                    IconButton(onClick = onPip) { Icon(Icons.Rounded.PictureInPictureAlt, "PiP", tint = Color.White) }
                }
                }
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) {
                        Icon(Icons.Rounded.Replay10, "Mundur", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    FilledIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(64.dp)) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/Jeda", Modifier.size(36.dp))
                    }
                    IconButton(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.coerceAtLeast(0))) }) {
                        Icon(Icons.Rounded.Forward10, "Maju", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
                preview?.let { b ->
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(b.asImageBitmap(), null, Modifier.width(160.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                        Text(fmtTime(seekPos.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtTime(if (seeking) seekPos.toLong() else pos), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = if (seeking) seekPos else pos.toFloat().coerceAtMost(maxOf(dur, 1L).toFloat()),
                        onValueChange = { seeking = true; seekPos = it },
                        onValueChangeFinished = { player.seekTo(seekPos.toLong()); pos = seekPos.toLong(); seeking = false },
                        valueRange = 0f..maxOf(dur, 1L).toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(fmtTime(dur), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
