package com.musicast.musicast.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.musicast.musicast.db.PodcastDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        val driver = AndroidSqliteDriver(PodcastDatabase.Schema, context, "podcast.db")
        // Ensure app_settings table exists for databases created before it was added
        driver.execute(null, "CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)", 0)
        return driver
    }
}
