package dev.gotlou.bettertrophies

import android.content.Context

object AppServices {
    lateinit var cacheStore: AppCacheStore
        private set

    fun initialize(context: Context) {
        if (::cacheStore.isInitialized) {
            return
        }

        cacheStore = AppCacheStore(context.applicationContext)
    }
}
