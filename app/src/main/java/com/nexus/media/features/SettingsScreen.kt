package com.nexus.media.features

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.nexus.media.player.audio.LyricsRepository
import com.nexus.media.ui.*

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun SwitchRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(vm: LibraryViewModel) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val scanning by c.repository.isScanning.collectAsState()
    val music by vm.music.collectAsState()
    val videos by vm.videos.collectAsState()
    val hidden by c.prefs.hiddenFolders.collectAsState()
    val hiddenIds by c.prefs.hiddenIds.collectAsState()
    val custom by c.prefs.customFolders.collectAsState()
    val filter by c.prefs.filter.collectAsState()
    var minSec by remember(filter.minDurationSec) { mutableFloatStateOf(filter.minDurationSec.toFloat()) }
    var kw by remember(filter.excludeKeywords) { mutableStateOf(filter.excludeKeywords) }
    var bgAudio by remember { mutableStateOf(c.prefs.videoBgAudio) }
    var online by remember { mutableStateOf(c.prefs.onlineLyrics) }
    var mode by remember { mutableStateOf(c.prefs.playerMode) }
    var vol by remember { mutableFloatStateOf(c.prefs.playerVolume) }
    var showEq by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            c.prefs.setCustom(u.toString(), true)
            c.repository.rescan()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar("Pengaturan") }

        item {
            Group("Pustaka") {
                Text("${music.size} lagu • ${videos.size} video terindeks")
                Button(onClick = { c.repository.rescan() }, enabled = !scanning) { Text(if (scanning) "Memindai…" else "Scan ulang media") }
                OutlinedButton(onClick = { addFolder.launch(null) }) { Text("Tambah folder (SAF)") }
            }
        }

        item {
            Group("Filter musik") {
                Text("Durasi minimum lagu: ${minSec.toInt()} detik")
                Slider(
                    value = minSec, onValueChange = { minSec = it },
                    onValueChangeFinished = { c.prefs.setFilter(filter.copy(minDurationSec = minSec.toInt())) },
                    valueRange = 0f..180f, steps = 35
                )
                SwitchRow("Hanya musik", "Abaikan rekaman, nada dering, dan audio yang bukan musik", filter.onlyMusic) {
                    c.prefs.setFilter(filter.copy(onlyMusic = it))
                }
                OutlinedTextField(
                    kw, { kw = it }, label = { Text("Sembunyikan jika lokasi/judul mengandung") },
                    supportingText = { Text("Pisahkan dengan koma, mis. elevenlabs,dubbing,/android/") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { c.prefs.setFilter(filter.copy(excludeKeywords = kw)) }) { Text("Terapkan kata kunci") }
                Text("${hiddenIds.size} item disembunyikan manual", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hiddenIds.isNotEmpty()) TextButton(onClick = { c.prefs.clearHiddenIds() }) { Text("Tampilkan semua item tersembunyi") }
            }
        }

        item {
            Group("Pemutar") {
                Text("Batas volume pemutar: ${(vol * 100).toInt()}%")
                Slider(
                    value = vol, onValueChange = { vol = it; c.player.setVolume(it) },
                    valueRange = 0.05f..1f
                )
                Text("Mode pemutar default")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("artwork" to "Cover", "vinyl" to "Vinyl", "lyrics" to "Lirik", "compact" to "Compact").forEach { (k, label) ->
                        FilterChip(selected = mode == k, onClick = { mode = k; c.prefs.playerMode = k }, label = { Text(label) })
                    }
                }
                SwitchRow("Audio latar untuk video", "Suara video lanjut saat layar dikunci atau app ditutup", bgAudio) {
                    bgAudio = it; c.prefs.videoBgAudio = it
                }
                SwitchRow("Cari lirik online", "Memakai LRCLIB saat lagu tidak punya file lirik", online) {
                    online = it; c.prefs.onlineLyrics = it
                }
                OutlinedButton(onClick = { showEq = true }) { Text("Buka equalizer") }
            }
        }

        item {
            Group("Folder kustom") {
                if (custom.isEmpty()) Text("Belum ada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                custom.toList().forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(DocumentFile.fromTreeUri(ctx, Uri.parse(s))?.name ?: s, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { c.prefs.setCustom(s, false); c.repository.rescan() }) { Icon(Icons.Rounded.Delete, "Hapus") }
                    }
                }
            }
        }

        item {
            Group("Folder tersembunyi") {
                if (hidden.isEmpty()) Text("Tidak ada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                hidden.toList().forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { c.prefs.setHidden(p, false) }) { Icon(Icons.Rounded.Visibility, "Tampilkan") }
                    }
                }
            }
        }

        item {
            Group("Data") {
                OutlinedButton(onClick = { vm.clearHistory(); msg = "Riwayat dihapus" }) { Text("Hapus riwayat") }
                OutlinedButton(onClick = { LyricsRepository.clearCache(ctx); msg = "Cache lirik dihapus" }) { Text("Hapus cache lirik") }
                msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Text("NEXUS Media v0.3.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showEq) EqualizerSheet(c) { showEq = false }
}
