package dev.gotlou.bettertrophies

data class TrophyTotals(
    val bronze: Int,
    val silver: Int,
    val gold: Int,
    val platinum: Int,
)

data class CaptureTotals(
    val totalCaptures: Int,
    val totalGames: Int,
)

data class UserProfile(
    val onlineId: String,
    val firstName: String?,
    val lastName: String?,
    val aboutMe: String?,
    val isPlus: Boolean,
    val isVerified: Boolean,
    val languages: List<String>,
    val avatarUrl: String?,
)

data class TrophySummaryRecord(
    val trophyLevel: Int,
    val progress: Int,
    val tier: Int,
    val trophyPoints: Int,
    val earnedTrophies: TrophyTotals,
)

data class GameTitle(
    val id: String,
    val npTitleId: String?,
    val titleName: String,
    val platform: String,
    val progress: Int,
    val iconUrl: String,
    val lastUpdated: String?,
    val communicationId: String,
    val serviceName: String,
    val earnedTrophies: TrophyTotals,
    val definedTrophies: TrophyTotals,
)

data class RecentTitle(
    val id: String,
    val npTitleId: String,
    val titleName: String,
    val platform: String,
    val playTimeHours: Int,
    val storyProgress: Int?,
    val coverUrl: String?,
    val hasHelpContent: Boolean,
    val hasCodex: Boolean,
)

data class TrophyEntry(
    val trophyId: String,
    val name: String?,
    val detail: String?,
    val trophyType: String?,
    val iconUrl: String?,
    val hidden: Boolean?,
    val earned: Boolean?,
    val earnedAt: String?,
    val progress: String?,
    val progressRate: Int?,
    val rare: Int?,
    val earnedRate: String?,
)

enum class TrophySortOption(val label: String) {
    Default("Default"),
    EarnedNewest("Earned date"),
    NotEarnedFirst("Earned status"),
    Rarity("Rarity"),
}

data class CaptureEntry(
    val ugcId: String,
    val titleId: String,
    val titleName: String,
    val titleImageUrl: String?,
    val uploadDate: String?,
    val captureType: String?,
    val description: String?,
    val fileType: String?,
    val resolution: String?,
    val fileSizeBytes: Long?,
    val videoDurationSeconds: Long?,
    val platform: String?,
    val isSpoiler: Boolean?,
    val expireAt: String?,
    val thumbnailUrl: String?,
    val localThumbnailPath: String?,
    val primaryAssetUrl: String?,
    val localPrimaryAssetPath: String?,
    val localPrimaryAssetGalleryUri: String?,
    val localPrimaryAssetContentType: String?,
    val localPrimaryAssetFileName: String?,
    val isCachedOnly: Boolean,
)

data class CaptureGroup(
    val titleId: String,
    val titleName: String,
    val conceptId: String?,
    val titleImageUrl: String?,
    val captures: List<CaptureEntry>,
) {
    val latestUploadDate: String?
        get() = captures.maxOfOrNull { it.uploadDate.orEmpty() }.takeIf { !it.isNullOrBlank() }
}

fun List<CaptureGroup>.toCaptureTotals(): CaptureTotals = CaptureTotals(
    totalCaptures = sumOf { it.captures.size },
    totalGames = size,
)

data class DashboardSnapshot(
    val profile: UserProfile,
    val summary: TrophySummaryRecord,
    val trophyTitles: List<GameTitle>,
    val recentTitles: List<RecentTitle>,
)
