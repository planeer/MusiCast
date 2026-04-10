package com.musicast.musicast.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.musicast.musicast.ui.components.PlayPauseIcon
import com.musicast.musicast.ui.components.SpeedControl
import com.musicast.musicast.ui.components.WaveformSeekBar
import com.musicast.musicast.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val episode = state.episode

    if (episode == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text("No episode playing", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            "<",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {

        // Artwork
        if (state.artworkUrl != null) {
            AsyncImage(
                model = state.artworkUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.size(280.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = episode.title.take(2).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Episode title
        Text(
            text = episode.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Podcast name
        if (state.podcastTitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.podcastTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Waveform seek bar
        var isSeeking by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableStateOf(0L) }

        if (state.durationMs > 0) {
            WaveformSeekBar(
                segments = state.segments,
                durationMs = state.durationMs,
                positionMs = if (isSeeking) seekPosition else state.positionMs,
                onSeek = { position ->
                    isSeeking = true
                    seekPosition = position
                },
                onSeekFinished = {
                    viewModel.seekTo(seekPosition)
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            // Time labels
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatTime(if (isSeeking) seekPosition else state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTime(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Transport controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Skip backward 15s
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(48.dp),
            ) {
                IconButton(
                    onClick = { viewModel.skipBackward() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = "15",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Play/Pause
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            ) {
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PlayPauseIcon(
                        isPlaying = state.isPlaying,
                        size = 32.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            // Skip forward 30s
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(48.dp),
            ) {
                IconButton(
                    onClick = { viewModel.skipForward() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = "30",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Speed control with slider
        SpeedControl(
            currentSpeed = state.currentSpeed,
            userSpeed = state.userSpeed,
            isMusicDetected = state.isMusicDetected,
            onIncrement = viewModel::incrementSpeed,
            onDecrement = viewModel::decrementSpeed,
            onSpeedChanged = viewModel::setSpeed,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        // Music detection toggle
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Speed: Music Detection",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Auto-slow to 1x during songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.musicDetectionEnabled,
                    onCheckedChange = { viewModel.toggleMusicDetection() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                    ),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val sec = seconds.toString().padStart(2, '0')
    val min = minutes.toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:$min:$sec"
    } else {
        "$minutes:$sec"
    }
}
