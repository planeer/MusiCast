package com.musicast.musicast.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol

class IosAudioPlayer : AudioPlayer {

    private var player: AVPlayer? = null
    private var timeObserver: Any? = null
    private var endObserver: NSObjectProtocol? = null
    private var currentSpeed: Float = 1.0f

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private fun setupPlayer(url: NSURL) {
        release()
        val item = AVPlayerItem(uRL = url)
        val avPlayer = AVPlayer(playerItem = item)
        player = avPlayer

        // Periodic time observer every 250ms
        val interval = CMTimeMake(value = 250, timescale = 1000)
        timeObserver = avPlayer.addPeriodicTimeObserverForInterval(interval, queue = null) { time ->
            val pos = (CMTimeGetSeconds(time) * 1000).toLong().coerceAtLeast(0L)
            _positionMs.value = pos
            _isPlaying.value = avPlayer.timeControlStatus == AVPlayerTimeControlStatusPlaying

            val dur = avPlayer.currentItem?.duration
            if (dur != null) {
                val durMs = (CMTimeGetSeconds(dur) * 1000).toLong()
                if (durMs > 0) _durationMs.value = durMs
            }
        }

        // End-of-track observer
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            _isPlaying.value = false
        }
    }

    override fun play(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        setupPlayer(nsUrl)
        player?.rate = currentSpeed
        player?.play()
    }

    override fun playLocal(path: String) {
        val nsUrl = NSURL.fileURLWithPath(path)
        setupPlayer(nsUrl)
        player?.rate = currentSpeed
        player?.play()
    }

    override fun pause() {
        player?.pause()
        _isPlaying.value = false
    }

    override fun resume() {
        player?.rate = currentSpeed
        player?.play()
        _isPlaying.value = true
    }

    override fun stop() {
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        _positionMs.value = 0L
        _isPlaying.value = false
    }

    override fun seekTo(positionMs: Long) {
        val time = CMTimeMake(value = positionMs, timescale = 1000)
        player?.seekToTime(time)
        _positionMs.value = positionMs
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
        if (_isPlaying.value) {
            player?.rate = speed
        }
    }

    override fun release() {
        timeObserver?.let { player?.removeTimeObserver(it) }
        timeObserver = null
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        player?.pause()
        player = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
    }
}
