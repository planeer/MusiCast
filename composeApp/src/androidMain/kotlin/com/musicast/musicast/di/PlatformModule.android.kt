package com.musicast.musicast.di

import com.musicast.musicast.audio.AndroidYamNetClassifier
import com.musicast.musicast.audio.YamNetClassifier
import com.musicast.musicast.data.local.DatabaseDriverFactory
import com.musicast.musicast.download.EpisodeDownloader
import com.musicast.musicast.player.AndroidAudioPlayer
import com.musicast.musicast.player.AudioPlayer
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(get()).createDriver() }
    single<AudioPlayer> { AndroidAudioPlayer(get()) }
    single { EpisodeDownloader(get()) }
    single<YamNetClassifier> { AndroidYamNetClassifier(get()) }
}
