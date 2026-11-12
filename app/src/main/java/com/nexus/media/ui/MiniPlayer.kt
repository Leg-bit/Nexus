package com.nexus.media.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
    val item = st.current
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(item?.id) {
        while (true) {
            val d = c.player.player.duration
            progress = if (d > 0) (c.player.player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(320)),
        exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp)).clickable(onClick = onExpand),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Crossfade(targetState = item, modifier = Modifier.weight(1f), animationSpec = tween(300), label = "miniTrack") { t ->
                        if (t != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Thumb(t, Modifier.size(44.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text(
                                        t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    BounceIconButton(onClick = { c.player.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Sebelumnya") }
                    BounceIconButton(onClick = { c.player.toggle() }) {
                        Icon(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/Jeda")
                    }
                    BounceIconButton(onClick = { c.player.next() }) { Icon(Icons.Rounded.SkipNext, "Berikutnya") }
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            }
        }
    }
}
