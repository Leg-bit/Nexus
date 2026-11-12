package com.nexus.media.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.animateScrollBy
import com.nexus.media.player.audio.decodeLyricsBytes
import com.nexus.media.player.audio.isRtlText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import com.nexus.media.core.theme.ElectricBlue
import com.nexus.media.data.local.entities.MediaEntity
import com.nexus.media.di.AppContainer
import com.nexus.media.player.PlayerUiState
import com.nexus.media.player.audio.LyricLine
import com.nexus.media.player.audio.LyricsTranslator
import com.nexus.media.player.audio.TrResult
import com.nexus.media.player.audio.LyricsRepository
import com.nexus.media.player.audio.estimateTimings
import com.nexus.media.player.audio.loadSidecarLyrics
import com.nexus.media.player.audio.parseLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(c: AppContainer, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val st by c.player.state.collectAsState()
    val sleepEnds by c.player.sleepEndsAt.collectAsState()
    val item = st.current
    var mode by remember { mutableStateOf(c.prefs.playerMode) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableFloatStateOf(0f) }

    // +1 when moving forward in the queue, -1 backwards: used for the slide direction on song change
    var prevIndex by remember { mutableIntStateOf(st.currentIndex) }
    val dir = remember(st.currentIndex) { if (st.currentIndex >= prevIndex) 1 else -1 }
    SideEffect { prevIndex = st.currentIndex }

    val bmp by produceState<Bitmap?>(null, item?.uri) { value = item?.let { ArtworkCache.load(ctx, it) } }
    val target = remember(bmp) { bmp?.let { dominantColor(it) } ?: ElectricBlue }
    val accent by animateColorAsState(target, tween(700), label = "accent")

    LaunchedEffect(Unit) {
        while (true) {
            if (!seeking) pos = c.player.player.currentPosition
            dur = c.player.player.duration.coerceAtLeast(0L)
            delay(300)
        }
    }

    // Outside Scaffold/Surface the default content colour is black -> force white so every icon is visible.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF060914), Color.Black)))
                .pointerInput(Unit) {}
        ) {
            if (mode == "lyrics") SnowFall(accent, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Tutup", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    Text("Sedang diputar", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.width(48.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("artwork" to "Cover", "vinyl" to "Vinyl", "lyrics" to "Lirik", "compact" to "Compact", "visual" to "Visual").forEach { (k, label) ->
                        FilterChip(
                            selected = mode == k,
                            onClick = { mode = k; c.prefs.playerMode = k },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color.White.copy(alpha = 0.75f),
                                selectedContainerColor = accent.copy(alpha = 0.45f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                if (item == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Belum ada lagu diputar", color = Color.White) }
                } else {
                    val cur: MediaEntity = item
                    AnimatedContent(
                        targetState = cur,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        transitionSpec = {
                            (fadeIn(tween(320)) + slideInHorizontally(tween(380)) { dir * it / 3 }) togetherWith
                                (fadeOut(tween(220)) + slideOutHorizontally(tween(380)) { -dir * it / 3 })
                        },
                        label = "track"
                    ) { track ->
                        BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            val s = minOf(maxWidth, maxHeight)
                            // read the constraints here: they are not reachable from inside Row's lambda
                            val halfSide = minOf((maxWidth - 14.dp) / 2, maxHeight - 64.dp).coerceAtLeast(80.dp)
                            when (mode) {
                                "vinyl" -> VinylDisc(track, st.isPlaying, accent, if (dur > 0) pos.toFloat() / dur else 0f, s)
                                "lyrics" -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        CoverArt(track, st.isPlaying, accent, halfSide / 0.92f)
                                        Spacer(Modifier.height(14.dp))
                                        TranslateControl(c, accent)
                                    }
                                    LyricsView(track, pos, c, accent, Modifier.weight(1f).fillMaxHeight())
                                }
                                "compact" -> CompactView(st, c, accent, Modifier.fillMaxSize())
                                "visual" -> VisualizerView(c, accent, Modifier.fillMaxSize())
                                else -> CoverArt(track, st.isPlaying, accent, s)
                            }
                        }
                    }
                    if (mode != "compact") {
                        AnimatedContent(
                            targetState = cur,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                            label = "title"
                        ) { t ->
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                                Text(t.artist, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SeekRow(pos, dur, accent, onSeek = { c.player.seekTo(it); pos = it })
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        BounceIconButton(onClick = { c.player.setShuffle(!st.shuffle) }) {
                            Icon(Icons.Rounded.Shuffle, "Acak", tint = if (st.shuffle) accent else Color.White.copy(alpha = 0.8f))
                        }
                        BounceIconButton(onClick = { c.player.previous() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        BounceFilledIconButton(
                            onClick = { c.player.toggle() },
                            containerColor = accent,
                            modifier = Modifier.size(76.dp).glowCircle(accent, 20.dp, 0.9f)
                        ) {
                            AnimatedContent(
                                targetState = st.isPlaying,
                                transitionSpec = { (scaleIn(tween(200)) + fadeIn(tween(200))) togetherWith (scaleOut(tween(150)) + fadeOut(tween(150))) },
                                label = "playPause"
                            ) { playing ->
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/Jeda", tint = Color.White, modifier = Modifier.size(42.dp))
                            }
                        }
                        BounceIconButton(onClick = { c.player.next() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        BounceIconButton(onClick = { c.player.cycleRepeat() }) {
                            Icon(
                                if (st.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Ulangi",
                                tint = if (st.repeat != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        BounceIconButton(onClick = { sheet = "queue" }) { Icon(Icons.Rounded.QueueMusic, "Antrian", tint = Color.White) }
                        BounceIconButton(onClick = { sheet = "sleep" }) {
                            Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (sleepEnds != null) accent else Color.White)
                        }
                        BounceIconButton(onClick = { sheet = "eq" }) { Icon(Icons.Rounded.Equalizer, "Equalizer", tint = Color.White) }
                    }
                                    }
            }
        }
    }

    when (sheet) {
        "queue" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("Antrian (${st.queue.size})", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            val ls = rememberLazyListState(initialFirstVisibleItemIndex = st.currentIndex.coerceAtLeast(0))
            LazyColumn(Modifier.heightIn(max = 480.dp), state = ls) {
                itemsIndexed(st.queue) { i, m ->
                    Row(Modifier.fillMaxWidth().clickable { c.player.jumpTo(i) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (i == st.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(m.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { c.player.removeAt(i) }) { Icon(Icons.Rounded.Close, "Hapus") }
                    }
                }
            }
        }
        "eq" -> EqualizerSheet(c) { sheet = null }
        "sleep" -> AlertDialog(
            onDismissRequest = { sheet = null },
            title = { Text("Sleep timer") },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
                        Text("$m menit", Modifier.fillMaxWidth().clickable { c.player.startSleepTimer(m); sheet = null }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = {
                if (sleepEnds != null) TextButton(onClick = { c.player.cancelSleepTimer(); sheet = null }) { Text("Matikan timer") }
            },
            dismissButton = { TextButton(onClick = { sheet = null }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun CoverArt(item: MediaEntity, playing: Boolean, accent: Color, size: Dp) {
    val scale by animateFloatAsState(if (playing) 1f else 0.9f, spring(dampingRatio = 0.6f), label = "cover")
    val shape = RoundedCornerShape(36.dp)
    Box(
        Modifier.size(size * 0.92f).graphicsLayer { scaleX = scale; scaleY = scale }
            .glow(accent, 32.dp, 36.dp, 1f)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
    ) {
        Thumb(item, Modifier.fillMaxSize(), shape)
    }
}

@Composable
private fun VinylDisc(item: MediaEntity, playing: Boolean, accent: Color, progress: Float, diameter: Dp) {
    var angle by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    // Spins up and slows down with inertia instead of starting/stopping instantly.
    LaunchedEffect(playing) {
        var last = withFrameNanos { it }
        while (playing || speed > 0.5f) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val target = if (playing) 60f else 0f
            speed += (target - speed) * (dt * 2.5f).coerceAtMost(1f)
            angle = (angle + speed * dt) % 360f
        }
        speed = 0f
    }
    val rest by animateFloatAsState(if (playing) 0f else 1f, tween(1000), label = "rest")
    val prog by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "prog")
    // the needle lifts and drops again whenever a new song starts (and when the vinyl view opens)
    val needleLift = remember { Animatable(0f) }
    LaunchedEffect(item.id) {
        needleLift.snapTo(0f)
        needleLift.animateTo(1f, tween(350))
        needleLift.animateTo(0f, tween(700))
    }

    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(0.86f).glowCircle(accent, 34.dp, 1f))

        // static drop shadow under the platter
        Canvas(Modifier.fillMaxSize(0.95f)) {
            val r = size.minDimension / 2
            drawCircle(Color.Black.copy(alpha = 0.55f), r * 1.03f, center + Offset(0f, 8.dp.toPx()))
        }
        // platter: metal rim + strobe dots (rotates with the record)
        Canvas(Modifier.fillMaxSize(0.95f).rotate(angle)) {
            val r = size.minDimension / 2
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF3A3F4D), Color(0xFF9AA1B1), Color(0xFF3A3F4D), Color(0xFF9AA1B1), Color(0xFF3A3F4D)),
                    center
                ),
                radius = r, center = center
            )
            drawCircle(Color(0xFF15171E), r * 0.965f, center)
            for (k in 0 until 72) {
                val a = k * 2f * Math.PI.toFloat() / 72f
                val big = k % 6 == 0
                drawCircle(
                    Color.White.copy(alpha = if (big) 0.7f else 0.25f),
                    if (big) 1.8.dp.toPx() else 1.dp.toPx(),
                    Offset(center.x + cos(a) * r * 0.925f, center.y + sin(a) * r * 0.925f)
                )
            }
        }
        // the record
        Box(Modifier.fillMaxSize(0.86f).rotate(angle), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Color(0xFF07070B), r, center)
                // faint colour sheen from the artwork accent
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.Transparent, accent.copy(alpha = 0.10f)), center = center, radius = r),
                    radius = r, center = center
                )
                var i = 0
                var rr = r * 0.975f
                while (rr > r * 0.37f) {
                    val a = 0.025f + ((i * 37) % 7) * 0.007f
                    drawCircle(Color.White.copy(alpha = a), rr, center, style = Stroke(0.7.dp.toPx()))
                    rr -= 2.0.dp.toPx()
                    i++
                }
                // gaps between tracks
                listOf(0.50f, 0.63f, 0.76f, 0.88f).forEach {
                    drawCircle(Color.Black.copy(alpha = 0.9f), r * it, center, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.06f), r * it + 2.dp.toPx(), center, style = Stroke(0.8.dp.toPx()))
                }
                drawCircle(Color.White.copy(alpha = 0.12f), r * 0.985f, center, style = Stroke(1.dp.toPx()))
                // label
                drawCircle(accent, r * 0.36f, center)
                drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.36f, center, style = Stroke(2.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.55f), r * 0.345f, center, style = Stroke(1.dp.toPx()))
            }
            Box(Modifier.fillMaxSize(0.58f).clip(CircleShape)) { Thumb(item, Modifier.fillMaxSize(), CircleShape) }
            Box(
                Modifier.size(13.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFEDEFF5), Color(0xFF7D8494))))
            )
        }
        // fixed light reflections (they do not rotate, like real vinyl)
        Canvas(Modifier.fillMaxSize(0.86f)) {
            val r = size.minDimension / 2
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.07f to Color.Transparent,
                    0.11f to Color.White.copy(alpha = 0.22f),
                    0.15f to Color.Transparent,
                    0.57f to Color.Transparent,
                    0.61f to Color.White.copy(alpha = 0.15f),
                    0.65f to Color.Transparent,
                    1f to Color.Transparent,
                    center = center
                ),
                radius = r, center = center
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), center = Offset(r * 0.55f, r * 0.45f), radius = r * 0.9f),
                radius = r, center = center
            )
        }
        // tonearm: the stylus follows the grooves from the outer edge towards the label as the song plays
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c0 = Offset(w * 0.5f, h * 0.5f)
            val rec = w * 0.43f
            val pivot = Offset(w * 0.90f, h * 0.09f)
            val armLen = w * 0.78f
            fun tipAt(deg: Float): Offset {
                val rad = Math.toRadians(deg.toDouble())
                return Offset(pivot.x - (armLen * sin(rad)).toFloat(), pivot.y + (armLen * cos(rad)).toFloat())
            }
            fun angleFor(dist: Float): Float {
                var best = 0f
                var bd = Float.MAX_VALUE
                var a = 0f
                while (a <= 70f) {
                    val t = tipAt(a)
                    val dd = abs(hypot(t.x - c0.x, t.y - c0.y) - dist)
                    if (dd < bd) { bd = dd; best = a }
                    a += 0.25f
                }
                return best
            }
            val outer = angleFor(rec * 0.93f)
            val playing = angleFor(rec * (0.93f - 0.37f * prog))
            // lifted and swung away when paused; lifts on each new song; sways slightly with the (slightly off-centre) record
            val sway = sin(Math.toRadians(angle.toDouble())).toFloat() * 0.55f * (1f - rest)
            val deg = playing - 26f * rest + 16f * needleLift.value + sway
            val tip = tipAt(deg)
            val dx = tip.x - pivot.x
            val dy = tip.y - pivot.y
            val len = sqrt(dx * dx + dy * dy)
            val ux = dx / len
            val uy = dy / len
            val lift = 1f + rest * 1.4f + needleLift.value * 1.2f
            val sh = Offset(4.dp.toPx() * lift, 7.dp.toPx() * lift)

            // arm shadow on the record
            drawLine(Color.Black.copy(alpha = 0.30f), pivot + sh, tip + sh, 6.dp.toPx(), StrokeCap.Round)
            // counterweight
            val back = Offset(pivot.x - ux * 40.dp.toPx(), pivot.y - uy * 40.dp.toPx())
            drawLine(Color(0xFF20232D), Offset(pivot.x - ux * 8.dp.toPx(), pivot.y - uy * 8.dp.toPx()), back, 22.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.16f), Offset(pivot.x - ux * 10.dp.toPx() - uy * 5.dp.toPx(), pivot.y - uy * 10.dp.toPx() + ux * 5.dp.toPx()),
                Offset(back.x - uy * 5.dp.toPx(), back.y + ux * 5.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            // base
            drawCircle(Color.Black.copy(alpha = 0.40f), 28.dp.toPx(), pivot + Offset(3.dp.toPx(), 5.dp.toPx()))
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFE9ECF3), Color(0xFF6F7686)), center = pivot, radius = 27.dp.toPx()),
                radius = 27.dp.toPx(), center = pivot
            )
            drawCircle(Color(0xFF14161D), 16.dp.toPx(), pivot)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFF4F6FA), Color(0xFF8A91A1)), center = pivot, radius = 11.dp.toPx()),
                radius = 11.dp.toPx(), center = pivot
            )
            drawCircle(Color(0xFF2B2E3A), 3.dp.toPx(), pivot)
            // arm tube
            val tubeEnd = Offset(tip.x - ux * 16.dp.toPx(), tip.y - uy * 16.dp.toPx())
            drawLine(
                brush = Brush.linearGradient(listOf(Color(0xFFF4F6FA), Color(0xFF9AA1B1)), start = pivot, end = tubeEnd),
                start = pivot, end = tubeEnd, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
            )
            drawLine(Color.White.copy(alpha = 0.55f), Offset(pivot.x - uy * 1.5.dp.toPx(), pivot.y + ux * 1.5.dp.toPx()),
                Offset(tubeEnd.x - uy * 1.5.dp.toPx(), tubeEnd.y + ux * 1.5.dp.toPx()), 1.2.dp.toPx(), StrokeCap.Round)
            // headshell, cartridge and stylus
            val shellEnd = Offset(tip.x + ux * 4.dp.toPx(), tip.y + uy * 4.dp.toPx())
            drawLine(Color(0xFF2B2E3A), tubeEnd, shellEnd, 15.dp.toPx(), StrokeCap.Round)
            drawLine(accent, Offset(tip.x - ux * 4.dp.toPx(), tip.y - uy * 4.dp.toPx()), Offset(tip.x + ux * 7.dp.toPx(), tip.y + uy * 7.dp.toPx()), 9.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.White, 2.2.dp.toPx(), Offset(tip.x + ux * 9.dp.toPx(), tip.y + uy * 9.dp.toPx()))
        }
    }
}

