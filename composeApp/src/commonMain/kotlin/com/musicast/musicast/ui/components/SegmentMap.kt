package com.musicast.musicast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.musicast.musicast.domain.model.AudioSegment
import com.musicast.musicast.domain.model.ContentType
import kotlin.random.Random

/**
 * Draws a horizontal bar showing the entire episode timeline with
 * music segments highlighted in the tertiary color and speech segments
 * in a subtle surface color.
 */
@Composable
fun SegmentMap(
    segments: List<AudioSegment>,
    durationMs: Long,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty() || durationMs <= 0) return

    val musicColor = MaterialTheme.colorScheme.tertiary
    val speechColor = MaterialTheme.colorScheme.surfaceVariant
    val positionColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        val width = size.width
        val height = size.height

        // Draw speech background
        drawRect(color = speechColor, size = size)

        // Draw music segments on top
        for (segment in segments) {
            if (segment.type == ContentType.MUSIC) {
                val startX = (segment.startMs.toFloat() / durationMs) * width
                val endX = (segment.endMs.toFloat() / durationMs) * width
                drawRect(
                    color = musicColor,
                    topLeft = Offset(startX, 0f),
                    size = Size(endX - startX, height),
                )
            }
        }

        // Draw playback position marker
        val posX = (positionMs.toFloat() / durationMs) * width
        drawRect(
            color = positionColor,
            topLeft = Offset(posX - 1f, 0f),
            size = Size(2f, height),
        )
    }
}

/**
 * Waveform-style seek bar that shows pseudo-random audio bars with
 * music segment coloring and touch-to-seek support.
 */
@Composable
fun WaveformSeekBar(
    segments: List<AudioSegment>,
    durationMs: Long,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryDim = primaryColor.copy(alpha = 0.3f)
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val tertiaryDim = tertiaryColor.copy(alpha = 0.3f)
    val positionIndicatorColor = MaterialTheme.colorScheme.onSurface

    var canvasWidth by remember { mutableStateOf(1f) }

    fun xToPosition(x: Float): Long {
        return ((x / canvasWidth) * durationMs).toLong().coerceIn(0L, durationMs)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val pos = xToPosition(offset.x)
                    onSeek(pos)
                    onSeekFinished()
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragEnd = { onSeekFinished() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val pos = xToPosition(change.position.x)
                        onSeek(pos)
                    },
                )
            },
    ) {
        canvasWidth = size.width
        val height = size.height
        val barCount = 120
        val totalBarSpace = size.width
        val barWidth = (totalBarSpace / barCount) * 0.65f
        val gapWidth = (totalBarSpace / barCount) * 0.35f
        val random = Random(durationMs)
        val positionFraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

        for (i in 0 until barCount) {
            val barX = i * (barWidth + gapWidth)
            val barFraction = i.toFloat() / barCount
            val barPositionMs = (barFraction * durationMs).toLong()

            // Generate pseudo-random height (between 15% and 100% of canvas height)
            val barHeight = height * (0.15f + random.nextFloat() * 0.85f)
            val barY = (height - barHeight) / 2f

            // Determine if this bar is in a music segment
            val isMusic = segments.any { segment ->
                segment.type == ContentType.MUSIC &&
                    barPositionMs >= segment.startMs &&
                    barPositionMs <= segment.endMs
            }

            // Color based on position and segment type
            val isPast = barFraction <= positionFraction
            val color = when {
                isMusic && isPast -> tertiaryColor
                isMusic && !isPast -> tertiaryDim
                isPast -> primaryColor
                else -> primaryDim
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(barX, barY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }

        // Draw position indicator line
        val posX = positionFraction * size.width
        drawRect(
            color = positionIndicatorColor,
            topLeft = Offset(posX - 1f, 0f),
            size = Size(2f, height),
        )
    }
}
