package dev.gotlou.bettertrophies

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gotlou.bettertrophies.stationplayer.CloudMediaCapture
import dev.gotlou.bettertrophies.stationplayer.CloudMediaCaptureGroup
import dev.gotlou.bettertrophies.stationplayer.CloudMediaCaptureUrls
import dev.gotlou.bettertrophies.stationplayer.MyInfo
import dev.gotlou.bettertrophies.stationplayer.RecentPlayedTitle
import dev.gotlou.bettertrophies.stationplayer.StationPlayer
import dev.gotlou.bettertrophies.stationplayer.Trophy
import dev.gotlou.bettertrophies.stationplayer.TrophyDistributions
import dev.gotlou.bettertrophies.stationplayer.UserGameTrophyInfo
import dev.gotlou.bettertrophies.stationplayer.UserTrophySummary
import dev.gotlou.bettertrophies.stationplayer.generateSignInUrl
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val npsso: String = "",
    val hasStoredNpsso: Boolean = false,
    val isEditingStoredNpsso: Boolean = false,
    val isRestoringStoredNpsso: Boolean = true,
    val signInUrl: String = "",
    val dashboard: DashboardSnapshot? = null,
    val trophies: List<TrophyEntry> = emptyList(),
    val captureGroups: List<CaptureGroup> = emptyList(),
    val currentScreen: MainScreen = MainScreen.Dashboard,
    val selectedTitleId: String? = null,
    val selectedTitleName: String? = null,
    val selectedCaptureGroupId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingTrophies: Boolean = false,
    val isLoadingCaptures: Boolean = false,
    val isShowingCachedDashboard: Boolean = false,
    val isRefreshingDashboard: Boolean = false,
    val dashboardCacheUpdatedAtEpochMs: Long? = null,
    val isShowingCachedTrophies: Boolean = false,
    val isRefreshingTrophies: Boolean = false,
    val trophiesCacheUpdatedAtEpochMs: Long? = null,
    val isShowingCachedCaptures: Boolean = false,
    val isRefreshingCaptures: Boolean = false,
    val capturesCacheUpdatedAtEpochMs: Long? = null,
    val error: String? = null,
    val logLines: List<String> = emptyList(),
)

