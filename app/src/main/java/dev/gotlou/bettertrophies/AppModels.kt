package dev.gotlou.bettertrophies

data class TrophyTotals(
    val bronze: Int,
    val silver: Int,
    val gold: Int,
    val platinum: Int,
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
    val titleId: String,
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

data class DashboardSnapshot(
    val profile: UserProfile,
    val summary: TrophySummaryRecord,
    val trophyTitles: List<GameTitle>,
    val recentTitles: List<RecentTitle>,
)
