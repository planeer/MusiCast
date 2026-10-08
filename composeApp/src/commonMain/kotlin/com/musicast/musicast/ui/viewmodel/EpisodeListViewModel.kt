package com.musicast.musicast.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicast.musicast.data.repository.PodcastRepository
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.download.DownloadProgress
import com.musicast.musicast.download.EpisodeProcessor
import com.musicast.musicast.player.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodeListState(
    val episodes: List<Episode> = emptyList(),
    val downloads: Map<Long, DownloadProgress> = emptyMap(),
    val analysisProgress: Map<Long, Float> = emptyMap(),
    /** Downloads the user removed but can still undo; hidden until confirmed. */
    val pendingDownloadRemovals: Set<Long> = emptySet(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class EpisodeListViewModel(
    private val podcastId: Long,
    private val feedUrl: String = "",
    private val podcastTitle: String = "",
    private val artworkUrl: String? = null,
    private val repository: PodcastRepository,
    private val playbackManager: PlaybackManager,
    private val processor: EpisodeProcessor,
) : ViewModel() {

    private val _state = MutableStateFlow(EpisodeListState())
    val state: StateFlow<EpisodeListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getEpisodes(podcastId).collect { episodes ->
                _state.update { it.copy(episodes = episodes) }
            }
        }
        viewModelScope.launch {
            processor.activeDownloads.collect { downloads ->
                _state.update { it.copy(downloads = downloads) }
            }
        }
        viewModelScope.launch {
            processor.analysisProgress.collect { progress ->
                _state.update { it.copy(analysisProgress = progress) }
            }
        }
    }

    fun playEpisode(episode: Episode) {
        playbackManager.playEpisode(episode, podcastTitle, artworkUrl)
        processor.prepareForPlayback(episode)
    }

    fun downloadEpisode(episode: Episode) {
        processor.download(episode)
    }

    fun retryAnalysis(episode: Episode) {
        processor.analyze(episode)
    }

    /** Hides the download immediately; call [confirmRemoveDownload] or [undoRemoveDownload] next. */
    fun removeDownload(episode: Episode) {
        _state.update { it.copy(pendingDownloadRemovals = it.pendingDownloadRemovals + episode.id) }
    }

    fun undoRemoveDownload(episode: Episode) {
        _state.update { it.copy(pendingDownloadRemovals = it.pendingDownloadRemovals - episode.id) }
    }

    fun confirmRemoveDownload(episode: Episode) {
        if (episode.id !in _state.value.pendingDownloadRemovals) return
        processor.deleteDownload(episode)
        _state.update { it.copy(pendingDownloadRemovals = it.pendingDownloadRemovals - episode.id) }
    }

    fun deletePodcast() {
        repository.deletePodcast(podcastId)
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            repository.refreshPodcast(podcastId, feedUrl)
                .onFailure { _state.update { it.copy(error = "Failed to refresh feed") } }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
