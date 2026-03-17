package dev.gotlou.bettertrophies

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gotlou.bettertrophies.stationplayer.MyInfo
import dev.gotlou.bettertrophies.stationplayer.RecentPlayedTitle
import dev.gotlou.bettertrophies.stationplayer.StationPlayer
import dev.gotlou.bettertrophies.stationplayer.Trophy
import dev.gotlou.bettertrophies.stationplayer.TrophyDistributions
import dev.gotlou.bettertrophies.stationplayer.UserGameTrophyInfo
import dev.gotlou.bettertrophies.stationplayer.UserTrophySummary
import dev.gotlou.bettertrophies.stationplayer.generateSignInUrl
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class MainUiState(
    val npsso: String = "",
    val hasStoredNpsso: Boolean = false,
    val isEditingStoredNpsso: Boolean = false,
    val isRestoringStoredNpsso: Boolean = true,
    val signInUrl: String = "",
    val dashboard: DashboardSnapshot? = null,
    val trophies: List<TrophyEntry> = emptyList(),
    val currentScreen: MainScreen = MainScreen.Dashboard,
    val selectedTitleId: String? = null,
    val selectedTitleName: String? = null,
    val isLoading: Boolean = false,
    val isLoadingTrophies: Boolean = false,
    val error: String? = null,
    val logLines: List<String> = emptyList(),
)

