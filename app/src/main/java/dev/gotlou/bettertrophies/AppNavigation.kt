package dev.gotlou.bettertrophies

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface AppRoute : NavKey {
    @Serializable
    data object Dashboard : AppRoute

    @Serializable
    data object Games : AppRoute

    @Serializable
    data class TrophyDetails(
        val titleId: String,
        val titleName: String,
    ) : AppRoute

    @Serializable
    data object Captures : AppRoute

    @Serializable
    data class CaptureDetails(
        val groupId: String,
        val titleName: String,
    ) : AppRoute
}
