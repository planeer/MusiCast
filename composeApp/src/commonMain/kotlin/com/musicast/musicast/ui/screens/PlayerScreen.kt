package com.musicast.musicast.ui.screens

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musicast.musicast.domain.model.AnalysisStatus
import com.musicast.musicast.domain.model.ContentType
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.domain.model.PlaybackState
import com.musicast.musicast.ui.components.Artwork
import com.musicast.musicast.ui.components.SegmentTimeline
import com.musicast.musicast.ui.components.SkipButton
import com.musicast.musicast.ui.components.SpeedControl
import com.musicast.musicast.ui.theme.AppIcons
import com.musicast.musicast.ui.theme.music
import com.musicast.musicast.ui.theme.musicContainer
import com.musicast.musicast.ui.theme.onMusicContainer
import com.musicast.musicast.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()
    val storedEpisode by viewModel.storedEpisode.collectAsState()
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
        // Outer Scaffold in App.kt already consumed the system-bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                // Outer Scaffold in App.kt already consumed the status-bar
                // inset; TopAppBar defaults to also adding it, which would
                // double the top gap.
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Text(
                        text = "Now playing",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
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
        Spacer(Modifier.height(8.dp))

        Artwork(
            url = state.artworkUrl,
            fallbackText = state.podcastTitle.ifEmpty { episode.title },
            cornerRadius = 20,
            modifier = Modifier.size(280.dp),
        )

        Spacer(Modifier.height(24.dp))

        // Episode title scrolls instead of being cut off
        Text(
            text = episode.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
        )

        // Podcast name
        if (state.podcastTitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.podcastTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Seek bar with music sections marked
        var isSeeking by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableStateOf(0L) }

        if (state.durationMs > 0) {
            SegmentTimeline(
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

            // Time labels
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatTime(if (isSeeking) seekPosition else state.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.isMusicDetected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.music,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Music",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.music,
                        )
                    }
                }
                Text(
                    text = "-" + formatTime((state.durationMs - (if (isSeeking) seekPosition else state.positionMs)).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Transport controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SkipButton(
                icon = AppIcons.Replay,
                seconds = 15,
                contentDescription = "Skip back 15 seconds",
                onClick = viewModel::skipBackward,
                modifier = Modifier.size(56.dp),
            )

            FilledIconButton(
                onClick = viewModel::togglePlayPause,
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    if (state.isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }

            SkipButton(
                icon = AppIcons.Forward,
                seconds = 30,
                contentDescription = "Skip forward 30 seconds",
                onClick = viewModel::skipForward,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        SpeedControl(
            currentSpeed = state.currentSpeed,
            userSpeed = state.userSpeed,
            isMusicDetected = state.isMusicDetected,
            onIncrement = viewModel::incrementSpeed,
            onDecrement = viewModel::decrementSpeed,
            onSpeedChanged = viewModel::setSpeed,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        SmartSpeedCard(
            state = state,
            // Fall back to the playback snapshot until the stored row for this episode loads
            storedEpisode = storedEpisode?.takeIf { it.id == episode.id } ?: episode,
            analysisProgress = analysisProgress[episode.id],
            onToggle = viewModel::toggleMusicDetection,
        )

        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun SmartSpeedCard(
    state: PlaybackState,
    storedEpisode: Episode,
    analysisProgress: Float?,
    onToggle: () -> Unit,
) {
    val musicSections = state.segments.count { it.type == ContentType.MUSIC }
    val status = when {
        !state.musicDetectionEnabled -> "Off · everything plays at your speed"
        state.segments.isNotEmpty() && musicSections == 0 -> "No music found in this episode"
        state.segments.isNotEmpty() -> "Plays $musicSections music sections at 1x"
        analysisProgress != null -> "Analyzing episode… ${(analysisProgress * 100).toInt()}%"
        storedEpisode.downloadPath == null -> "Download this episode to detect music"
        storedEpisode.analysisStatus == AnalysisStatus.FAILED -> "Music analysis failed for this episode"
        else -> "Waiting for music analysis"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.musicContainer,
                contentColor = MaterialTheme.colorScheme.onMusicContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(AppIcons.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Speed",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.musicDetectionEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.music,
                    checkedThumbColor = MaterialTheme.colorScheme.musicContainer,
                ),
            )
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