enum class MainScreen {
    Dashboard,
    Games,
    TrophyDetail,
}

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val tokenStore = NpssoTokenStore(application.applicationContext)
    private var session: StationPlayer? = null
    private var storedNpsso: String? = null
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        initializeStationPlayer()
        restoreStoredNpsso()
    }

    fun updateNpsso(value: String) {
        _state.update { it.copy(npsso = value, error = null) }
    }

    fun startStoredTokenEdit() {
        _state.update {
            it.copy(
                npsso = "",
                isEditingStoredNpsso = true,
                error = null,
            )
        }
    }

    fun cancelStoredTokenEdit() {
        _state.update {
            it.copy(
                npsso = storedNpsso.orEmpty(),
                isEditingStoredNpsso = false,
                error = null,
            )
        }
    }

    fun clearStoredToken() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { tokenStore.clearToken() }
                .onSuccess {
                    storedNpsso = null
                    session = null
                    appendLog("Cleared the stored NPSSO token.")
                    _state.update {
                        it.copy(
                            npsso = "",
                            hasStoredNpsso = false,
                            isEditingStoredNpsso = false,
                            dashboard = null,
                            trophies = emptyList(),
                            currentScreen = MainScreen.Dashboard,
                            selectedTitleId = null,
                            selectedTitleName = null,
                            isLoading = false,
                            isLoadingTrophies = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    appendLog("Failed to clear the stored NPSSO token: ${formatThrowable(error)}")
                    _state.update {
                        it.copy(error = userFacingError(error, "Failed to clear the stored NPSSO token."))
                    }
                }
        }
    }

    fun clearLogs() {
        _state.update { it.copy(logLines = emptyList()) }
    }

    fun showDashboardScreen() {
        _state.update { it.copy(currentScreen = MainScreen.Dashboard, error = null) }
    }

    fun showGamesScreen() {
        _state.update { state ->
            if (state.dashboard == null) state else state.copy(currentScreen = MainScreen.Games, error = null)
        }
    }

    fun useSignInUrl() {
        val signInUrl = state.value.signInUrl
        appendLog(
            if (signInUrl.isBlank()) {
                "Sign-in URL requested before the native bindings were ready."
            } else {
                "Sign-in URL is available below the token field."
            },
        )
        _state.update { state ->
            state.copy(
                error = if (signInUrl.isBlank()) {
                    "Rust bindings are not loaded yet. Run ./scripts/generate-bindings.sh and ./scripts/build-android-libs.sh before building the app."
                } else {
                    null
                },
            )
        }
    }

    fun connect() {
        val token = state.value.npsso.trim()
        if (token.isBlank()) {
            appendLog("Connect blocked because the NPSSO field is empty.")
            _state.update { it.copy(error = "Enter an NPSSO token.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isLoading = true,
                    currentScreen = MainScreen.Dashboard,
                    dashboard = null,
                    error = null,
                    trophies = emptyList(),
                    selectedTitleId = null,
                    selectedTitleName = null,
                )
            }
            appendLog("Loading native stationplayer library.")
            try {
                StationPlayerLoader.load()
                appendLog("Creating StationPlayer session from the supplied NPSSO token.")
                val activeSession = StationPlayer.init(token)

                appendLog("Fetching profile.")
                val profile = activeSession.getProfile()
                appendLog(
                    buildString {
                        append("Fetched ${profile.avatars.size} profile avatar candidate(s)")
                        profile.avatars
                            .mapNotNull { avatar ->
                                avatar.url
                                    .takeIf { it.isNotBlank() }
                                    ?.let { "${avatar.size}=$it" }
                            }
                            .takeIf { it.isNotEmpty() }
                            ?.let { avatars ->
                                append(": ")
                                append(avatars.joinToString())
                            }
                    },
                )
                appendLog("Fetching trophy summary.")
                val summary = activeSession.trophySummary()
                appendLog("Fetching all trophy titles.")
                val trophyTitles = activeSession.getAllUserTrophyGames()
                appendLog("Fetching recent titles.")
                val recentTitles = activeSession.recentPlayedTitles(8u)

                val dashboard = DashboardSnapshot(
                    profile = mapProfile(profile),
                    summary = mapSummary(summary),
                    trophyTitles = trophyTitles.map(::mapGameTitle),
                    recentTitles = recentTitles.data.recentPlayedTitles.map(::mapRecentTitle),
                )
                session = activeSession
                tokenStore.writeToken(token)
                storedNpsso = token
                appendLog(
                    "Dashboard loaded with ${dashboard.trophyTitles.size} trophy titles and ${dashboard.recentTitles.size} recent titles.",
                )
                _state.update {
                    it.copy(
                        npsso = token,
                        hasStoredNpsso = true,
                        isEditingStoredNpsso = false,
                        isLoading = false,
                        currentScreen = MainScreen.Dashboard,
                        dashboard = dashboard,
                        selectedTitleId = null,
                        selectedTitleName = null,
                        trophies = emptyList(),
                    )
                }
            } catch (error: Throwable) {
                appendLog("Connect failed: ${formatThrowable(error)}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = userFacingError(error, "Failed to connect."),
                    )
                }
            }
        }
    }

    fun loadTrophiesForTitle(titleId: String, titleName: String) {
        val activeSession = session ?: run {
            appendLog("Trophy load blocked because there is no active StationPlayer session.")
            _state.update { it.copy(error = "Connect with an NPSSO token first.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    currentScreen = MainScreen.TrophyDetail,
                    selectedTitleId = titleId,
                    selectedTitleName = titleName,
                    isLoadingTrophies = true,
                    error = null,
                )
            }
            appendLog("Loading trophies for $titleName ($titleId).")
            try {
                StationPlayerLoader.load()
                val trophies = activeSession.getAllTrophiesForTitleId(titleId).map(::mapTrophy)
                appendLog("Loaded ${trophies.size} trophies for $titleName.")
                _state.update {
                    it.copy(
                        trophies = trophies,
                        isLoadingTrophies = false,
                    )
                }
            } catch (error: Throwable) {
                appendLog("Trophy load failed for $titleName: ${formatThrowable(error)}")
                _state.update {
                    it.copy(
                        isLoadingTrophies = false,
                        error = userFacingError(error, "Failed to load trophies."),
                    )
                }
            }
        }
    }

    fun loadTrophiesForGame(title: GameTitle) {
        val activeSession = session ?: run {
            appendLog("Trophy load blocked because there is no active StationPlayer session.")
            _state.update { it.copy(error = "Connect with an NPSSO token first.") }
            return
        }

        val selectedId = title.id
        val requestLabel = title.npTitleId ?: "${title.communicationId} (${title.serviceName})"

        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    currentScreen = MainScreen.TrophyDetail,
                    selectedTitleId = selectedId,
                    selectedTitleName = title.titleName,
                    isLoadingTrophies = true,
                    error = null,
                )
            }
            appendLog("Loading trophies for ${title.titleName} ($requestLabel).")
            try {
                StationPlayerLoader.load()
                val trophies = if (title.npTitleId != null) {
                    activeSession.getAllTrophiesForTitleId(title.npTitleId)
                } else {
                    activeSession.getAllTrophiesForCommunicationId(
                        title.communicationId,
                        title.serviceName,
                    )
                }.map(::mapTrophy)
                appendLog("Loaded ${trophies.size} trophies for ${title.titleName}.")
                _state.update {
                    it.copy(
                        trophies = trophies,
                        isLoadingTrophies = false,
                    )
                }
            } catch (error: Throwable) {
                appendLog("Trophy load failed for ${title.titleName}: ${formatThrowable(error)}")
                _state.update {
                    it.copy(
                        isLoadingTrophies = false,
                        error = userFacingError(error, "Failed to load trophies."),
                    )
                }
            }
        }
    }

    private fun initializeStationPlayer() {
        viewModelScope.launch(Dispatchers.IO) {
            appendLog("Preparing stationplayer bindings.")
            try {
                StationPlayerLoader.load()
                appendLog("Native stationplayer library loaded.")
                val signInUrl = generateSignInUrl(null)
                appendLog("Generated sign-in URL.")
                _state.update { it.copy(signInUrl = signInUrl, error = null) }
            } catch (error: Throwable) {
                appendLog("StationPlayer initialization failed: ${formatThrowable(error)}")
                _state.update {
                    it.copy(
                        error = "Rust bindings are not loaded yet. Run ./scripts/generate-bindings.sh and ./scripts/build-android-libs.sh before building the app.",
                    )
                }
            }
        }
    }

    private fun restoreStoredNpsso() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { tokenStore.readToken() }
                .onSuccess { token ->
                    if (token.isNullOrBlank()) {
                    appendLog("No stored NPSSO token found.")
                    _state.update { it.copy(isRestoringStoredNpsso = false) }
                    return@onSuccess
                }

                storedNpsso = token
                appendLog("Restored a stored NPSSO token. Attempting automatic sign-in.")
                    _state.update {
                        it.copy(
                            npsso = token,
                            hasStoredNpsso = true,
                            isEditingStoredNpsso = false,
                            isRestoringStoredNpsso = false,
                            error = null,
                        )
                    }
                    connect()
                }
                .onFailure { error ->
                    appendLog("Failed to read the stored NPSSO token: ${formatThrowable(error)}")
                    _state.update {
                        it.copy(
                            isRestoringStoredNpsso = false,
                            error = userFacingError(error, "Failed to read the stored NPSSO token."),
                        )
                    }
                }
        }
    }

    private fun appendLog(message: String) {
        val timestamp = LocalTime.now().format(timeFormatter)
        _state.update { current ->
            current.copy(logLines = (current.logLines + "$timestamp  $message").takeLast(200))
        }
    }

    private fun userFacingError(error: Throwable, fallback: String): String {
        val detailed = formatThrowable(error)
        return if (detailed.isBlank()) fallback else detailed
    }

    private fun formatThrowable(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }
            .map { throwable ->
                val typeName = throwable::class.simpleName ?: throwable.javaClass.simpleName
                val message = throwable.message?.takeIf { it.isNotBlank() }
                when {
                    message == null -> typeName
                    message.equals(typeName, ignoreCase = true) -> message
                    else -> "$typeName: $message"
                }
            }
            .distinct()
            .toList()
        return chain.joinToString(" -> ")
    }

    private fun mapProfile(profile: MyInfo): UserProfile {
        return UserProfile(
            onlineId = profile.onlineId,
            firstName = profile.personalDetail.firstName.ifBlank { null },
            lastName = profile.personalDetail.lastName.ifBlank { null },
            aboutMe = profile.aboutMe.ifBlank { null },
            isPlus = profile.isPlus,
            isVerified = profile.isVerified,
            languages = profile.languages,
            avatarUrl = selectAvatarUrl(profile),
        )
    }

    private fun selectAvatarUrl(profile: MyInfo): String? {
        return profile.avatars
            .asSequence()
            .filter { it.url.isNotBlank() }
            .sortedByDescending { avatarPriority(it.size) }
            .map { normalizeAvatarUrl(it.url) }
            .firstOrNull()
    }

    private fun avatarPriority(size: String): Int {
        return when (size.trim().lowercase(Locale.US)) {
            "xl", "xlarge", "large", "l" -> 4
            "m", "medium" -> 3
            "s", "small" -> 2
            "xs", "xsmall" -> 1
            else -> 0
        }
    }

    private fun normalizeAvatarUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.removePrefix("http://")}"
            else -> trimmed
        }
    }

    private fun mapSummary(summary: UserTrophySummary): TrophySummaryRecord {
        return TrophySummaryRecord(
            trophyLevel = summary.trophyLevel.toInt(),
            progress = summary.progress.toInt(),
            tier = summary.tier.toInt(),
            trophyPoints = summary.trophyPoint.toInt(),
            earnedTrophies = mapTotals(summary.earnedTrophies),
        )
    }

    private fun mapGameTitle(title: UserGameTrophyInfo): GameTitle {
        val stableId = title.npTitleId ?: "comm:${title.npCommunicationId}:${title.npServiceName}"
        return GameTitle(
            id = stableId,
            npTitleId = title.npTitleId,
            titleName = title.title,
            platform = title.platform,
            progress = title.progress.toInt(),
            iconUrl = title.trophyTitleIcon,
            lastUpdated = title.lastUpdated.ifBlank { null },
            communicationId = title.npCommunicationId,
            serviceName = title.npServiceName,
            earnedTrophies = mapTotals(title.earnedTrophies),
            definedTrophies = mapTotals(title.definedTrophies),
        )
    }

    private fun mapRecentTitle(title: RecentPlayedTitle): RecentTitle {
        val coverUrl = title.title.media.firstOrNull { media ->
            media.role.equals("MASTER", ignoreCase = true)
        }?.url ?: title.title.media.firstOrNull()?.url

        return RecentTitle(
            id = title.id,
            npTitleId = title.npTitleId,
            titleName = title.title.name,
            platform = title.title.platform,
            playTimeHours = title.playTimeHours.toInt(),
            storyProgress = title.storyProgress?.toInt(),
            coverUrl = coverUrl,
            hasHelpContent = title.hasHelpContent,
            hasCodex = title.codexSummary.hasCodex,
        )
    }

    private fun mapTrophy(trophy: Trophy): TrophyEntry {
        return TrophyEntry(
            trophyId = trophy.trophyId,
            name = trophy.trophyName,
            detail = trophy.trophyDetail,
            trophyType = trophy.trophyType,
            iconUrl = trophy.trophyIconUrl,
            hidden = trophy.trophyHidden,
            earned = trophy.earned,
            earnedAt = trophy.earnedDateTime,
            progress = trophy.progress,
            progressRate = trophy.progressRate?.toInt(),
            rare = trophy.rare?.toInt(),
            earnedRate = trophy.trophyEarnedRate,
        )
    }

    private fun mapTotals(totals: TrophyDistributions): TrophyTotals {
        return TrophyTotals(
            bronze = totals.bronze.toInt(),
            silver = totals.silver.toInt(),
            gold = totals.gold.toInt(),
            platinum = totals.platinum.toInt(),
        )
    }
}
