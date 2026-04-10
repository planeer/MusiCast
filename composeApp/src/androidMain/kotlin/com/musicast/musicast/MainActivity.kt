package com.musicast.musicast

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.musicast.musicast.App
import com.musicast.musicast.player.PlaybackManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val playbackManager: PlaybackManager by inject()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        setContent {
            App()
        }

        // Connect a MediaController to the PlaybackService.
        // This binds and creates the service. Media3's MediaNotificationManager
        // internally handles startForegroundService + startForeground when the
        // player starts playing, posting the proper media notification.
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            { /* controller connected */ },
            MoreExecutors.directExecutor()
        )

        // When playback starts, explicitly start the service as a foreground service.
        // This ensures startForeground() is called via onStartCommand(), which is
        // required for Samsung's Now Bar / Dynamic Island to recognize the session.
        lifecycleScope.launch {
            playbackManager.state
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { isPlaying ->
                    if (isPlaying) {
                        val intent = Intent(this@MainActivity, PlaybackService::class.java)
                        startForegroundService(intent)
                    }
                }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
