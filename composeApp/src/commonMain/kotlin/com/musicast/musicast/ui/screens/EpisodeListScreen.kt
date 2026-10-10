package com.musicast.musicast.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicast.musicast.domain.model.AnalysisStatus
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.download.DownloadProgress
import com.musicast.musicast.download.DownloadStatus
import com.musicast.musicast.ui.components.formatDuration
import com.musicast.musicast.ui.components.formatPublishDate
import com.musicast.musicast.ui.theme.AppIcons
import com.musicast.musicast.ui.theme.music
import com.musicast.musicast.ui.viewmodel.EpisodeListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    podcastTitle: String,
    viewModel: EpisodeListViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var confirmUnfollow by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        val error = state.error
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    fun removeDownload(episode: Episode) {
        // One undo at a time, so the visible Undo always belongs to the latest removal
        snackbarHostState.currentSnackbarData?.dismiss()
        viewModel.removeDownload(episode)
        scope.launch {
            var undone = false
            try {
                undone = snackbarHostState.showSnackbar(
                    message = "Removed \"${episode.title}\"",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short,
                ) == SnackbarResult.ActionPerformed
            } finally {
                // Leaving the screen also commits the removal
                if (undone) viewModel.undoRemoveDownload(episode) else viewModel.confirmRemoveDownload(episode)
            }
        }
    }

    Scaffold(
        // Outer Scaffold in App.kt already consumed the system-bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                // Outer Scaffold in App.kt already consumed the status-bar
                // inset; TopAppBar defaults to also adding it, which would
                // double the top gap.
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = podcastTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(AppIcons.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Refresh feed") },
                                leadingIcon = { Icon(AppIcons.Refresh, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.refreshFeed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Unfollow podcast") },
                                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    confirmUnfollow = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refreshFeed,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.episodes.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = "No episodes found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.episodes, key = { it.id }) { episode ->
                        EpisodeItem(
                            episode = episode,
                            isDownloaded = episode.downloadPath != null && episode.id !in state.pendingDownloadRemovals,
                            downloadProgress = state.downloads[episode.id],
                            analysisProgress = state.analysisProgress[episode.id],
                            onPlay = {
                                viewModel.playEpisode(episode)
                                if (episode.id in state.pendingDownloadRemovals) {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                }
                                onNavigateToPlayer()
                            },
                            onDownload = {
                                viewModel.downloadEpisode(episode)
                                if (episode.id in state.pendingDownloadRemovals) {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                }
                            },
                            onRemoveDownload = { removeDownload(episode) },
                            onRetryAnalysis = { viewModel.retryAnalysis(episode) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (confirmUnfollow) {
        AlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text("Unfollow podcast?") },
            text = { Text("\"$podcastTitle\" and its downloaded episodes will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnfollow = false
                    viewModel.deletePodcast()
                    onBack()
                }) {
                    Text("Unfollow", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnfollow = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EpisodeItem(
    episode: Episode,
    isDownloaded: Boolean,
    downloadProgress: DownloadProgress?,
    analysisProgress: Float?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onRetryAnalysis: () -> Unit,
) {
    // PENDING (still connecting) counts too, so the Download button can't be tapped twice
    val isDownloading = downloadProgress?.status == DownloadStatus.PENDING ||
        downloadProgress?.status == DownloadStatus.DOWNLOADING
    val inProgress = episode.playbackPositionMs > 0 && !episode.isPlayed &&
        episode.durationMs != null && episode.durationMs > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                // Date · duration
                val meta = listOfNotNull(
                    episode.publishDate?.let(::formatPublishDate),
                    if (inProgress) {
                        formatDuration(episode.durationMs!! - episode.playbackPositionMs)?.let { "$it left" }
                    } else {
                        formatDuration(episode.durationMs)
                    },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (episode.isPlayed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                if (inProgress) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (episode.playbackPositionMs.toFloat() / episode.durationMs!!).coerceIn(0f, 1f) },
                        modifier = Modifier.width(120.dp).height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                }

                if (isDownloaded) {
                    Spacer(Modifier.height(6.dp))
                    DownloadStatusLine(episode.analysisStatus, analysisProgress)
                }
            }

            Spacer(Modifier.width(4.dp))

            // Trailing action
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                when {
                    isDownloading -> {
                        val total = downloadProgress!!.totalBytes
                        if (total > 0) {
                            CircularProgressIndicator(
                                progress = { (downloadProgress.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        }
                    }
                    isDownloaded -> DownloadedMenu(
                        canRetryAnalysis = episode.analysisStatus == AnalysisStatus.FAILED && analysisProgress == null,
                        onRemoveDownload = onRemoveDownload,
                        onRetryAnalysis = onRetryAnalysis,
                    )
                    else -> IconButton(onClick = onDownload) {
                        Icon(
                            AppIcons.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (analysisProgress != null && isDownloaded) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { analysisProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp).height(3.dp),
                color = MaterialTheme.colorScheme.music,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun DownloadStatusLine(analysisStatus: AnalysisStatus, analysisProgress: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusLabel(AppIcons.DownloadDone, "Downloaded", MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        when {
            analysisProgress != null -> StatusLabel(
                AppIcons.MusicNote,
                "Finding music ${(analysisProgress * 100).toInt()}%",
                MaterialTheme.colorScheme.music,
            )
            analysisStatus == AnalysisStatus.COMPLETED ->
                StatusLabel(AppIcons.MusicNote, "Smart Speed ready", MaterialTheme.colorScheme.music)
            analysisStatus == AnalysisStatus.FAILED ->
                StatusLabel(AppIcons.ErrorOutline, "Analysis failed", MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusLabel(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun DownloadedMenu(
    canRetryAnalysis: Boolean,
    onRemoveDownload: () -> Unit,
    onRetryAnalysis: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                AppIcons.MoreVert,
                contentDescription = "Episode options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canRetryAnalysis) {
                DropdownMenuItem(
                    text = { Text("Retry music analysis") },
                    leadingIcon = { Icon(AppIcons.Refresh, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRetryAnalysis()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove download") },
                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRemoveDownload()
                },
            )
        }
    }
}
