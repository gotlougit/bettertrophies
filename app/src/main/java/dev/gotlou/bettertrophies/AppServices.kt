package dev.gotlou.bettertrophies

import android.content.Context

object AppServices {
    lateinit var cacheStore: AppCacheStore
        private set
    lateinit var captureMediaStore: CaptureMediaStore
        private set

    fun initialize(context: Context) {
        if (::cacheStore.isInitialized) {
            return
        }

        captureMediaStore = CaptureMediaStore(context.applicationContext)
        cacheStore = AppCacheStore(context.applicationContext)
    }
}
