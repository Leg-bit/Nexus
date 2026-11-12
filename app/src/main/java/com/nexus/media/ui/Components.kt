package com.nexus.media.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.navigation.NavHostController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexus.media.data.local.entities.MediaEntity
import com.nexus.media.data.repository.SortSetting
import com.nexus.media.data.repository.defaultAsc
import com.nexus.media.di.AppContainer
import com.nexus.media.player.video.VideoPlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalNav = staticCompositionLocalOf<NavHostController?> { null }
val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }
val LocalGoTab = staticCompositionLocalOf<(Int) -> Unit> { {} }


fun openMedia(ctx: Context, c: AppContainer, item: MediaEntity, list: List<MediaEntity>) {
    if (item.mediaType == "video") {
        ctx.startActivity(
            Intent(ctx, VideoPlayerActivity::class.java)
                .setData(Uri.parse(item.uri))
                .putExtra("id", item.id)
                .putExtra("title", item.title)
        )
    } else {
        val audio = list.filter { it.mediaType == "audio" }
        c.player.play(audio, audio.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
        else Spacer(Modifier.width(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(
                brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFA9C2FF), Color(0xFFC9A8FF)))
            ),
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun Section(title: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(text: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

// ---------- artwork ----------
object ArtworkCache {
    private val cache = LruCache<String, Bitmap>(64)
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val gate = kotlinx.coroutines.sync.Semaphore(3)   // never decode more than 3 covers at once

    suspend fun load(ctx: Context, item: MediaEntity): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(item.uri)?.let { return@withContext it }
        if (item.uri in missing) return@withContext null
        val bmp = gate.withPermit {
            val uri = Uri.parse(item.uri)
            var b: Bitmap? = null
            if (android.os.Build.VERSION.SDK_INT >= 29 && item.uri.startsWith("content://media/")) {
                b = runCatching { ctx.contentResolver.loadThumbnail(uri, android.util.Size(400, 400), null) }.getOrNull()
            }
            if (b == null) {
                b = try {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(ctx, uri)
                        r.embeddedPicture?.let { decode(it, 400) }
                    } finally {
                        r.release()
                    }
                } catch (e: Exception) {
                    null
                }
            }
            b
        }
        if (bmp != null) cache.put(item.uri, bmp) else missing.add(item.uri)
        bmp
    }

    private fun decode(data: ByteArray, target: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

@Composable
fun Thumb(item: MediaEntity, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (item.mediaType == "video") {
            VideoFrameImage(item, Modifier.fillMaxSize())
        } else {
            val ctx = LocalContext.current
            val bmp by produceState<Bitmap?>(null, item.uri) { value = ArtworkCache.load(ctx, item) }
            val b = bmp
            if (b != null) {
                Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                ArtPlaceholder(item.album + item.title, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun MediaRow(
    item: MediaEntity,
    onClick: () -> Unit,
    playing: Boolean = false,
    onRemove: (() -> Unit)? = null
) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Thumb(item, Modifier.size(52.dp))
            if (playing) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) { PlayingBars(MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            val sub = if (item.mediaType == "audio") "${item.artist} • ${fmtTime(item.duration)}"
            else "${fmtTime(item.duration)} • ${"%.1f".format(item.size / 1048576.0)} MB"
            Text(
                sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (item.mediaType == "audio") {
                    DropdownMenuItem(text = { Text("Putar berikutnya") }, onClick = { c.player.playNext(item); menu = false })
                    DropdownMenuItem(text = { Text("Tambah ke antrian") }, onClick = { c.player.addToQueue(item); menu = false })
                }
                DropdownMenuItem(text = { Text("Sembunyikan dari pustaka") }, onClick = { c.prefs.setHiddenId(item.id, true); menu = false })
                DropdownMenuItem(text = { Text("Detail") }, onClick = { menu = false; nav?.navigate("details/${item.id}") })
                DropdownMenuItem(text = { Text("Tambah ke playlist") }, onClick = { menu = false; pick = true })
                if (onRemove != null) {
                    DropdownMenuItem(text = { Text("Hapus dari playlist") }, onClick = { onRemove(); menu = false })
                }
            }
        }
    }
    if (pick) PlaylistPickerDialog(item) { pick = false }
}

@Composable
fun PlaylistPickerDialog(item: MediaEntity, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val lists by c.repository.playlists().collectAsState(emptyList())
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah ke playlist") },
        text = {
            Column {
                if (lists.isEmpty() && !creating) Text("Belum ada playlist.")
                lists.forEach { p ->
                    Text(
                        p.name,
                        Modifier.fillMaxWidth().clickable {
                            c.appScope.launch { c.repository.addToPlaylist(p.id, item.id) }
                            onDismiss()
                        }.padding(vertical = 12.dp)
                    )
                }
                if (creating) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nama playlist") })
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        c.appScope.launch {
                            val id = c.repository.createPlaylist(name)
                            c.repository.addToPlaylist(id, item.id)
                        }
                        onDismiss()
                    }
                }) { Text("Buat & tambah") }
            } else TextButton(onClick = { creating = true }) { Text("Playlist baru") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(item: MediaEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val nav = LocalNav.current
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = { nav?.navigate("details/${item.id}") })) {
        Box {
            Thumb(item, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
            Text(
                fmtTime(item.duration),
                Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White, style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            if (item.width > 0) "${item.width}x${item.height} • ${"%.1f".format(item.size / 1048576.0)} MB" else "${"%.1f".format(item.size / 1048576.0)} MB",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/** Sort button with a dropdown: tap an option to sort by it, tap the selected one again to flip the direction. */
@Composable
fun SortMenu(options: List<Pair<String, String>>, current: SortSetting, onChange: (SortSetting) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.SortByAlpha, "Urutkan") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Urutkan berdasarkan", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            options.forEach { (key, label) ->
                val selected = key == current.key
                DropdownMenuItem(
                    text = {
                        Text(
                            if (selected) label + (if (current.asc) "  ↑" else "  ↓") else label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = { if (selected) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = {
                        onChange(if (selected) current.copy(asc = !current.asc) else SortSetting(key, defaultAsc(key)))
                        open = false
                    }
                )
            }
            Text(
                "Tekan opsi yang sama untuk membalik urutan", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun sortLabel(options: List<Pair<String, String>>, s: SortSetting): String =
    (options.firstOrNull { it.first == s.key }?.second ?: s.key) + if (s.asc) " ↑" else " ↓"
