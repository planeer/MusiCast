package com.musicast.musicast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.musicast.musicast.domain.model.AudioSegment
import com.musicast.musicast.domain.model.ContentType
import com.musicast.musicast.ui.theme.music

/**
 * Seek bar that doubles as a map of the episode: speech is a thin neutral
 * track, detected music sections are taller blocks in the music accent.
 * Supports tap and drag to seek.
 */
@Composable
fun SegmentTimeline(
    segments: List<AudioSegment>,
    durationMs: Long,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0) return

    val playedColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val musicColor = MaterialTheme.colorScheme.music
    val musicDim = musicColor.copy(alpha = 0.4f)
    val musicSegments = remember(segments) { segments.filter { it.type == ContentType.MUSIC } }

    var isDragging by remember { mutableStateOf(false) }
    // Gesture handlers outlive recompositions; always call the latest callbacks
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekFinished by rememberUpdatedState(onSeekFinished)

    fun xToPosition(x: Float, width: Int): Long {
        return ((x / width.coerceAtLeast(1)) * durationMs).toLong().coerceIn(0L, durationMs)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    currentOnSeek(xToPosition(offset.x, size.width))
                    currentOnSeekFinished()
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        currentOnSeekFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        currentOnSeekFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        currentOnSeek(xToPosition(change.position.x, size.width))
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val posX = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width

        // Speech track: played part in primary, rest neutral
        val trackHeight = 4.dp.toPx()
        drawSplitBar(0f, size.width, posX, centerY, trackHeight, playedColor, trackColor, trackHeight / 2f)

        // Music blocks stand taller than the track so they read at a glance
        val musicHeight = 14.dp.toPx()
        for (segment in musicSegments) {
            val startX = (segment.startMs.toFloat() / durationMs) * size.width
            val endX = ((segment.endMs.toFloat() / durationMs) * size.width).coerceAtLeast(startX + 2.dp.toPx())
            drawSplitBar(startX, endX, posX, centerY, musicHeight, musicColor, musicDim, 3.dp.toPx())
        }

        val inMusic = musicSegments.any { positionMs in it.startMs..it.endMs }
        val thumbRadius = (if (isDragging) 10.dp else 8.dp).toPx()
        drawCircle(
            color = if (inMusic) musicColor else playedColor,
            radius = thumbRadius,
            center = Offset(posX, centerY),
        )
    }
}

/** Draws a horizontal bar from [startX] to [endX], colored [played] up to [splitX] and [remaining] after. */
private fun DrawScope.drawSplitBar(
    startX: Float,
    endX: Float,
    splitX: Float,
    centerY: Float,
    height: Float,
    played: Color,
    remaining: Color,
    corner: Float,
) {
    val top = centerY - height / 2f
    val radius = CornerRadius(corner, corner)
    drawRoundRect(remaining, Offset(startX, top), Size(endX - startX, height), radius)
    val playedEnd = splitX.coerceIn(startX, endX)
    if (playedEnd > startX) {
        drawRoundRect(played, Offset(startX, top), Size(playedEnd - startX, height), radius)
    }
}
