package dev.gotlou.bettertrophies

import android.app.Application

class BetterTrophiesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StationPlayerLoader.initialize(this)
    }
}
