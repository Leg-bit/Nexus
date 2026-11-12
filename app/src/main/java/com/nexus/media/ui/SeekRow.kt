package com.nexus.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One-line seek bar:  0:42 ━━━━●━━━━━━ 3:15  (times sit inside the row, thin track, glowing thumb). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekRow(pos: Long, dur: Long, accent: Color, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var seeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableFloatStateOf(0f) }
    val max = maxOf(dur, 1L).toFloat()
    val shown = if (seeking) seekPos else pos.toFloat().coerceIn(0f, max)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            fmtTime(shown.toLong()), Modifier.width(46.dp),
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f)
        )
        Slider(
            value = shown,
            onValueChange = { seeking = true; seekPos = it },
            onValueChangeFinished = { onSeek(seekPos.toLong()); seeking = false },
            valueRange = 0f..max,
            modifier = Modifier.weight(1f),
            thumb = {
                Box(Modifier.size(14.dp).glowCircle(accent, 8.dp, 0.9f).clip(CircleShape).background(Color.White))
            },
            track = { st ->
                val f = (st.value / max).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(accent))
                }
            }
        )
        Text(
            fmtTime(dur), Modifier.width(46.dp), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f)
        )
    }
}
