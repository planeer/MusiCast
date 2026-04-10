package com.musicast.musicast.player

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>

    fun play(url: String)
    fun playLocal(path: String)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun release()
}
