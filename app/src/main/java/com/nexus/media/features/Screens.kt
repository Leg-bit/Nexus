package com.nexus.media.features

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavHostController
import com.nexus.media.data.local.entities.MediaEntity
import com.nexus.media.ui.*

@Composable
private fun QuickCard(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HomeScreen(vm: LibraryViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val cw by vm.continueWatching.collectAsState()
    val recent by vm.recent.collectAsState()
    val music by vm.music.collectAsState()
    val videos by vm.videos.collectAsState()
    val scanning by c.repository.isScanning.collectAsState()
    val openDrawer = LocalOpenDrawer.current
    val goTab = LocalGoTab.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            TopBar("NEXUS Media", actions = {
                IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Rounded.Search, "Cari") }
                IconButton(onClick = openDrawer) { Icon(Icons.Rounded.Menu, "Menu") }
            })
        }
        if (scanning) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickCard(Icons.Rounded.Videocam, "Video", "${videos.size} file", Modifier.weight(1f)) { goTab(1) }
                    QuickCard(Icons.Rounded.MusicNote, "Musik", "${music.size} lagu", Modifier.weight(1f)) { goTab(2) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickCard(Icons.Rounded.PlaylistPlay, "Playlist", "Koleksi", Modifier.weight(1f)) { goTab(3) }
                    QuickCard(Icons.Rounded.Folder, "Folder", "Penyimpanan lokal", Modifier.weight(1f)) { nav.navigate("folders") }
                }
            }
        }
        if (cw.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); Section("Lanjut menonton") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cw, key = { it.id }) { v -> VideoTile(v, Modifier.width(150.dp)) { openMedia(ctx, c, v, cw) } }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); Section("Terakhir diputar") }
            items(recent.take(10), key = { it.id }) { m -> MediaRow(m, onClick = { openMedia(ctx, c, m, recent) }) }
        }
        if (music.isEmpty() && videos.isEmpty() && !scanning) {
            item {
                EmptyState("Belum ada media yang ditemukan.") {
                    Button(onClick = { c.repository.rescan() }) { Text("Scan ulang") }
                }
            }
        }
    }
}

@Composable
fun VideoScreen(vm: LibraryViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val videos by vm.videos.collectAsState()
    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TopBar("Video", actions = { IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Rounded.Search, "Cari") } })
        }
        if (videos.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Belum ada video.") }
        items(videos, key = { it.id }) { v ->
            VideoTile(v, onLongClick = { nav.navigate("details/${v.id}") }) { openMedia(ctx, c, v, videos) }
        }
    }
}

@Composable
fun MusicScreen(vm: LibraryViewModel, nav: NavHostController) {
    val c = LocalContainer.current
    val songs by vm.music.collectAsState()
    val st by c.player.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val albums = remember(songs) { songs.groupBy { it.album }.toList().sortedBy { it.first.lowercase() } }
    val artists = remember(songs) { songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() } }
    Column(Modifier.fillMaxSize()) {
        TopBar("Musik", actions = { IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Rounded.Search, "Cari") } })
        TabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            listOf("Lagu", "Album", "Artis").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        if (songs.isEmpty()) {
            EmptyState("Belum ada musik.")
        } else when (tab) {
            0 -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { c.player.setShuffle(false); c.player.play(songs, 0) }) { Text("Putar semua") }
                        OutlinedButton(onClick = { c.player.playShuffled(songs) }) { Text("Acak") }
                    }
                }
                itemsIndexed(songs, key = { _, m -> m.id }) { i, m ->
                    MediaRow(m, playing = st.current?.id == m.id, onClick = { c.player.play(songs, i) })
                }
            }
            1 -> GroupList(albums, "album", nav)
            else -> GroupList(artists, "artist", nav)
        }
    }
}

@Composable
private fun GroupList(groups: List<Pair<String, List<MediaEntity>>>, kind: String, nav: NavHostController) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.first }) { (name, list) ->
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate("group/$kind/${enc(name)}") }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(list.first(), Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${list.size} lagu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun GroupScreen(vm: LibraryViewModel, nav: NavHostController, kind: String, name: String) {
    val c = LocalContainer.current
    val songs by vm.music.collectAsState()
    val st by c.player.state.collectAsState()
    val list = remember(songs, kind, name) { songs.filter { if (kind == "album") it.album == name else it.artist == name } }
    Column(Modifier.fillMaxSize()) {
        TopBar(name, onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { c.player.setShuffle(false); c.player.play(list, 0) }) { Text("Putar") }
                    OutlinedButton(onClick = { c.player.playShuffled(list) }) { Text("Acak") }
                }
            }
            itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
                MediaRow(m, playing = st.current?.id == m.id, onClick = { c.player.play(list, i) })
            }
        }
    }
}

@Composable
fun LibraryScreen(vm: LibraryViewModel, nav: NavHostController) {
    val playlists by vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize()) {
        item { TopBar("Pustaka") }
        item { LinkRow(Icons.Rounded.Folder, "Folder") { nav.navigate("folders") } }
        item { LinkRow(Icons.Rounded.History, "Terakhir diputar") { nav.navigate("recent") } }
        item { LinkRow(Icons.Rounded.ContentCopy, "File duplikat") { nav.navigate("duplicates") } }
        item {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Section("Playlist") }
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Buat playlist") }
            }
        }
        if (playlists.isEmpty()) item { EmptyState("Belum ada playlist.") }
        items(playlists, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate("playlist/${p.id}") }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { vm.deletePlaylist(p.id) }) { Icon(Icons.Rounded.Delete, "Hapus") }
            }
        }
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Playlist baru") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nama") }) },
            confirmButton = { TextButton(onClick = { vm.createPlaylist(name); name = ""; creating = false }) { Text("Buat") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
    }
}

