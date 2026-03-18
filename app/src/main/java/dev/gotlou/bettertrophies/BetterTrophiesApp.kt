package dev.gotlou.bettertrophies

import android.app.Application

class BetterTrophiesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppServices.initialize(this)
        StationPlayerLoader.initialize(this)
    }
}
