package com.musicast.musicast.player

import com.musicast.musicast.data.local.LocalDataSource
import com.musicast.musicast.domain.model.AudioSegment
import com.musicast.musicast.domain.model.ContentType
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.domain.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaybackManager(
    private val audioPlayer: AudioPlayer,
    private val localDataSource: LocalDataSource,
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var positionTrackingJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var segments: List<AudioSegment> = emptyList()
    private var lastSavedPositionMs: Long = 0L

    init {
        // Restore saved user speed
        val savedSpeed = localDataSource.getSetting(KEY_USER_SPEED)?.toFloatOrNull()
        if (savedSpeed != null) {
            val clamped = savedSpeed.coerceIn(0.5f, 3.0f)
            _state.update { it.copy(userSpeed = clamped, currentSpeed = clamped) }
        }

        // Forward player state changes
        positionTrackingJob = scope.launch {
            launch {
                audioPlayer.positionMs.collect { pos ->
                    _state.update { it.copy(positionMs = pos) }
                    checkMusicDetection(pos)
                    maybeSavePosition(pos)
                }
            }
            launch {
                audioPlayer.durationMs.collect { dur ->
                    _state.update { it.copy(durationMs = dur) }
                }
            }
            launch {
                audioPlayer.isPlaying.collect { playing ->
                    _state.update { it.copy(isPlaying = playing) }
                    // Save position when pausing
                    if (!playing) {
                        savePosition()
                    }
                }
            }
        }
    }

    fun playEpisode(episode: Episode, podcastTitle: String = "", artworkUrl: String? = null) {
        // Save position of previously playing episode
        savePosition()

        segments = emptyList()
        lastSavedPositionMs = episode.playbackPositionMs
        _state.update {
            it.copy(
                episode = episode,
                positionMs = episode.playbackPositionMs,
                isMusicDetected = false,
                segments = emptyList(),
                podcastTitle = podcastTitle,
                artworkUrl = artworkUrl,
            )
        }

        val url = episode.downloadPath ?: episode.audioUrl
        if (episode.downloadPath != null) {
            audioPlayer.playLocal(url)
        } else {
            audioPlayer.play(url)
        }

        if (episode.playbackPositionMs > 0) {
            audioPlayer.seekTo(episode.playbackPositionMs)
        }

        audioPlayer.setSpeed(_state.value.currentSpeed)
    }

    fun pause() {
        audioPlayer.pause()
    }

    fun resume() {
        audioPlayer.resume()
    }

    fun stop() {
        savePosition()
        audioPlayer.stop()
        segments = emptyList()
        _state.update {
            it.copy(
                episode = null,
                positionMs = 0L,
                durationMs = 0L,
                isPlaying = false,
                isMusicDetected = false,
            )
        }
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun skipForward(ms: Long = 30_000L) {
        val newPos = (_state.value.positionMs + ms).coerceAtMost(_state.value.durationMs)
        seekTo(newPos)
    }

    fun skipBackward(ms: Long = 15_000L) {
        val newPos = (_state.value.positionMs - ms).coerceAtLeast(0L)
        seekTo(newPos)
    }

    fun setUserSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        _state.update { it.copy(userSpeed = clamped) }

        // Only apply if we're not in a music section (or detection is off)
        if (!_state.value.isMusicDetected) {
            _state.update { it.copy(currentSpeed = clamped) }
            audioPlayer.setSpeed(clamped)
        }

        // Persist so it survives app restart
        scope.launch(Dispatchers.Default) {
            localDataSource.setSetting(KEY_USER_SPEED, clamped.toString())
        }
    }

    fun incrementSpeed() {
        val newSpeed = ((_state.value.userSpeed * 10).toInt() + 1).toFloat() / 10f
        setUserSpeed(newSpeed)
    }

    fun decrementSpeed() {
        val newSpeed = ((_state.value.userSpeed * 10).toInt() - 1).toFloat() / 10f
        setUserSpeed(newSpeed)
    }

    fun toggleMusicDetection() {
        val enabled = !_state.value.musicDetectionEnabled
        _state.update { it.copy(musicDetectionEnabled = enabled) }

        if (!enabled && _state.value.isMusicDetected) {
            // Turning off detection while music is detected -> restore user speed
            _state.update { it.copy(isMusicDetected = false, currentSpeed = it.userSpeed) }
            audioPlayer.setSpeed(_state.value.userSpeed)
        }
    }

    fun setActiveEpisodeFromExternal(episode: Episode, podcastTitle: String, artworkUrl: String?) {
        savePosition()
        segments = emptyList()
        lastSavedPositionMs = episode.playbackPositionMs
        _state.update {
            it.copy(
                episode = episode,
                positionMs = episode.playbackPositionMs,
                isMusicDetected = false,
                segments = emptyList(),
                podcastTitle = podcastTitle,
                artworkUrl = artworkUrl,
            )
        }
        audioPlayer.setSpeed(_state.value.currentSpeed)
    }

    fun setSegments(newSegments: List<AudioSegment>) {
        segments = newSegments
        _state.update { it.copy(segments = newSegments) }
    }

    private fun checkMusicDetection(positionMs: Long) {
        if (!_state.value.musicDetectionEnabled || segments.isEmpty()) return

        val currentType = getContentTypeAt(positionMs)
        val wasMusic = _state.value.isMusicDetected
        val isMusic = currentType == ContentType.MUSIC

        if (isMusic != wasMusic) {
            _state.update { it.copy(isMusicDetected = isMusic) }
            val newSpeed = if (isMusic) 1.0f else _state.value.userSpeed
            _state.update { it.copy(currentSpeed = newSpeed) }
            audioPlayer.setSpeed(newSpeed)
        }
    }

    private fun getContentTypeAt(positionMs: Long): ContentType {
        return segments.firstOrNull { positionMs in it.startMs..it.endMs }?.type
            ?: ContentType.SPEECH
    }

    /**
     * Save position to DB every 10 seconds of playback progress.
     */
    private fun maybeSavePosition(positionMs: Long) {
        if (positionMs - lastSavedPositionMs > 10_000L || positionMs < lastSavedPositionMs) {
            savePosition()
        }
    }

    private fun savePosition() {
        val episode = _state.value.episode ?: return
        val pos = _state.value.positionMs
        if (pos > 0 && pos != lastSavedPositionMs) {
            lastSavedPositionMs = pos
            scope.launch(Dispatchers.Default) {
                localDataSource.updatePlaybackPosition(episode.id, pos)
            }
        }
    }

    fun release() {
        savePosition()
        positionTrackingJob?.cancel()
        audioPlayer.release()
    }

    companion object {
        private const val KEY_USER_SPEED = "user_speed"
    }
}
