package com.musicast.musicast.di

import com.musicast.musicast.audio.MusicDetector
import com.musicast.musicast.audio.PcmDecoder
import com.musicast.musicast.audio.YamNetClassifier
import com.musicast.musicast.data.local.LocalDataSource
import com.musicast.musicast.data.remote.RssFeedService
import com.musicast.musicast.data.repository.PodcastRepository
import com.musicast.musicast.db.PodcastDatabase
import com.musicast.musicast.player.PlaybackManager
import com.prof18.rssparser.RssParser
import org.koin.core.module.Module
import org.koin.dsl.module

val commonModule = module {
    // Data
    single { RssParser() }
    single { RssFeedService(get()) }
    single { PodcastDatabase(get()) }
    single { LocalDataSource(get()) }
    single { PodcastRepository(get(), get()) }

    // Audio
    single { PcmDecoder() }
    single { MusicDetector(get(), get()) } // PcmDecoder + YamNetClassifier

    // Player
    single { PlaybackManager(get(), get()) }

    // ViewModels are created manually in App.kt via remember{} with injected deps
}

expect val platformModule: Module
