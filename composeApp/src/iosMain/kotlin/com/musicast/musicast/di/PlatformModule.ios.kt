package com.musicast.musicast.di

import com.musicast.musicast.audio.IosYamNetClassifier
import com.musicast.musicast.audio.YamNetClassifier
import com.musicast.musicast.data.local.DatabaseDriverFactory
import com.musicast.musicast.download.EpisodeDownloader
import com.musicast.musicast.player.AudioPlayer
import com.musicast.musicast.player.IosAudioPlayer
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory().createDriver() }
    single<AudioPlayer> { IosAudioPlayer() }
    single { EpisodeDownloader() }
    single<YamNetClassifier> { IosYamNetClassifier() }
}
