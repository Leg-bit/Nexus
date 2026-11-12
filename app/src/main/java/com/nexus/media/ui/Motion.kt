package com.nexus.media.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/** Icon button that squashes and springs back when pressed. */
@Composable
fun BounceIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.78f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "bounce")
    IconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = s; scaleY = s },
        interactionSource = src,
        content = content
    )
}

@Composable
fun BounceFilledIconButton(
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "bounceF")
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = s; scaleY = s },
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor, contentColor = Color.White),
        interactionSource = src,
        content = content
    )
}
