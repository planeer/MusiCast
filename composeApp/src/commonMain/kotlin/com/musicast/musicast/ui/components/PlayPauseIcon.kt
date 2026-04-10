package com.musicast.musicast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        if (isPlaying) {
            // Pause: two vertical bars
            val barWidth = w * 0.25f
            val barHeight = h * 0.7f
            val gap = w * 0.15f
            val startX = (w - barWidth * 2 - gap) / 2f
            val startY = (h - barHeight) / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(startX, startY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth * 0.2f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + barWidth + gap, startY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth * 0.2f),
            )
        } else {
            // Play: triangle pointing right
            val triLeft = w * 0.25f
            val triRight = w * 0.8f
            val triTop = h * 0.15f
            val triBottom = h * 0.85f

            val path = Path().apply {
                moveTo(triLeft, triTop)
                lineTo(triRight, h / 2f)
                lineTo(triLeft, triBottom)
                close()
            }
            drawPath(path, color)
        }
    }
}
