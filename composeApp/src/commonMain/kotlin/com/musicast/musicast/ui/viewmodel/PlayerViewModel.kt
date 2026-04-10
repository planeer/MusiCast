package com.musicast.musicast.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.musicast.musicast.domain.model.PlaybackState
import com.musicast.musicast.player.PlaybackManager
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(
    private val playbackManager: PlaybackManager,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playbackManager.state

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