@Composable
private fun CompactView(st: PlayerUiState, c: AppContainer, accent: Color, modifier: Modifier) {
    val item = st.current ?: return
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(item, Modifier.size(88.dp).glow(accent, 14.dp, 18.dp, 0.9f), RoundedCornerShape(18.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
                Text(item.artist, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Berikutnya", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
        val offset = st.currentIndex + 1
        val upcoming = st.queue.drop(offset)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(upcoming) { i, m ->
                Row(Modifier.fillMaxWidth().clickable { c.player.jumpTo(offset + i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(m, Modifier.size(42.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                        Text(m.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

private data class LyricsUi(val loading: Boolean, val lines: List<LyricLine>, val estimated: Boolean = false)

/** Falling snow tinted with the song's accent colour (lyrics background). */
private class Flake(val x: Float, val speed: Float, val size: Float, val phase: Float, val sway: Float, val alpha: Float, val y0: Float)

@Composable
private fun SnowFall(accent: Color, modifier: Modifier = Modifier) {
    val flakes = remember {
        val r = java.util.Random(7)
        List(70) {
            Flake(
                x = r.nextFloat(), speed = 0.04f + r.nextFloat() * 0.09f, size = 1.2f + r.nextFloat() * 3.8f,
                phase = r.nextFloat() * 6.28f, sway = 0.01f + r.nextFloat() * 0.03f,
                alpha = 0.25f + r.nextFloat() * 0.6f, y0 = r.nextFloat()
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t += (now - last) / 1_000_000_000f
            last = now
        }
    }
    val tint = remember(accent) { lerp(accent, Color.White, 0.55f) }
    Canvas(modifier) {
        for (f in flakes) {
            val y = (f.y0 + t * f.speed) % 1f
            val x = f.x + sin(t * 0.8f + f.phase) * f.sway
            val c = Offset(x * size.width, y * size.height)
            val r = f.size.dp.toPx()
            drawCircle(tint.copy(alpha = f.alpha * 0.18f), r * 2.6f, c)   // soft halo
            drawCircle(tint.copy(alpha = f.alpha), r, c)
        }
    }
}

@Composable
private fun LyricsView(item: MediaEntity, posMs: Long, c: AppContainer, accent: Color, modifier: Modifier) {
    val ctx = LocalContext.current
    var uriStr by remember(item.id) { mutableStateOf(c.prefs.lyricsUri(item.id)) }
    var retry by remember(item.id) { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            c.prefs.setLyricsUri(item.id, u.toString())
            uriStr = u.toString()
        }
    }
    val ui by produceState(LyricsUi(true, emptyList()), uriStr, item.id, retry) {
        val saved = uriStr
        value = LyricsUi(true, emptyList())
        val parsed = withContext(Dispatchers.IO) {
            // order: picked file -> <lagu>.lrc beside the audio -> online (LRCLIB)
            var raw: String? = if (saved != null) {
                runCatching { decodeLyricsBytes(ctx.contentResolver.openInputStream(Uri.parse(saved))!!.use { it.readBytes() }) }.getOrNull()
            } else null
            if (raw == null) raw = loadSidecarLyrics(ctx, Uri.parse(item.uri))
            if (raw == null && c.prefs.onlineLyrics) raw = LyricsRepository.online(ctx, item, force = retry > 0)
            if (raw == null) emptyList() else parseLyrics(raw)
        }
        val plainOnly = parsed.isNotEmpty() && parsed.first().timeMs < 0
        value = LyricsUi(false, if (plainOnly) estimateTimings(parsed, item.duration) else parsed, plainOnly)
    }
    val trSet by c.prefs.translate.collectAsState()
    val tr by produceState(TrResult(), ui.lines, trSet.on, trSet.target, item.id) {
        if (!trSet.on || ui.lines.isEmpty()) { value = TrResult(); return@produceState }
        value = TrResult(busy = true)
        value = LyricsTranslator.translate(ctx, item.id, ui.lines, trSet.target)
    }
    if (ui.loading) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = accent)
            Spacer(Modifier.height(12.dp))
            Text("Mencari lirik…", color = Color.White.copy(alpha = 0.7f))
        }
    } else if (ui.lines.isEmpty()) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Lirik tidak ditemukan", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "Lagu tanpa tag/artis jelas sering tidak ada di database lirik online.",
                color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp)
            )
            Button(onClick = { retry++ }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) { Text("Cari online lagi") }
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Pilih file lirik (.lrc / .txt)", color = Color.White) }
        }
    } else {
        val lines = tr.lines ?: ui.lines
        val synced = lines.first().timeMs >= 0
        val cur = if (synced) lines.indexOfLast { it.timeMs <= posMs + 250 }.coerceAtLeast(0) else -1
        val ls = rememberLazyListState()
        var viewportH by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        // The current line slides up to ~30% from the top; previous lines drift above and fade.
        LaunchedEffect(cur, viewportH) {
            if (cur < 0 || viewportH <= 0) return@LaunchedEffect
            val targetY = viewportH * 0.30f
            val info = ls.layoutInfo.visibleItemsInfo.firstOrNull { it.index == cur }
            if (info != null) {
                // smooth glide: the new line slides up while the previous ones drift away
                ls.animateScrollBy(info.offset - targetY, tween(650, easing = FastOutSlowInEasing))
            } else {
                ls.animateScrollToItem(cur, scrollOffset = -targetY.toInt())
            }
        }
        LazyColumn(
            modifier
                .onSizeChanged { viewportH = it.height }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent, 0.10f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            state = ls,
            contentPadding = PaddingValues(top = 24.dp, bottom = with(density) { (viewportH * 0.7f).toDp() })
        ) {
            itemsIndexed(lines) { i, l ->
                val active = i == cur
                val dist = if (cur < 0) 0 else i - cur
                val scale by animateFloatAsState(
                    if (active || !synced) 1f else 0.80f,
                    spring(dampingRatio = 0.55f, stiffness = 260f), label = "lineScale"
                )
                val alpha by animateFloatAsState(
                    when {
                        !synced -> 0.92f
                        active -> 1f
                        dist < 0 -> 0.30f
                        else -> 0.50f
                    },
                    tween(450), label = "lineAlpha"
                )
                val glow by animateFloatAsState(if (active) 1f else 0f, tween(700), label = "lineGlow")
                val parts = l.text.split("\n")
                val rtl = isRtlText(parts[0])
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                        }
                ) {
                    parts.forEachIndexed { k, line ->
                        Text(
                            line,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White.copy(alpha = if (k == 0) 1f else 0.7f),
                            fontSize = if (k == 0) 21.sp else 15.sp,
                            lineHeight = if (k == 0) 27.sp else 20.sp,
                            fontWeight = if (k == 0) FontWeight.ExtraBold else FontWeight.Medium,
                            textAlign = TextAlign.Start,
                            style = TextStyle(shadow = if (k == 0) Shadow(accent.copy(alpha = 0.95f * glow), Offset.Zero, 30f * glow) else null)
                        )
                    }
                }
            }
            item {
                Column {
                    if (tr.busy) {
                        Text(
                            "Menerjemahkan… (pertama kali mengunduh model bahasa)",
                            style = MaterialTheme.typography.bodySmall, color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    tr.message?.let {
                        Text(
                            it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (ui.estimated) {
                        Text(
                            "Lirik tanpa timestamp: waktu sorotnya diperkirakan.",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Ganti file lirik", color = accent) }
                }
            }
        }
    }
}


@Composable
private fun VisualizerView(c: AppContainer, accent: Color, modifier: Modifier) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (c.prefs.bitPerfect) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Visualizer dimatikan saat mode bit-perfect aktif, karena efek audio akan mengubah jalur suara.", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
        }
    } else if (!granted) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Visualizer membaca spektrum lagu yang sedang diputar, jadi Android meminta izin audio.", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            Button(onClick = { launcher.launch(android.Manifest.permission.RECORD_AUDIO) }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) { Text("Izinkan") }
        }
    } else {
        DisposableEffect(Unit) {
            c.visualizer.start(c.player.player.audioSessionId)
            onDispose { c.visualizer.stop() }
        }
        val n = com.nexus.media.player.audio.VisualizerManager.BARS
        val sm = remember { FloatArray(n) }
        var frame by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { it }
                val raw = c.visualizer.bars.value
                for (i in 0 until n) sm[i] = maxOf(raw.getOrElse(i) { 0f }, sm[i] * 0.90f)
                frame++
            }
        }
        val top = remember(accent) { lerp(accent, Color.White, 0.6f) }
        Canvas(modifier) {
            val f = frame
            val gap = 4.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val h = (sm[i].coerceIn(0f, 1f) * size.height * 0.9f).coerceAtLeast(4.dp.toPx())
                val x = i * (bw + gap)
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(top, accent), startY = size.height - h, endY = size.height),
                    topLeft = Offset(x, size.height - h), size = androidx.compose.ui.geometry.Size(bw, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2)
                )
            }
        }
    }
}


