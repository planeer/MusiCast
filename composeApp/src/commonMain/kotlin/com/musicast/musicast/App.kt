package com.musicast.musicast

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.musicast.musicast.audio.MusicDetector
import com.musicast.musicast.data.local.LocalDataSource
import com.musicast.musicast.data.repository.PodcastRepository
import com.musicast.musicast.download.EpisodeDownloader
import com.musicast.musicast.player.PlaybackManager
import com.musicast.musicast.ui.components.MiniPlayer
import com.musicast.musicast.ui.navigation.Screen
import com.musicast.musicast.ui.screens.EpisodeListScreen
import com.musicast.musicast.ui.screens.PlayerScreen
import com.musicast.musicast.ui.screens.PodcastListScreen
import com.musicast.musicast.ui.viewmodel.EpisodeListViewModel
import com.musicast.musicast.ui.viewmodel.PlayerViewModel
import com.musicast.musicast.ui.viewmodel.PodcastListViewModel
import org.koin.compose.koinInject

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(),
    ) {
        // Simple back-stack navigation
        val backStack = remember { mutableStateListOf<Screen>(Screen.PodcastList) }
        val currentScreen = backStack.last()

        fun navigateTo(screen: Screen) {
            backStack.add(screen)
        }

        fun goBack() {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }

        val playbackManager = koinInject<PlaybackManager>()
        val repository = koinInject<PodcastRepository>()
        val localDataSource = koinInject<LocalDataSource>()
        val downloader = koinInject<EpisodeDownloader>()
        val musicDetector = koinInject<MusicDetector>()
        val playbackState by playbackManager.state.collectAsState()

        Scaffold(
            bottomBar = {
                if (playbackState.episode != null && currentScreen !is Screen.Player) {
                    MiniPlayer(
                        state = playbackState,
                        onTap = { navigateTo(Screen.Player) },
                        onPlayPause = {
                            if (playbackState.isPlaying) {
                                playbackManager.pause()
                            } else {
                                playbackManager.resume()
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Crossfade(targetState = currentScreen) { screen ->
                    when (screen) {
                        is Screen.PodcastList -> {
                            val viewModel = remember { PodcastListViewModel(repository) }
                            PodcastListScreen(
                                viewModel = viewModel,
                                onPodcastClick = { id, title, artworkUrl ->
                                    navigateTo(Screen.EpisodeList(id, title, artworkUrl))
                                },
                            )
                        }

                        is Screen.EpisodeList -> {
                            val viewModel = remember(screen.podcastId) {
                                EpisodeListViewModel(
                                    podcastId = screen.podcastId,
                                    podcastTitle = screen.podcastTitle,
                                    artworkUrl = screen.artworkUrl,
                                    repository = repository,
                                    localDataSource = localDataSource,
                                    downloader = downloader,
                                    playbackManager = playbackManager,
                                    musicDetector = musicDetector,
                                )
                            }
                            EpisodeListScreen(
                                podcastTitle = screen.podcastTitle,
                                viewModel = viewModel,
                                onBack = { goBack() },
                                onNavigateToPlayer = { navigateTo(Screen.Player) },
                            )
                        }

                        is Screen.Player -> {
                            val viewModel = remember { PlayerViewModel(playbackManager) }
                            PlayerScreen(
                                viewModel = viewModel,
                                onBack = { goBack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
