package dev.gotlou.bettertrophies

import android.content.Context

object StationPlayerLoader {
    private const val NATIVE_LIBRARY_NAME = "uniffi_stationplayer"

    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) {
            return
        }

        ensureLibraryLoaded()

        synchronized(this) {
            if (!initialized) {
                initializeRustlsPlatformVerifier(context.applicationContext)
                initialized = true
            }
        }
    }

    fun load() {
        ensureLibraryLoaded()
        check(initialized) {
            "StationPlayerLoader.initialize(context) must be called before using stationplayer."
        }
    }

    @Synchronized
    private fun ensureLibraryLoaded() {
        if (libraryLoaded) {
            return
        }

        System.loadLibrary(NATIVE_LIBRARY_NAME)
        libraryLoaded = true
    }

    @JvmStatic
    private external fun initializeRustlsPlatformVerifier(context: Context)
}
