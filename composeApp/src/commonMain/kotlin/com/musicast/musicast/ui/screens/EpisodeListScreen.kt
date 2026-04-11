package com.musicast.musicast.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicast.musicast.domain.model.AnalysisStatus
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.download.DownloadProgress
import com.musicast.musicast.download.DownloadStatus
import com.musicast.musicast.ui.viewmodel.EpisodeListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    podcastTitle: String,
    viewModel: EpisodeListViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        // Outer Scaffold in App.kt already consumed the system-bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                // Outer Scaffold in App.kt already consumed the status-bar
                // inset; TopAppBar defaults to also adding it, which would
                // double the top gap.
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = podcastTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
        if (state.episodes.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                Text(
                    text = "No episodes found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.episodes, key = { it.id }) { episode ->
                    EpisodeItem(
                        episode = episode,
                        downloadProgress = state.downloads[episode.id],
                        analysisProgress = state.analysisProgress[episode.id],
                        onPlay = {
                            viewModel.playEpisode(episode)
                            onNavigateToPlayer()
                        },
                        onDownload = { viewModel.downloadEpisode(episode) },
                        onDeleteDownload = { viewModel.deleteDownload(episode) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EpisodeItem(
    episode: Episode,
    downloadProgress: DownloadProgress?,
    analysisProgress: Float?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (episode.isPlayed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                // Inline playback progress bar with dot indicator
                if (episode.playbackPositionMs > 0 && !episode.isPlayed && episode.durationMs != null && episode.durationMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    val progress = (episode.playbackPositionMs.toFloat() / episode.durationMs).coerceIn(0f, 1f)
                    val progressColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp),
                        ) {
                            val trackHeight = 3.dp.toPx()
                            val centerY = size.height / 2f
                            val dotRadius = 4.dp.toPx()
                            val progressX = progress * size.width

                            // Track background
                            drawRect(
                                color = trackColor,
                                topLeft = Offset(0f, centerY - trackHeight / 2f),
                                size = Size(size.width, trackHeight),
                            )
                            // Active track
                            drawRect(
                                color = progressColor,
                                topLeft = Offset(0f, centerY - trackHeight / 2f),
                                size = Size(progressX, trackHeight),
                            )
                            // Dot indicator
                            drawCircle(
                                color = progressColor,
                                radius = dotRadius,
                                center = Offset(progressX, centerY),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        val posMin = episode.playbackPositionMs / 60000
                        val totalMin = episode.durationMs / 60000
                        Text(
                            text = "${posMin}min / ${totalMin}min",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Duration (only show if no progress bar shown above)
                    if (episode.playbackPositionMs <= 0 || episode.isPlayed) {
                        episode.durationMs?.let { ms ->
                            val minutes = ms / 60000
                            Text(
                                text = "${minutes}min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }

                    // Download/analysis status
                    when {
                        downloadProgress?.status == DownloadStatus.DOWNLOADING -> {
                            Text(
                                text = "Downloading...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        episode.downloadPath != null -> {
                            Text(
                                text = "Downloaded",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            when (episode.analysisStatus) {
                                AnalysisStatus.COMPLETED -> {
                                    Text(
                                        text = " \u00B7 Analyzed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                AnalysisStatus.IN_PROGRESS -> {
                                    val pct = analysisProgress?.let { (it * 100).toInt() } ?: 0
                                    Text(
                                        text = " \u00B7 Analyzing $pct%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                AnalysisStatus.FAILED -> {
                                    Text(
                                        text = " \u00B7 Analysis failed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action buttons
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (episode.downloadPath == null && downloadProgress == null) {
                    FilledTonalIconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text(
                            text = "\u2B07",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                } else if (episode.downloadPath != null) {
                    FilledTonalIconButton(
                        onClick = onDeleteDownload,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text(
                            text = "\u2715",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }

        // Download progress bar
        if (downloadProgress?.status == DownloadStatus.DOWNLOADING && downloadProgress.totalBytes > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = {
                    (downloadProgress.bytesDownloaded.toFloat() / downloadProgress.totalBytes).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }

        // Analysis progress bar
        if (analysisProgress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { analysisProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
