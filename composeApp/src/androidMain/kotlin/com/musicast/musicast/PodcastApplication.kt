package com.musicast.musicast

import android.app.Application
import com.musicast.musicast.di.commonModule
import com.musicast.musicast.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PodcastApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PodcastApplication)
            modules(commonModule, platformModule)
        }
    }
}