@Composable
fun PlaylistScreen(vm: LibraryViewModel, nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val items by remember(id) { vm.playlistMedia(id) }.collectAsState(emptyList())
    val playlists by vm.playlists.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(playlists.firstOrNull { it.id == id }?.name ?: "Playlist", onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize()) {
            if (items.isEmpty()) item { EmptyState("Playlist kosong. Tambahkan lewat menu ⋮ pada lagu atau video.") }
            itemsIndexed(items, key = { _, m -> m.id }) { _, m ->
                MediaRow(m, onClick = { openMedia(ctx, c, m, items) }, onRemove = { vm.removeFromPlaylist(id, m.id) })
            }
        }
    }
}

@Composable
fun FoldersScreen(vm: LibraryViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val all by vm.all.collectAsState()
    var cat by remember { mutableStateOf("Semua") }
    val cats = listOf("Semua", "Music", "Movies", "Download", "DCIM", "Recordings", "Kustom")
    val folders = remember(all, cat) {
        all.filter { inCategory(it, cat) }.groupBy { it.folderPath }.toList().sortedBy { folderName(it.first).lowercase() }
    }
    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            c.prefs.setCustom(u.toString(), true)
            c.repository.rescan()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Folder", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { addFolder.launch(null) }) { Icon(Icons.Rounded.Add, "Tambah folder") }
        })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { k -> FilterChip(selected = cat == k, onClick = { cat = k }, label = { Text(k) }) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (folders.isEmpty()) item { EmptyState("Belum ada folder berisi media.") }
            items(folders, key = { it.first }) { (path, list) ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("folder/${enc(path)}") }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folderName(path), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            "${list.size} item • $path", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { c.prefs.setHidden(path, true) }) { Icon(Icons.Rounded.VisibilityOff, "Sembunyikan") }
                }
            }
        }
    }
}

@Composable
fun FolderScreen(vm: LibraryViewModel, nav: NavHostController, path: String) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val all by vm.all.collectAsState()
    val st by c.player.state.collectAsState()
    val list = remember(all, path) { all.filter { it.folderPath == path } }
    Column(Modifier.fillMaxSize()) {
        TopBar(folderName(path), onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { m ->
                MediaRow(m, playing = st.current?.id == m.id, onClick = { openMedia(ctx, c, m, list) })
            }
        }
    }
}

@Composable
fun SearchScreen(vm: LibraryViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    var q by remember { mutableStateOf("") }
    val results by remember(q) { vm.search(q) }.collectAsState(emptyList())
    Column(Modifier.fillMaxSize()) {
        TopBar("Cari", onBack = { nav.popBackStack() })
        OutlinedTextField(
            q, { q = it }, singleLine = true, placeholder = { Text("Judul, artis, album") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (q.isNotBlank() && results.isEmpty()) item { EmptyState("Tidak ada hasil.") }
            items(results, key = { it.id }) { m -> MediaRow(m, onClick = { openMedia(ctx, c, m, results) }) }
        }
    }
}

@Composable
fun RecentScreen(vm: LibraryViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val recent by vm.recent.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("Terakhir diputar", onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize()) {
            if (recent.isEmpty()) item { EmptyState("Belum ada riwayat.") }
            items(recent, key = { it.id }) { m -> MediaRow(m, onClick = { openMedia(ctx, c, m, recent) }) }
        }
    }
}

@Composable
fun DuplicatesScreen(vm: LibraryViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val dups by vm.duplicates.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("File duplikat", onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize()) {
            if (dups.isEmpty()) item { EmptyState("Tidak ada duplikat (judul + durasi sama).") }
            items(dups, key = { it.id }) { m ->
                Column {
                    MediaRow(m, onClick = { openMedia(ctx, c, m, dups) })
                    Text(
                        m.folderPath, Modifier.padding(start = 80.dp, end = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun inCategory(m: MediaEntity, cat: String): Boolean = when (cat) {
    "Semua" -> true
    "Kustom" -> m.id.startsWith("s_")
    "Recordings" -> listOf("recordings", "recorder", "voice").any { m.folderPath.contains(it, ignoreCase = true) }
    else -> m.folderPath.split('/').any { it.equals(cat, ignoreCase = true) }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "-" })
    }
}

/** Halaman Audio Details / Video Details. */
@Composable
fun DetailsScreen(nav: NavHostController, id: String) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val item by produceState<MediaEntity?>(null, id) { value = c.db.media().getMany(listOf(id)).firstOrNull() }
    val m = item
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(if (m?.mediaType == "video") "Detail video" else "Detail audio", onBack = { nav.popBackStack() })
        if (m == null) {
            EmptyState("Memuat…")
        } else {
            Thumb(
                m, Modifier.padding(horizontal = 16.dp).fillMaxWidth().then(
                    if (m.mediaType == "video") Modifier.aspectRatio(16 / 9f) else Modifier.height(240.dp)
                ),
                RoundedCornerShape(20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { openMedia(ctx, c, m, listOf(m)) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Putar") }
            DetailRow("Judul", m.title)
            if (m.mediaType == "audio") {
                DetailRow("Artis", m.artist)
                DetailRow("Album", m.album)
                DetailRow("Genre", m.genre)
            } else {
                DetailRow("Resolusi", if (m.width > 0) "${m.width} x ${m.height}" else "")
            }
            DetailRow("Durasi", fmtTime(m.duration))
            DetailRow("Ukuran", "%.2f MB".format(m.size / 1048576.0))
            DetailRow("Tipe", m.mimeType)
            DetailRow("Folder", m.folderPath)
            DetailRow("Ditambahkan", java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.dateAdded * 1000)))
            DetailRow("URI", m.uri)
        }
    }
}
