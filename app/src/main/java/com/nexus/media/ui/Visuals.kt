package com.nexus.media.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.*
import coil.request.*
import com.nexus.media.core.theme.ElectricBlue
import com.nexus.media.data.local.entities.MediaEntity
import kotlin.math.abs

/** Soft glow behind a rounded rectangle (drawn outside the bounds). */
fun Modifier.glow(color: Color, radius: Dp = 18.dp, corner: Dp = 24.dp, intensity: Float = 0.6f): Modifier = drawBehind {
    val r = radius.toPx()
    val cr = corner.toPx()
    for (i in 8 downTo 1) {
        val t = i / 8f
        drawRoundRect(
            color = color.copy(alpha = (intensity * (1f - t + 0.1f) * 0.3f).coerceIn(0f, 1f)),
            topLeft = Offset(-r * t, -r * t),
            size = Size(size.width + 2 * r * t, size.height + 2 * r * t),
            cornerRadius = CornerRadius(cr + r * t)
        )
    }
}

/** Soft glow behind a circle. */
fun Modifier.glowCircle(color: Color, radius: Dp = 24.dp, intensity: Float = 0.6f): Modifier = drawBehind {
    val r = radius.toPx()
    val base = size.minDimension / 2
    for (i in 8 downTo 1) {
        val t = i / 8f
        drawCircle(
            color = color.copy(alpha = (intensity * (1f - t + 0.1f) * 0.3f).coerceIn(0f, 1f)),
            radius = base + r * t, center = center
        )
    }
}

/** Average colour of the artwork, pushed towards a vivid tone for backgrounds/glow. */
fun dominantColor(b: Bitmap): Color {
    val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(px, hsv)
    hsv[1] = (hsv[1] * 1.3f).coerceIn(0.45f, 1f)
    hsv[2] = hsv[2].coerceIn(0.7f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun ArtPlaceholder(seed: String, modifier: Modifier = Modifier) {
    val h = (abs(seed.hashCode()) % 360).toFloat()
    Box(
        modifier.background(Brush.linearGradient(listOf(Color.hsv(h, 0.6f, 0.85f), Color.hsv((h + 55f) % 360f, 0.75f, 0.45f)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize(0.45f))
    }
}

@Composable
fun VideoFrameImage(item: MediaEntity, modifier: Modifier = Modifier, alpha: Float = 1f, cyan: Boolean = false) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(Uri.parse(item.uri)).size(400).videoFramePercent(0.1).crossfade(true).build(),
        contentDescription = null, contentScale = ContentScale.Crop, alpha = alpha, modifier = modifier,
        colorFilter = if (cyan) ColorFilter.tint(Color(0xFF19D3E6), BlendMode.Color) else null
    )
}

/** Video grid tile: original thumbnail, blue accent outline with glow, no title. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoTile(item: MediaEntity, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val indication = LocalIndication.current
    val appear = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "tilePress")
    Box(
        modifier.aspectRatio(1f)
            .graphicsLayer { val sc = appear.value * press; scaleX = sc; scaleY = sc }
            .clip(shape)
            .background(Color(0xFF0B1226))
            .combinedClickable(interactionSource = src, indication = indication, onClick = onClick, onLongClick = onLongClick)
            .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.30f))), shape)
    ) {
        VideoFrameImage(item, Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))))
        Text(
            fmtTime(item.duration),
            Modifier.align(Alignment.BottomEnd).padding(10.dp)
                .background(Color(0xCC0A1330), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall
        )
    }
}


/** Three bouncing bars: marks the song that is playing. */
@Composable
fun PlayingBars(color: Color, modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "bars")
    val a by t.animateFloat(0.25f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.25f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(560, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.25f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "c")
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        listOf(a, b, c).forEach { f ->
            Box(Modifier.width(4.dp).fillMaxHeight(f).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}
