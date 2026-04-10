package com.musicast.musicast.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.musicast.musicast.domain.model.Podcast
import com.musicast.musicast.ui.components.AddPodcastDialog
import com.musicast.musicast.ui.viewmodel.PodcastListViewModel

@Composable
fun PodcastListScreen(
    viewModel: PodcastListViewModel,
    onPodcastClick: (podcastId: Long, podcastTitle: String, artworkUrl: String?) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        var podcastToDelete by remember { mutableStateOf<Podcast?>(null) }

        if (state.podcasts.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No podcasts yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tap + to add a podcast feed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item {
                    Text(
                        text = "Podcast Library",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Followed Shows",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }
                items(state.podcasts, key = { it.id }) { podcast ->
                    PodcastItem(
                        podcast = podcast,
                        onClick = { onPodcastClick(podcast.id, podcast.title, podcast.artworkUrl) },
                        onLongClick = { podcastToDelete = podcast },
                    )
                }
            }
        }

        // Delete confirmation dialog
        podcastToDelete?.let { podcast ->
            AlertDialog(
                onDismissRequest = { podcastToDelete = null },
                title = { Text("Delete Podcast") },
                text = { Text("Remove \"${podcast.title}\" and all its episodes?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deletePodcast(podcast.id)
                        podcastToDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { podcastToDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }

    if (state.showAddDialog) {
        AddPodcastDialog(
            url = state.addPodcastUrl,
            isLoading = state.isLoading,
            error = state.error,
            onUrlChanged = viewModel::onUrlChanged,
            onConfirm = viewModel::addPodcast,
            onDismiss = viewModel::dismissAddDialog,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastItem(
    podcast: Podcast,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            // Artwork
            if (podcast.artworkUrl != null) {
                AsyncImage(
                    model = podcast.artworkUrl,
                    contentDescription = podcast.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier.size(80.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = podcast.title.take(2).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (podcast.description.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = podcast.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
