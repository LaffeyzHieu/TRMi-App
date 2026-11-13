package com.trmi.s3monitor

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.trmi.s3monitor.data.local.PrefsManager

/**
 * Application entry point. Keeps a single app context reference for helpers
 * that need it without having to thread `Context` through every call site.
 *
 * Room database, DataStore and other singletons will be initialised here
 * as the screens that need them ship in later rounds.
 */
class S3App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Áp mode đã lưu trước khi Activity đầu tiên inflate (không bị nháy màu).
        AppCompatDelegate.setDefaultNightMode(PrefsManager(this).uiModeToAppCompat())
    }

    companion object {
        @Volatile
        lateinit var instance: S3App
            private set
    }
}