enum class MainScreen {
    Dashboard,
    Games,
    TrophyDetail,
    Captures,
    CaptureDetail,
}

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val tokenStore = NpssoTokenStore(application.applicationContext)
    private val cacheStore = AppServices.cacheStore
    private val captureMediaStore = AppServices.captureMediaStore
    private var session: StationPlayer? = null
    private var storedNpsso: String? = null
    private var currentAccountKey: String? = null
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        initializeStationPlayer()
        restoreStoredNpsso()
    }

    fun updateNpsso(value: String) {
        _state.update { it.copy(npsso = value, error = null) }
    }

    fun startStoredTokenEdit() {
        _state.update { it.copy(npsso = "", isEditingStoredNpsso = true, error = null) }
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
            val accountKeyToClear = currentAccountKey ?: storedNpsso?.let(::accountKeyForToken)
            runCatching { tokenStore.clearToken() }
                .onSuccess {
                    accountKeyToClear?.let {
                        cacheStore.clearAccount(it)
                        captureMediaStore.clearAccount(it)
                    }
                    storedNpsso = null
                    currentAccountKey = null
                    session = null
                    appendLog("Cleared the stored NPSSO token.")
                    _state.update {
                        it.copy(
                            npsso = "",
                            hasStoredNpsso = false,
                            isEditingStoredNpsso = false,
                            dashboard = null,
                            trophies = emptyList(),
                            captureGroups = emptyList(),
                            currentScreen = MainScreen.Dashboard,
                            selectedTitleId = null,
                            selectedTitleName = null,
                            selectedCaptureGroupId = null,
                            isLoading = false,
                            isLoadingTrophies = false,
                            isLoadingCaptures = false,
                            isShowingCachedDashboard = false,
                            isRefreshingDashboard = false,
                            dashboardCacheUpdatedAtEpochMs = null,
                            isShowingCachedTrophies = false,
                            isRefreshingTrophies = false,
                            trophiesCacheUpdatedAtEpochMs = null,
                            isShowingCachedCaptures = false,
                            isRefreshingCaptures = false,
                            capturesCacheUpdatedAtEpochMs = null,
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
        _state.update { current ->
            if (current.dashboard == null) current else current.copy(currentScreen = MainScreen.Games, error = null)
        }
    }

    fun showCapturesScreen() {
        _state.update {
            it.copy(
                currentScreen = MainScreen.Captures,
                selectedCaptureGroupId = null,
                error = null,
            )
        }
        if (state.value.captureGroups.isEmpty() && !state.value.isRefreshingCaptures) {
            loadCaptures()
        }
    }

    fun openCaptureGroup(group: CaptureGroup) {
        _state.update {
            it.copy(
                currentScreen = MainScreen.CaptureDetail,
                selectedCaptureGroupId = group.titleId,
                error = null,
            )
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
        _state.update {
            it.copy(
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
            val accountKey = accountKeyForToken(token)
            currentAccountKey = accountKey
            val hasCachedDashboard = loadCachedDashboard(accountKey)
            if (!hasCachedDashboard) {
                _state.update {
                    it.copy(
                        isLoading = true,
                        currentScreen = MainScreen.Dashboard,
                        dashboard = null,
                        trophies = emptyList(),
                        captureGroups = emptyList(),
                        selectedTitleId = null,
                        selectedTitleName = null,
                        selectedCaptureGroupId = null,
                        isShowingCachedDashboard = false,
                        isRefreshingDashboard = false,
                        dashboardCacheUpdatedAtEpochMs = null,
                        isShowingCachedTrophies = false,
                        isRefreshingTrophies = false,
                        trophiesCacheUpdatedAtEpochMs = null,
                        isShowingCachedCaptures = false,
                        isRefreshingCaptures = false,
                        capturesCacheUpdatedAtEpochMs = null,
                        error = null,
                    )
                }
            }
            appendLog("Loading native stationplayer library.")
            try {
                StationPlayerLoader.load()
                appendLog("Creating StationPlayer session from the supplied NPSSO token.")
                val activeSession = StationPlayer.init(token)
                session = activeSession

                appendLog("Fetching profile.")
                val profile = activeSession.getProfile()
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
                val updatedAtEpochMs = System.currentTimeMillis()
                cacheStore.writeDashboard(accountKey, dashboard, updatedAtEpochMs)
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
                        isShowingCachedDashboard = false,
                        isRefreshingDashboard = false,
                        dashboardCacheUpdatedAtEpochMs = updatedAtEpochMs,
                        currentScreen = MainScreen.Dashboard,
                        dashboard = dashboard,
                        selectedTitleId = null,
                        selectedTitleName = null,
                        selectedCaptureGroupId = null,
                        trophies = emptyList(),
                        captureGroups = emptyList(),
                        isShowingCachedTrophies = false,
                        isRefreshingTrophies = false,
                        trophiesCacheUpdatedAtEpochMs = null,
                        isShowingCachedCaptures = false,
                        isRefreshingCaptures = false,
                        capturesCacheUpdatedAtEpochMs = null,
                        error = null,
                    )
                }
            } catch (error: Throwable) {
                appendLog("Connect failed: ${formatThrowable(error)}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshingDashboard = false,
                        error = if (hasCachedDashboard) {
                            "Showing saved data. ${userFacingError(error, "Refresh failed.")}"
                        } else {
                            userFacingError(error, "Failed to connect.")
                        },
                    )
                }
            }
        }
    }

    fun loadTrophiesForTitle(titleId: String, titleName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountKey = currentAccountKey
            val cachedTrophies = accountKey?.let { cacheStore.readTrophies(it, titleId) }
            showTrophyScreenWithCachedData(titleId, titleName, cachedTrophies)
            appendLog("Loading trophies for $titleName ($titleId).")
            val activeSession = session
            if (activeSession == null) {
                handleMissingSessionForTrophies(titleName, cachedTrophies != null)
                return@launch
            }

            try {
                StationPlayerLoader.load()
                val trophies = activeSession.getAllTrophiesForTitleId(titleId).map(::mapTrophy)
                val updatedAtEpochMs = System.currentTimeMillis()
                accountKey?.let { cacheStore.writeTrophies(it, titleId, trophies, updatedAtEpochMs) }
                appendLog("Loaded ${trophies.size} trophies for $titleName.")
                _state.update {
                    it.copy(
                        trophies = trophies,
                        isLoadingTrophies = false,
                        isShowingCachedTrophies = false,
                        isRefreshingTrophies = false,
                        trophiesCacheUpdatedAtEpochMs = updatedAtEpochMs,
                        error = null,
                    )
                }
            } catch (error: Throwable) {
                handleTrophyLoadFailure(error, cachedTrophies != null)
            }
        }
    }

    fun loadTrophiesForGame(title: GameTitle) {
        val selectedId = title.id
        val requestLabel = title.npTitleId ?: "${title.communicationId} (${title.serviceName})"
        viewModelScope.launch(Dispatchers.IO) {
            val accountKey = currentAccountKey
            val cachedTrophies = accountKey?.let { cacheStore.readTrophies(it, selectedId) }
            showTrophyScreenWithCachedData(selectedId, title.titleName, cachedTrophies)
            appendLog("Loading trophies for ${title.titleName} ($requestLabel).")
            val activeSession = session
            if (activeSession == null) {
                handleMissingSessionForTrophies(title.titleName, cachedTrophies != null)
                return@launch
            }

            try {
                StationPlayerLoader.load()
                val trophies = if (title.npTitleId != null) {
                    activeSession.getAllTrophiesForTitleId(title.npTitleId)
                } else {
                    activeSession.getAllTrophiesForCommunicationId(title.communicationId, title.serviceName)
                }.map(::mapTrophy)
                val updatedAtEpochMs = System.currentTimeMillis()
                accountKey?.let { cacheStore.writeTrophies(it, selectedId, trophies, updatedAtEpochMs) }
                appendLog("Loaded ${trophies.size} trophies for ${title.titleName}.")
                _state.update {
                    it.copy(
                        trophies = trophies,
                        isLoadingTrophies = false,
                        isShowingCachedTrophies = false,
                        isRefreshingTrophies = false,
                        trophiesCacheUpdatedAtEpochMs = updatedAtEpochMs,
                        error = null,
                    )
                }
            } catch (error: Throwable) {
                handleTrophyLoadFailure(error, cachedTrophies != null)
            }
        }
    }

    private fun loadCaptures() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountKey = currentAccountKey
            val cachedCaptures = accountKey?.let(cacheStore::readCaptureGroups)
            showCaptureScreenWithCachedData(cachedCaptures)
            appendLog("Loading cloud captures.")
            val activeSession = session
            if (activeSession == null) {
                if (cachedCaptures != null) {
                    _state.update { it.copy(isLoadingCaptures = false, isRefreshingCaptures = false) }
                } else {
                    _state.update {
                        it.copy(
                            isLoadingCaptures = false,
                            isRefreshingCaptures = false,
                            error = "Connect with an NPSSO token first.",
                        )
                    }
                }
                return@launch
            }

            try {
                StationPlayerLoader.load()
                val freshGroups = activeSession.getAllCloudMediaCaptureGroups().map(::mapCaptureGroup)
                val mergedGroups = mergeCaptureGroups(freshGroups, cachedCaptures?.groups.orEmpty())
                val updatedAtEpochMs = System.currentTimeMillis()
                accountKey?.let { cacheStore.writeCaptureGroups(it, mergedGroups, updatedAtEpochMs) }
                _state.update {
                    it.copy(
                        captureGroups = mergedGroups,
                        isLoadingCaptures = false,
                        isShowingCachedCaptures = false,
                        isRefreshingCaptures = false,
                        capturesCacheUpdatedAtEpochMs = updatedAtEpochMs,
                        error = null,
                    )
                }
                appendLog(
                    "Loaded ${mergedGroups.sumOf { it.captures.size }} captures across ${mergedGroups.size} games.",
                )
                if (accountKey != null) {
                    cacheCaptureMedia(accountKey, mergedGroups)
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isLoadingCaptures = false,
                        isRefreshingCaptures = false,
                        error = if (cachedCaptures != null) {
                            "Showing saved captures. ${userFacingError(error, "Refresh failed.")}"
                        } else {
                            userFacingError(error, "Failed to load captures.")
                        },
                    )
                }
            }
        }
    }

    private fun cacheCaptureMedia(accountKey: String, groups: List<CaptureGroup>) {
        val activeSession = session ?: return
        viewModelScope.launch(Dispatchers.IO) {
            groups.flatMap { it.captures }.forEach { capture ->
                if (!capture.localThumbnailPath.isNullOrBlank() && !capture.localPrimaryAssetPath.isNullOrBlank()) {
                    return@forEach
                }

                runCatching {
                    val urls = activeSession.cloudMediaCaptureUrls(capture.ugcId)
                    val updated = cacheCaptureAssets(activeSession, accountKey, capture, urls)
                    if (updated != capture) {
                        val updatedAtEpochMs = System.currentTimeMillis()
                        cacheStore.upsertCapture(accountKey, updated, updatedAtEpochMs)
                        _state.update { state ->
                            state.copy(
                                captureGroups = state.captureGroups.map { group ->
                                    if (group.titleId != updated.titleId) group else group.copy(
                                        captures = group.captures.map { existing ->
                                            if (existing.ugcId == updated.ugcId) {
                                                updated.copy(isCachedOnly = existing.isCachedOnly)
                                            } else {
                                                existing
                                            }
                                        },
                                    )
                                },
                                capturesCacheUpdatedAtEpochMs = updatedAtEpochMs,
                            )
                        }
                    }
                }.onFailure { error ->
                    appendLog("Capture asset cache failed for ${capture.ugcId}: ${formatThrowable(error)}")
                }
            }
        }
    }

    private fun cacheCaptureAssets(
        activeSession: StationPlayer,
        accountKey: String,
        capture: CaptureEntry,
        urls: CloudMediaCaptureUrls,
    ): CaptureEntry {
        var updated = capture

        if (capture.localThumbnailPath.isNullOrBlank()) {
            val thumbnailUrl = urls.smallPreviewImage
                ?: urls.largePreviewImage
                ?: urls.screenshotUrl
                ?: capture.thumbnailUrl
            if (!thumbnailUrl.isNullOrBlank()) {
                val downloaded = downloadUrl(thumbnailUrl)
                val localPath = captureMediaStore.persistThumbnail(
                    accountKey = accountKey,
                    captureId = capture.ugcId,
                    sourceUrl = thumbnailUrl,
                    contentType = downloaded.contentType,
                    bytes = downloaded.bytes,
                )
                updated = updated.copy(
                    thumbnailUrl = updated.thumbnailUrl ?: thumbnailUrl,
                    localThumbnailPath = localPath,
                )
            }
        }

        if (capture.localPrimaryAssetPath.isNullOrBlank()) {
            val downloaded = activeSession.downloadCloudMediaCapture(capture.ugcId)
            val bytes = downloaded.bytes
            val localPath = captureMediaStore.persistPrimaryAsset(
                accountKey = accountKey,
                captureId = capture.ugcId,
                fileName = downloaded.fileName,
                contentType = downloaded.contentType,
                bytes = bytes.toUByteArray().toByteArray(),
            )
            updated = updated.copy(
                primaryAssetUrl = downloaded.sourceUrl,
                localPrimaryAssetPath = localPath,
                localPrimaryAssetContentType = downloaded.contentType,
                localPrimaryAssetFileName = downloaded.fileName,
            )
        }

        return updated
    }

    private fun downloadUrl(url: String): DownloadedUrl {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.connect()
        return try {
            DownloadedUrl(
                bytes = connection.inputStream.use { it.readBytes() },
                contentType = connection.contentType?.ifBlank { null },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun handleMissingSessionForTrophies(titleName: String, hasCache: Boolean) {
        if (hasCache) {
            appendLog("Showing cached trophies for $titleName while the account reconnects.")
            _state.update { it.copy(isLoadingTrophies = false, isRefreshingTrophies = false) }
        } else {
            _state.update {
                it.copy(
                    isLoadingTrophies = false,
                    isRefreshingTrophies = false,
                    error = "Connect with an NPSSO token first.",
                )
            }
        }
    }

    private fun handleTrophyLoadFailure(error: Throwable, hasCache: Boolean) {
        appendLog("Trophy load failed: ${formatThrowable(error)}")
        _state.update {
            it.copy(
                isLoadingTrophies = false,
                isRefreshingTrophies = false,
                error = if (hasCache) {
                    "Showing saved trophies. ${userFacingError(error, "Refresh failed.")}"
                } else {
                    userFacingError(error, "Failed to load trophies.")
                },
            )
        }
    }

    private fun initializeStationPlayer() {
        viewModelScope.launch(Dispatchers.IO) {
            appendLog("Preparing stationplayer bindings.")
            try {
                StationPlayerLoader.load()
                val signInUrl = generateSignInUrl(null)
                _state.update { it.copy(signInUrl = signInUrl, error = null) }
            } catch (error: Throwable) {
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
                        _state.update { it.copy(isRestoringStoredNpsso = false) }
                        return@onSuccess
                    }

                    storedNpsso = token
                    currentAccountKey = accountKeyForToken(token)
                    _state.update {
                        it.copy(
                            npsso = token,
                            hasStoredNpsso = true,
                            isEditingStoredNpsso = false,
                            isRestoringStoredNpsso = false,
                            error = null,
                        )
                    }
                    loadCachedDashboard(currentAccountKey!!)
                    connect()
                }
                .onFailure { error ->
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

    private fun loadCachedDashboard(accountKey: String): Boolean {
        val cachedDashboard = cacheStore.readDashboard(accountKey) ?: return false
        _state.update {
            it.copy(
                dashboard = cachedDashboard.snapshot,
                currentScreen = MainScreen.Dashboard,
                selectedTitleId = null,
                selectedTitleName = null,
                selectedCaptureGroupId = null,
                trophies = emptyList(),
                captureGroups = emptyList(),
                isLoading = true,
                isShowingCachedDashboard = true,
                isRefreshingDashboard = true,
                dashboardCacheUpdatedAtEpochMs = cachedDashboard.updatedAtEpochMs,
                isShowingCachedTrophies = false,
                isRefreshingTrophies = false,
                trophiesCacheUpdatedAtEpochMs = null,
                isShowingCachedCaptures = false,
                isRefreshingCaptures = false,
                capturesCacheUpdatedAtEpochMs = null,
                error = null,
            )
        }
        return true
    }

    private fun showTrophyScreenWithCachedData(
        titleId: String,
        titleName: String,
        cachedTrophies: CachedTrophyEntries?,
    ) {
        _state.update {
            it.copy(
                currentScreen = MainScreen.TrophyDetail,
                selectedTitleId = titleId,
                selectedTitleName = titleName,
                trophies = cachedTrophies?.trophies.orEmpty(),
                isLoadingTrophies = cachedTrophies == null,
                isShowingCachedTrophies = cachedTrophies != null,
                isRefreshingTrophies = cachedTrophies != null,
                trophiesCacheUpdatedAtEpochMs = cachedTrophies?.updatedAtEpochMs,
                error = null,
            )
        }
    }

    private fun showCaptureScreenWithCachedData(cachedCaptures: CachedCaptureGroups?) {
        _state.update {
            it.copy(
                currentScreen = MainScreen.Captures,
                selectedCaptureGroupId = null,
                captureGroups = cachedCaptures?.groups.orEmpty(),
                isLoadingCaptures = cachedCaptures == null,
                isShowingCachedCaptures = cachedCaptures != null,
                isRefreshingCaptures = cachedCaptures != null,
                capturesCacheUpdatedAtEpochMs = cachedCaptures?.updatedAtEpochMs,
                error = null,
            )
        }
    }

    private fun mergeCaptureGroups(
        freshGroups: List<CaptureGroup>,
        cachedGroups: List<CaptureGroup>,
    ): List<CaptureGroup> {
        val cachedById = cachedGroups.flatMap { it.captures }.associateBy(CaptureEntry::ugcId).toMutableMap()
        val mergedCaptures = linkedMapOf<String, CaptureEntry>()

        freshGroups.flatMap { it.captures }.forEach { fresh ->
            val cached = cachedById.remove(fresh.ugcId)
            mergedCaptures[fresh.ugcId] = fresh.copy(
                description = fresh.description ?: cached?.description,
                thumbnailUrl = fresh.thumbnailUrl ?: cached?.thumbnailUrl,
                localThumbnailPath = existingPathOrNull(fresh.localThumbnailPath ?: cached?.localThumbnailPath),
                primaryAssetUrl = fresh.primaryAssetUrl ?: cached?.primaryAssetUrl,
                localPrimaryAssetPath = existingPathOrNull(fresh.localPrimaryAssetPath ?: cached?.localPrimaryAssetPath),
                localPrimaryAssetContentType = fresh.localPrimaryAssetContentType ?: cached?.localPrimaryAssetContentType,
                localPrimaryAssetFileName = fresh.localPrimaryAssetFileName ?: cached?.localPrimaryAssetFileName,
                isCachedOnly = false,
            )
        }

        cachedById.values.forEach { cached ->
            mergedCaptures[cached.ugcId] = cached.copy(
                localThumbnailPath = existingPathOrNull(cached.localThumbnailPath),
                localPrimaryAssetPath = existingPathOrNull(cached.localPrimaryAssetPath),
                isCachedOnly = true,
            )
        }

        return mergedCaptures.values
            .groupBy { it.titleId to it.titleName }
            .map { (key, captures) ->
                val latest = captures.maxByOrNull { it.uploadDate.orEmpty() }
                CaptureGroup(
                    titleId = key.first,
                    titleName = key.second,
                    conceptId = null,
                    titleImageUrl = latest?.titleImageUrl,
                    captures = captures.sortedByDescending { it.uploadDate.orEmpty() },
                )
            }
            .sortedByDescending { it.latestUploadDate.orEmpty() }
    }

    private fun existingPathOrNull(path: String?): String? {
        return path?.takeIf { captureMediaStore.resolve(it)?.exists() == true }
    }

    private fun accountKeyForToken(token: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun userFacingError(error: Throwable, fallback: String): String {
        val detailed = formatThrowable(error)
        return if (detailed.isBlank()) fallback else detailed
    }

    private fun formatThrowable(error: Throwable): String {
        return generateSequence(error) { it.cause }
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
            .joinToString(" -> ")
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

    private fun mapCaptureGroup(group: CloudMediaCaptureGroup): CaptureGroup {
        return CaptureGroup(
            titleId = group.titleId,
            titleName = group.titleName,
            conceptId = group.conceptId,
            titleImageUrl = group.titleImageUrl,
            captures = group.captures.map {
                mapCapture(group.titleId, group.titleName, group.titleImageUrl, it)
            }.sortedByDescending { it.uploadDate.orEmpty() },
        )
    }

    private fun mapCapture(
        titleId: String,
        titleName: String,
        titleImageUrl: String?,
        capture: CloudMediaCapture,
    ): CaptureEntry {
        return CaptureEntry(
            ugcId = capture.id,
            titleId = titleId,
            titleName = titleName,
            titleImageUrl = titleImageUrl,
            uploadDate = capture.uploadDate,
            captureType = capture.captureType,
            description = capture.description,
            fileType = capture.fileType,
            resolution = capture.resolution,
            fileSizeBytes = capture.fileSize?.toLong(),
            videoDurationSeconds = capture.videoDuration?.toLong(),
            platform = capture.scePlatform,
            isSpoiler = capture.isSpoiler,
            expireAt = capture.expireAt,
            thumbnailUrl = capture.smallPreviewImage
                ?: capture.largePreviewImage
                ?: capture.streamingPreviewImage
                ?: capture.screenshotUrl,
            localThumbnailPath = null,
            primaryAssetUrl = capture.screenshotUrl ?: capture.downloadUrl ?: capture.videoUrl,
            localPrimaryAssetPath = null,
            localPrimaryAssetContentType = null,
            localPrimaryAssetFileName = null,
            isCachedOnly = false,
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

    private data class DownloadedUrl(
        val bytes: ByteArray,
        val contentType: String?,
    )
}
