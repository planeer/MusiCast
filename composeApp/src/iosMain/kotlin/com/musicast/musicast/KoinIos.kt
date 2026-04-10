package com.musicast.musicast

import com.musicast.musicast.di.commonModule
import com.musicast.musicast.di.platformModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(commonModule, platformModule)
    }
}
