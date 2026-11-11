package com.nexus.media.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Midnight = Color(0xFF0A1330)
val ElectricBlue = Color(0xFF2E7BFF)

private val NexusColors = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16336B),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF6FA8FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0B1226),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF131E3D),
    onSurfaceVariant = Color(0xFFA9B4D0),
    surfaceContainer = Color(0xFF0B1226),
    surfaceContainerHigh = Color(0xFF131E3D),
    surfaceContainerHighest = Color(0xFF1A2749),
    outline = Color(0xFF3A4A75)
)

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusColors,
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}

val Lime = androidx.compose.ui.graphics.Color(0xFF9CFF2E)
val CyanTile = androidx.compose.ui.graphics.Color(0xFF1FB5AE)
