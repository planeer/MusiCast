package com.musicast.musicast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musicast.musicast.player.AndroidAudioPlayer
import com.musicast.musicast.player.AudioPlayer
import com.musicast.musicast.player.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val audioPlayer: AudioPlayer by inject()
    private val playbackManager: PlaybackManager by inject()

    companion object {
        const val CMD_TOGGLE_SPEED = "TOGGLE_SPEED"
        const val CMD_SKIP_BACK = "SKIP_BACK"
        const val CMD_SKIP_FORWARD = "SKIP_FORWARD"
        private val SPEED_STEPS = floatArrayOf(1.0f, 1.2f, 1.5f, 1.8f, 2.0f, 2.5f, 3.0f, 0.5f, 0.8f)

        fun formatSpeed(speed: Float): String {
            return if (speed == speed.toInt().toFloat()) {
                "${speed.toInt()}x"
            } else {
                "${String.format("%.1f", speed)}x"
            }
        }

        fun speedToIconRes(speed: Float): Int {
            val rounded = Math.round(speed * 10f) / 10f
            return when (rounded) {
                0.5f -> R.drawable.ic_speed_0_5x
                0.8f -> R.drawable.ic_speed_0_8x
                1.0f -> R.drawable.ic_speed_1x
                1.2f -> R.drawable.ic_speed_1_2x
                1.5f -> R.drawable.ic_speed_1_5x
                1.8f -> R.drawable.ic_speed_1_8x
                2.0f -> R.drawable.ic_speed_2x
                2.5f -> R.drawable.ic_speed_2_5x
                3.0f -> R.drawable.ic_speed_3x
                else -> R.drawable.ic_speed
            }
        }

        fun createSpeedBitmap(speedText: String): Bitmap {
            val size = 128
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = 44f
            }

            val x = size / 2f
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(speedText, x, y, paint)

            return bitmap
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d("PlaybackService", "onCreate called")

        // Set custom notification provider BEFORE creating the session
        setMediaNotificationProvider(SpeedAwareNotificationProvider(this))

        val exoPlayer = (audioPlayer as AndroidAudioPlayer).player

        // Set metadata on the MediaItem as soon as it's loaded, BEFORE playback
        // starts. This ensures the MediaSession reports the correct info immediately,
        // which is needed for Samsung's Now Bar / Dynamic Island to pick it up.
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                @Player.MediaItemTransitionReason reason: Int,
            ) {
                if (mediaItem != null && mediaItem.mediaMetadata.title == null) {
                    val state = playbackManager.state.value
                    val episode = state.episode ?: return
                    val metadataBuilder = MediaMetadata.Builder()
                        .setTitle(episode.title)
                        .setArtist(state.podcastTitle)
                    if (state.artworkUrl != null) {
                        metadataBuilder.setArtworkUri(android.net.Uri.parse(state.artworkUrl))
                    }
                    val updated = mediaItem.buildUpon()
                        .setMediaMetadata(metadataBuilder.build())
                        .build()
                    exoPlayer.replaceMediaItem(exoPlayer.currentMediaItemIndex, updated)
                }
            }
        })

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(PlaybackSessionCallback())
            .build()

        mediaSession = session
        Log.d("PlaybackService", "MediaSession created")
        updateCustomLayout(playbackManager.state.value.currentSpeed)

        // Observe speed changes to update the speed button label
        serviceScope.launch {
            playbackManager.state
                .map { it.currentSpeed }
                .distinctUntilChanged()
                .collect { speed ->
                    updateCustomLayout(speed)
                }
        }

        // Observe episode changes to update metadata (for changes during playback)
        serviceScope.launch {
            playbackManager.state
                .map { Triple(it.episode?.title, it.podcastTitle, it.artworkUrl) }
                .distinctUntilChanged()
                .collect { (title, podcastTitle, artworkUrl) ->
                    if (title != null) {
                        updateMetadata(title, podcastTitle ?: "", artworkUrl)
                    }
                }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PlaybackService", "onStartCommand called")
        val session = mediaSession
        if (session != null) {
            // Ensure notification channel exists
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                "playback_channel",
                "Playback",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)

            val state = playbackManager.state.value
            // Post a proper MediaStyle foreground notification with the session token.
            // This ensures the service is truly foreground, which is required for
            // Samsung's Now Bar / Dynamic Island to show our session.
            // Media3's MediaNotificationManager will replace this with the full
            // notification (with action buttons) shortly after.
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            val contentIntent = PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(this, "playback_channel")
                .setSmallIcon(R.drawable.ic_speed)
                .setContentTitle(state.episode?.title ?: "Playing")
                .setContentText(state.podcastTitle)
                .setContentIntent(contentIntent)
                .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
                .setSilent(true)
                .setOngoing(true)
                .build()

            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateCustomLayout(currentSpeed: Float) {
        val session = mediaSession ?: return
        session.setCustomLayout(buildCustomLayout(currentSpeed))
    }

    private fun updateMetadata(title: String, artist: String, artworkUrl: String?) {
        val session = mediaSession ?: return
        val player = session.player

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)

        if (artworkUrl != null) {
            metadataBuilder.setArtworkUri(android.net.Uri.parse(artworkUrl))
        }

        val currentItem = player.currentMediaItem
        if (currentItem != null) {
            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(metadataBuilder.build())
                .build()
            player.replaceMediaItem(0, updatedItem)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            // Don't release the player — it's a Koin singleton shared with the app
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun buildCustomLayout(currentSpeed: Float): List<CommandButton> {
        val speedLabel = formatSpeed(currentSpeed)

        val skipBackButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
            .setDisplayName("-15s")
            .setIconResId(R.drawable.ic_replay_15)
            .setSessionCommand(SessionCommand(CMD_SKIP_BACK, Bundle.EMPTY))
            .build()

        val skipForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setDisplayName("+30s")
            .setIconResId(R.drawable.ic_forward_30)
            .setSessionCommand(SessionCommand(CMD_SKIP_FORWARD, Bundle.EMPTY))
            .build()

        val speedButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(speedLabel)
            .setIconResId(speedToIconRes(currentSpeed))
            .setSessionCommand(SessionCommand(CMD_TOGGLE_SPEED, Bundle.EMPTY))
            .build()

        // Order: -15s, [play/pause auto-inserted], +30s, speed
        return listOf(skipBackButton, skipForwardButton, speedButton)
    }

    /**
     * Custom notification provider that renders the speed button with a dynamically
     * generated bitmap showing the current speed text (e.g., "1.5x").
     */
    @UnstableApi
    private class SpeedAwareNotificationProvider(
        private val appContext: Context,
    ) : DefaultMediaNotificationProvider(appContext) {

        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: ImmutableList<CommandButton>,
            builder: NotificationCompat.Builder,
            actionFactory: MediaNotification.ActionFactory,
        ): IntArray {
            val compactIndices = mutableListOf<Int>()
            var actionIndex = 0

            for (button in mediaButtons) {
                val sessionCommand = button.sessionCommand

                if (sessionCommand != null) {
                    // Custom session command (skip back, skip forward, speed)
                    val icon = if (sessionCommand.customAction == CMD_TOGGLE_SPEED) {
                        val speedText = button.displayName?.toString() ?: "1x"
                        IconCompat.createWithBitmap(createSpeedBitmap(speedText))
                    } else {
                        IconCompat.createWithResource(appContext, button.iconResId)
                    }

                    builder.addAction(
                        actionFactory.createCustomAction(
                            mediaSession,
                            icon,
                            button.displayName ?: "",
                            sessionCommand.customAction,
                            Bundle.EMPTY,
                        )
                    )

                    // Include skip buttons in compact view, not speed
                    if (sessionCommand.customAction != CMD_TOGGLE_SPEED
                        && compactIndices.size < 3
                    ) {
                        compactIndices.add(actionIndex)
                    }
                    actionIndex++

                } else if (button.playerCommand != Player.COMMAND_INVALID) {
                    // Player command (play/pause)
                    builder.addAction(
                        actionFactory.createMediaAction(
                            mediaSession,
                            IconCompat.createWithResource(appContext, button.iconResId),
                            button.displayName ?: "",
                            button.playerCommand,
                        )
                    )

                    // Always include play/pause in compact view
                    if (compactIndices.size < 3) {
                        compactIndices.add(actionIndex)
                    }
                    actionIndex++
                }
            }

            return compactIndices.toIntArray()
        }
    }

    private inner class PlaybackSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_TOGGLE_SPEED, Bundle.EMPTY))
                .add(SessionCommand(CMD_SKIP_BACK, Bundle.EMPTY))
                .add(SessionCommand(CMD_SKIP_FORWARD, Bundle.EMPTY))
                .build()

            // Remove previous/next track buttons
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(buildCustomLayout(playbackManager.state.value.currentSpeed))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_TOGGLE_SPEED -> {
                    val currentSpeed = playbackManager.state.value.userSpeed
                    val nextSpeed = getNextSpeed(currentSpeed)
                    playbackManager.setUserSpeed(nextSpeed)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_SKIP_BACK -> {
                    playbackManager.skipBackward()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_SKIP_FORWARD -> {
                    playbackManager.skipForward()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        private fun getNextSpeed(current: Float): Float {
            val rounded = (Math.round(current * 10f) / 10f)
            for (i in SPEED_STEPS.indices) {
                if (Math.abs(SPEED_STEPS[i] - rounded) < 0.05f) {
                    return SPEED_STEPS[(i + 1) % SPEED_STEPS.size]
                }
            }
            return 1.0f
        }
    }
}
