package com.musicast.musicast.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicast.musicast.audio.MusicDetector
import com.musicast.musicast.data.local.LocalDataSource
import com.musicast.musicast.data.repository.PodcastRepository
import com.musicast.musicast.domain.model.AnalysisStatus
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.download.DownloadProgress
import com.musicast.musicast.download.DownloadStatus
import com.musicast.musicast.download.EpisodeDownloader
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
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class EpisodeListViewModel(
    private val podcastId: Long,
    private val feedUrl: String = "",
    private val podcastTitle: String = "",
    private val artworkUrl: String? = null,
    private val repository: PodcastRepository,
    private val localDataSource: LocalDataSource,
    private val downloader: EpisodeDownloader,
    private val playbackManager: PlaybackManager,
    private val musicDetector: MusicDetector,
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
            downloader.activeDownloads.collect { downloads ->
                _state.update { it.copy(downloads = downloads) }
            }
        }
    }

    fun playEpisode(episode: Episode) {
        playbackManager.playEpisode(episode, podcastTitle, artworkUrl)

        if (episode.downloadPath != null) {
            when (episode.analysisStatus) {
                AnalysisStatus.COMPLETED -> {
                    // Load previously analyzed segments from DB
                    viewModelScope.launch {
                        val segments = localDataSource.loadSegments(episode.id)
                        if (segments != null && segments.isNotEmpty()) {
                            val currentEpisode = playbackManager.state.value.episode
                            if (currentEpisode?.id == episode.id) {
                                playbackManager.setSegments(segments)
                            }
                        }
                    }
                }
                AnalysisStatus.NONE -> analyzeEpisode(episode)
                else -> {} // IN_PROGRESS or FAILED — do nothing
            }
        }
    }

    fun downloadEpisode(episode: Episode) {
        viewModelScope.launch {
            downloader.download(episode.id, episode.audioUrl).collect { progress ->
                if (progress.status == DownloadStatus.COMPLETED) {
                    val localPath = downloader.getLocalPath(episode.id)
                    if (localPath != null) {
                        localDataSource.updateDownloadPath(episode.id, localPath)
                        // Auto-analyze after download
                        analyzeEpisode(episode.copy(downloadPath = localPath))
                    }
                }
            }
        }
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

    fun deleteDownload(episode: Episode) {
        downloader.deleteDownload(episode.id)
        localDataSource.updateDownloadPath(episode.id, null)
        localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.NONE)
        localDataSource.clearSegmentsData(episode.id)
        _state.update { it.copy(analysisProgress = it.analysisProgress - episode.id) }
    }

    private fun analyzeEpisode(episode: Episode) {
        val path = episode.downloadPath ?: return
        viewModelScope.launch {
            localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.IN_PROGRESS)
            _state.update { it.copy(analysisProgress = it.analysisProgress + (episode.id to 0f)) }

            val segments = musicDetector.analyzeFile(path) { progress ->
                _state.update {
                    it.copy(analysisProgress = it.analysisProgress + (episode.id to progress))
                }
            }

            _state.update { it.copy(analysisProgress = it.analysisProgress - episode.id) }

            if (segments != null) {
                localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.COMPLETED)
                localDataSource.saveSegments(episode.id, segments)
                val currentEpisode = playbackManager.state.value.episode
                if (currentEpisode?.id == episode.id) {
                    playbackManager.setSegments(segments)
                }
            } else {
                localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.FAILED)
            }
        }
    }
}
