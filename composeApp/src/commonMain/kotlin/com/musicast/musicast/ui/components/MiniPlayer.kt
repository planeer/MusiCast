package com.musicast.musicast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.musicast.musicast.domain.model.PlaybackState
import com.musicast.musicast.ui.theme.AppIcons
import com.musicast.musicast.ui.theme.music

@Composable
fun MiniPlayer(
    state: PlaybackState,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episode = state.episode ?: return
    val accent = if (state.isMusicDetected) MaterialTheme.colorScheme.music else MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable { onTap() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            // Progress bar at the top (above content, away from gesture bar)
            LinearProgressIndicator(
                progress = {
                    if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Artwork(
                    url = state.artworkUrl,
                    fallbackText = state.podcastTitle.ifEmpty { episode.title },
                    modifier = Modifier.size(44.dp),
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isMusicDetected) {
                            Icon(
                                AppIcons.MusicNote,
                                contentDescription = "Music detected",
                                tint = accent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = "${formatSpeed(state.currentSpeed)}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                        if (state.podcastTitle.isNotEmpty()) {
                            Text(
                                text = " · ${state.podcastTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (state.isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp),
                    )
                }
                SkipButton(
                    icon = AppIcons.Forward,
                    seconds = 30,
                    contentDescription = "Skip forward 30 seconds",
                    onClick = onSkipForward,
                )
            }
        }
    }
}

/** Rounded podcast artwork with a text placeholder when there is no image. */
@Composable
fun Artwork(
    url: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = modifier,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackText.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Skip icon with the number of seconds drawn inside, like the system media controls. */
@Composable
fun SkipButton(
    icon: ImageVector,
    seconds: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(0.72f))
            Text(
                text = seconds.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = 1.dp),
            )
        }
    }
}
