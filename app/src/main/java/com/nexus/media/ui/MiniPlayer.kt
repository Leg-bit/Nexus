package com.nexus.media.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.media.di.AppContainer
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(c: AppContainer, onExpand: () -> Unit) {
    val st by c.player.state.collectAsState()
    val item = st.current ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(item.id) {
        while (true) {
            val d = c.player.player.duration
            progress = if (d > 0) (c.player.player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp)).clickable(onClick = onExpand),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(item, Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { c.player.toggle() }) {
                    Icon(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/Jeda")
                }
                IconButton(onClick = { c.player.next() }) { Icon(Icons.Rounded.SkipNext, "Berikutnya") }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}