private fun langName(code: String): String =
    java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }

private val translateLanguages: List<String> by lazy {
    val all = com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages()
    val top = listOf("id", "en", "ja", "ko", "zh", "ar", "es", "fr", "de", "pt", "ru", "hi", "th", "vi", "ms", "tr", "it")
    top.filter { it in all } + all.filter { it !in top }.sortedBy { langName(it) }
}

/** Button under the cover in lyrics mode: choose the target language and switch translation on/off. */
@Composable
private fun TranslateControl(c: AppContainer, accent: Color) {
    val set by c.prefs.translate.collectAsState()
    var open by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { open = true },
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = if (set.on) 0.55f else 0.25f), contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (set.on) langName(set.target) else "Terjemahkan", maxLines = 1, fontSize = 12.sp)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Terjemahan lirik") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tampilkan terjemahan", Modifier.weight(1f))
                        Switch(checked = set.on, onCheckedChange = { c.prefs.setTranslate(set.copy(on = it)) })
                    }
                    Text("Ke bahasa:", Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge)
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(translateLanguages) { code ->
                            Row(
                                Modifier.fillMaxWidth().clickable { c.prefs.setTranslate(set.copy(on = true, target = code)) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = set.target == code, onClick = null)
                                Spacer(Modifier.width(10.dp))
                                Text(langName(code))
                            }
                        }
                    }
                    Text(
                        "Diterjemahkan di perangkat. Model bahasa (sekitar 30 MB per bahasa) diunduh sekali, setelah itu bisa offline.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Selesai") } }
        )
    }
}
