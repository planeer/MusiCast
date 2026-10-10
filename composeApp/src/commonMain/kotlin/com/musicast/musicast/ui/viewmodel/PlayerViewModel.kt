package com.musicast.musicast.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicast.musicast.data.repository.PodcastRepository
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.domain.model.PlaybackState
import com.musicast.musicast.download.EpisodeProcessor
import com.musicast.musicast.player.PlaybackManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PlayerViewModel(
    private val playbackManager: PlaybackManager,
    processor: EpisodeProcessor,
    repository: PodcastRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playbackManager.state
    val analysisProgress: StateFlow<Map<Long, Float>> = processor.analysisProgress

    /**
     * The playing episode as currently stored. [PlaybackState.episode] is a snapshot
     * from when playback started, so it misses later downloads and analysis results.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val storedEpisode: StateFlow<Episode?> = playbackManager.state
        .map { it.episode?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.getEpisodeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun togglePlayPause() {
        if (state.value.isPlaying) {
            playbackManager.pause()
        } else {
            playbackManager.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun skipForward() {
        playbackManager.skipForward()
    }

    fun skipBackward() {
        playbackManager.skipBackward()
    }

    fun setSpeed(speed: Float) {
        playbackManager.setUserSpeed(speed)
    }

    fun incrementSpeed() {
        playbackManager.incrementSpeed()
    }

    fun decrementSpeed() {
        playbackManager.decrementSpeed()
    }

    fun toggleMusicDetection() {
        playbackManager.toggleMusicDetection()
    }
}
