package com.nexus.media.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.nexus.media.core.theme.ElectricBlue
import com.nexus.media.data.local.entities.MediaEntity
import com.nexus.media.di.AppContainer
import com.nexus.media.player.PlayerUiState
import com.nexus.media.player.audio.LyricLine
import com.nexus.media.player.audio.LyricsRepository
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
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Tutup", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    Text("Sedang diputar", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.width(48.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("artwork" to "Cover", "vinyl" to "Vinyl", "lyrics" to "Lirik", "compact" to "Compact").forEach { (k, label) ->
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
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        val s = minOf(maxWidth, maxHeight)
                        when (mode) {
                            "vinyl" -> VinylDisc(item, st.isPlaying, accent, s)
                            "lyrics" -> LyricsView(item, pos, c, accent, Modifier.fillMaxSize())
                            "compact" -> CompactView(st, c, accent, Modifier.fillMaxSize())
                            else -> CoverArt(item, st.isPlaying, accent, s)
                        }
                    }
                    if (mode != "compact") {
                        Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                        Text(item.artist, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = if (seeking) seekPos else pos.toFloat().coerceAtMost(maxOf(dur, 1L).toFloat()),
                        onValueChange = { seeking = true; seekPos = it },
                        onValueChangeFinished = { c.player.seekTo(seekPos.toLong()); pos = seekPos.toLong(); seeking = false },
                        valueRange = 0f..maxOf(dur, 1L).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.2f))
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTime(if (seeking) seekPos.toLong() else pos), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        Text(fmtTime(dur), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { c.player.setShuffle(!st.shuffle) }) {
                            Icon(Icons.Rounded.Shuffle, "Acak", tint = if (st.shuffle) accent else Color.White.copy(alpha = 0.8f))
                        }
                        IconButton(onClick = { c.player.previous() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        FilledIconButton(
                            onClick = { c.player.toggle() },
                            modifier = Modifier.size(76.dp).glowCircle(accent, 20.dp, 0.9f),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = Color.White)
                        ) {
                            Icon(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/Jeda", tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                        IconButton(onClick = { c.player.next() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        IconButton(onClick = { c.player.cycleRepeat() }) {
                            Icon(
                                if (st.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Ulangi",
                                tint = if (st.repeat != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { sheet = "queue" }) { Icon(Icons.Rounded.QueueMusic, "Antrian", tint = Color.White) }
                        IconButton(onClick = { sheet = "sleep" }) {
                            Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (sleepEnds != null) accent else Color.White)
                        }
                        IconButton(onClick = { sheet = "eq" }) { Icon(Icons.Rounded.Equalizer, "Equalizer", tint = Color.White) }
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
private fun VinylDisc(item: MediaEntity, playing: Boolean, accent: Color, size: Dp) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle = (angle + (now - last) / 1_000_000_000f * 45f) % 360f
                last = now
            }
        }
    }
    val arm by animateFloatAsState(if (playing) 0f else -26f, tween(700), label = "arm")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(0.88f).glowCircle(accent, 30.dp, 1f))
        Box(Modifier.fillMaxSize(0.88f).rotate(angle), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                drawCircle(Color(0xFF0B0B10), r, center)
                var rr = r * 0.97f
                while (rr > r * 0.36f) {
                    drawCircle(Color.White.copy(alpha = 0.04f), rr, center, style = Stroke(1.dp.toPx()))
                    rr -= 4.5.dp.toPx()
                }
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                        center
                    ),
                    radius = r, center = center
                )
                drawCircle(accent, r * 0.34f, center)
            }
            Box(Modifier.fillMaxSize(0.56f).clip(CircleShape)) { Thumb(item, Modifier.fillMaxSize(), CircleShape) }
            Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFF05060A)))
        }
        Canvas(Modifier.fillMaxSize()) {
            val pivot = Offset(this.size.width * 0.93f, this.size.height * 0.05f)
            rotate(degrees = arm, pivot = pivot) {
                val end = Offset(this.size.width * 0.70f, this.size.height * 0.80f)
                drawLine(Color(0xFFD5D9E3), pivot, end, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Color(0xFF8E94A3), 12.dp.toPx(), pivot)
                drawCircle(Color(0xFFD5D9E3), 6.dp.toPx(), pivot)
                drawCircle(Color(0xFFB0B5C2), 7.dp.toPx(), end)
            }
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

private data class LyricsUi(val loading: Boolean, val lines: List<LyricLine>)

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
                runCatching { ctx.contentResolver.openInputStream(Uri.parse(saved))!!.bufferedReader().use { it.readText() } }.getOrNull()
            } else null
            if (raw == null) raw = loadSidecarLyrics(ctx, Uri.parse(item.uri))
            if (raw == null && c.prefs.onlineLyrics) raw = LyricsRepository.online(ctx, item)
            if (raw == null) emptyList() else parseLyrics(raw)
        }
        value = LyricsUi(false, parsed)
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
        val lines = ui.lines
        val synced = lines.first().timeMs >= 0
        val cur = if (synced) lines.indexOfLast { it.timeMs <= posMs }.coerceAtLeast(0) else -1
        val ls = rememberLazyListState()
        LaunchedEffect(cur) { if (cur >= 0) ls.animateScrollToItem(maxOf(cur - 2, 0)) }
        LazyColumn(modifier, state = ls, horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(vertical = 80.dp)) {
            itemsIndexed(lines) { i, l ->
                val active = i == cur
                Text(
                    l.text, textAlign = TextAlign.Center,
                    fontSize = if (active) 23.sp else 17.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active || !synced) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
                )
            }
            item { TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Ganti file lirik", color = accent) } }
        }
    }
}
