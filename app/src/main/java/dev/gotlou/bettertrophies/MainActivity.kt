package dev.gotlou.bettertrophies

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import dev.gotlou.bettertrophies.ui.theme.BetterTrophiesTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when (viewModel.state.value.currentScreen) {
                        MainScreen.Games -> viewModel.showDashboardScreen()
                        MainScreen.Captures -> viewModel.showDashboardScreen()
                        MainScreen.TrophyDetail -> viewModel.showGamesScreen()
                        MainScreen.CaptureDetail -> viewModel.showCapturesScreen()
                        MainScreen.Dashboard -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
            },
        )
        enableEdgeToEdge()
        setContent {
            BetterTrophiesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BetterTrophiesScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun BetterTrophiesScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        when (state.currentScreen) {
            MainScreen.Dashboard -> DashboardScreen(
                state = state,
                onNpssoChanged = viewModel::updateNpsso,
                onConnect = viewModel::connect,
                onRefresh = viewModel::refreshDashboard,
                onPasteSignInUrl = viewModel::useSignInUrl,
                onCancelStoredTokenEdit = viewModel::cancelStoredTokenEdit,
                onClearStoredToken = viewModel::clearStoredToken,
                onClearLogs = viewModel::clearLogs,
                onShowGames = viewModel::showGamesScreen,
                onShowCaptures = viewModel::showCapturesScreen,
                onOpenRecentTitle = { viewModel.loadTrophiesForTitle(it.npTitleId, it.titleName) },
            )

            MainScreen.Games -> GamesScreen(
                state = state,
                onBack = viewModel::showDashboardScreen,
                onSelectTitle = viewModel::loadTrophiesForGame,
            )

            MainScreen.TrophyDetail -> TrophyDetailsScreen(
                state = state,
                onBack = viewModel::showGamesScreen,
                onSortChanged = viewModel::setTrophySort,
            )

            MainScreen.Captures -> CapturesScreen(
                state = state,
                onBack = viewModel::showDashboardScreen,
                onSelectGroup = viewModel::openCaptureGroup,
            )

            MainScreen.CaptureDetail -> CaptureDetailsScreen(
                state = state,
                onBack = viewModel::showCapturesScreen,
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DashboardScreen(
    state: MainUiState,
    onNpssoChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onPasteSignInUrl: () -> Unit,
    onCancelStoredTokenEdit: () -> Unit,
    onClearStoredToken: () -> Unit,
    onClearLogs: () -> Unit,
    onShowGames: () -> Unit,
    onShowCaptures: () -> Unit,
    onOpenRecentTitle: (RecentTitle) -> Unit,
) {
    val showAuthModule = state.isRestoringStoredNpsso || !state.hasStoredNpsso || state.isEditingStoredNpsso
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshingDashboard,
        onRefresh = onRefresh,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showAuthModule) {
                item {
                    AuthDashboardSection(
                        state = state,
                        onNpssoChanged = onNpssoChanged,
                        onConnect = onConnect,
                        onPasteSignInUrl = onPasteSignInUrl,
                        onCancelStoredTokenEdit = onCancelStoredTokenEdit,
                        onClearLogs = onClearLogs,
                    )
                }
            }

            val dashboard = state.dashboard
            if (dashboard != null) {
                item {
                    ProfileHeader(
                        avatarUrl = dashboard.profile.avatarUrl,
                        onlineId = dashboard.profile.onlineId,
                        realName = listOfNotNull(
                            dashboard.profile.firstName,
                            dashboard.profile.lastName,
                        ).joinToString(" ").ifBlank { null },
                        about = dashboard.profile.aboutMe,
                    )
                }

                item {
                    SummaryCard(
                        level = dashboard.summary.trophyLevel.toString(),
                        points = dashboard.summary.trophyPoints.toString(),
                        progress = dashboard.summary.progress.toString(),
                        bronze = dashboard.summary.earnedTrophies.bronze.toString(),
                        silver = dashboard.summary.earnedTrophies.silver.toString(),
                        gold = dashboard.summary.earnedTrophies.gold.toString(),
                        platinum = dashboard.summary.earnedTrophies.platinum.toString(),
                    )
                }

                item {
                    GamesEntryCard(
                        totalGames = dashboard.trophyTitles.size,
                        onShowGames = onShowGames,
                    )
                }

                item {
                    CapturesEntryCard(
                        totalCaptures = state.captureTotals.totalCaptures,
                        totalGames = state.captureTotals.totalGames,
                        onShowCaptures = onShowCaptures,
                    )
                }

                if (dashboard.recentTitles.isNotEmpty()) {
                    item {
                        SectionTitle("Recent titles")
                    }
                    item {
                        RecentTitlesRow(
                            titles = dashboard.recentTitles,
                            onSelect = onOpenRecentTitle,
                        )
                    }
                }
            } else if (state.hasStoredNpsso && !state.isEditingStoredNpsso) {
                item {
                    StoredSessionStatusCard(
                        loading = state.isLoading,
                        error = state.error,
                        onReconnect = onConnect,
                        onResetSignIn = onClearStoredToken,
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = state.isRefreshingDashboard,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CapturesScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSelectGroup: (CaptureGroup) -> Unit,
) {
    val captureGroups = state.captureGroups.sortedByDescending { group ->
        group.captures.maxOfOrNull { it.uploadDate.sortEpochMillis() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "Captures",
                subtitle = "${state.captureGroups.sumOf { it.captures.size }} captures across ${state.captureGroups.size} games",
                actionLabel = "Back",
                onAction = onBack,
            )
        }

        if (state.error != null) {
            item {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (state.isShowingCachedCaptures || state.isRefreshingCaptures) {
            item {
                CacheStatusCard(
                    title = "Showing saved captures",
                    updatedAtEpochMs = state.capturesCacheUpdatedAtEpochMs,
                    refreshing = state.isRefreshingCaptures,
                )
            }
        }

        if (state.isLoadingCaptures && captureGroups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (captureGroups.isEmpty()) {
            item {
                EmptyStateCard("No captures loaded yet.")
            }
        } else {
            items(captureGroups, key = { it.titleId }) { group ->
                CaptureGroupCard(
                    group = group,
                    selected = state.selectedCaptureGroupId == group.titleId,
                    onSelect = { onSelectGroup(group) },
                )
            }
        }
    }
}

@Composable
private fun CaptureDetailsScreen(
    state: MainUiState,
    onBack: () -> Unit,
) {
    val selectedGroup = state.captureGroups.firstOrNull { it.titleId == state.selectedCaptureGroupId }
    val captures = selectedGroup
        ?.captures
        ?.sortedByDescending { it.uploadDate.sortEpochMillis() }
        .orEmpty()
    var selectedCaptureId by remember(state.selectedCaptureGroupId) { mutableStateOf<String?>(null) }
    val selectedCapture = captures.firstOrNull { it.ugcId == selectedCaptureId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = selectedGroup?.titleName ?: "Capture details",
                subtitle = "${captures.size} captures",
                actionLabel = "Captures",
                onAction = onBack,
            )
        }

        if (state.error != null) {
            item {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (state.isShowingCachedCaptures || state.isRefreshingCaptures) {
            item {
                CacheStatusCard(
                    title = "Showing saved captures",
                    updatedAtEpochMs = state.capturesCacheUpdatedAtEpochMs,
                    refreshing = state.isRefreshingCaptures,
                )
            }
        }

        if (state.isLoadingCaptures && captures.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (captures.isEmpty()) {
            item {
                EmptyStateCard("No captures are available for this game.")
            }
        } else {
            items(captures, key = { it.ugcId }) { capture ->
                CaptureCard(
                    capture = capture,
                    onOpen = { selectedCaptureId = capture.ugcId },
                )
            }
        }
    }

    selectedCapture?.let { capture ->
        CaptureViewerDialog(
            capture = capture,
            onDismiss = { selectedCaptureId = null },
        )
    }
}

@Composable
private fun GamesScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSelectTitle: (GameTitle) -> Unit,
) {
    val dashboard = state.dashboard ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "All games",
                subtitle = "${dashboard.trophyTitles.size} trophy titles",
                actionLabel = "Back",
                onAction = onBack,
            )
        }

        if (state.isShowingCachedDashboard || state.isRefreshingDashboard) {
            item {
                CacheStatusCard(
                    title = "Showing saved library",
                    updatedAtEpochMs = state.dashboardCacheUpdatedAtEpochMs,
                    refreshing = state.isRefreshingDashboard,
                )
            }
        }

        items(dashboard.trophyTitles, key = { it.id }) { title ->
            TrophyTitleCard(
                title = title,
                selected = state.selectedTitleId == title.id,
                onSelect = { onSelectTitle(title) },
            )
        }
    }
}

@Composable
private fun TrophyDetailsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSortChanged: (TrophySortOption) -> Unit,
) {
    val trophySections = remember(state.trophies, state.trophySort) {
        state.trophies.sectionsFor(state.trophySort)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = state.selectedTitleName ?: "Trophies",
                subtitle = state.selectedTitleId ?: "Selected game",
                actionLabel = "All games",
                onAction = onBack,
            )
        }

        if (state.error != null) {
            item {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (state.isShowingCachedTrophies || state.isRefreshingTrophies) {
            item {
                CacheStatusCard(
                    title = "Showing saved trophies",
                    updatedAtEpochMs = state.trophiesCacheUpdatedAtEpochMs,
                    refreshing = state.isRefreshingTrophies,
                )
            }
        }

        if (state.isLoadingTrophies && state.trophies.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.trophies.isEmpty()) {
            item {
                EmptyStateCard("No trophies loaded for this title yet.")
            }
        } else {
            item {
                TrophySortBar(
                    selected = state.trophySort,
                    trophyCount = state.trophies.size,
                    onSortChanged = onSortChanged,
                )
            }
            trophySections.forEach { section ->
                if (section.title != null) {
                    item(key = "section-${section.title}") {
                        TrophySectionHeader(
                            title = section.title,
                            count = section.trophies.size,
                        )
                    }
                }
                items(section.trophies, key = { "${section.title ?: "all"}-${it.trophyId}" }) { trophy ->
                    TrophyCard(trophy = trophy)
                }
            }
        }
    }
}

@Composable
private fun TrophySortBar(
    selected: TrophySortOption,
    trophyCount: Int,
    onSortChanged: (TrophySortOption) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sort trophies", style = MaterialTheme.typography.titleMedium)
            Text(
                "$trophyCount trophies",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrophySortOption.entries.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSortChanged(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrophySectionHeader(
    title: String,
    count: Int,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            "$count trophies",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            AssistChip(
                onClick = onAction,
                label = { Text(actionLabel) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun GamesEntryCard(
    totalGames: Int,
    onShowGames: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Library", style = MaterialTheme.typography.titleLarge)
            Text(
                "Browse all $totalGames trophy-enabled games on a dedicated screen instead of mixing the list with trophy details.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onShowGames) {
                Text("View all games")
            }
        }
    }
}

@Composable
private fun CapturesEntryCard(
    totalCaptures: Int,
    totalGames: Int,
    onShowCaptures: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Captures", style = MaterialTheme.typography.titleLarge)
            Text(
                "Browse $totalCaptures recent captures grouped across $totalGames games, while older cached media stays visible from local storage.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onShowCaptures) {
                Text("View captures")
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StoredSessionStatusCard(
    loading: Boolean,
    error: String?,
    onReconnect: () -> Unit,
    onResetSignIn: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connecting saved account", style = MaterialTheme.typography.headlineSmall)
            Text(
                "A saved NPSSO token is present. The app is reconnecting in the background before showing the dashboard.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onReconnect, enabled = !loading) {
                    Text(if (loading) "Loading..." else "Retry")
                }
                AssistChip(
                    onClick = onResetSignIn,
                    label = { Text("Reset sign-in") },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CacheStatusCard(
    title: String,
    updatedAtEpochMs: Long?,
    refreshing: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(
                        updatedAtEpochMs?.let(::formatCacheTimestamp)
                            ?: "Saved data timestamp unavailable.",
                    )
                    if (refreshing) {
                        append(" Refreshing in the background.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (refreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    avatarUrl: String?,
    onlineId: String,
    realName: String?,
    about: String?,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = onlineId,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(onlineId, style = MaterialTheme.typography.headlineSmall)
                if (!realName.isNullOrBlank()) {
                    Text(realName, style = MaterialTheme.typography.titleMedium)
                }
                if (!about.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(about, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    level: String,
    points: String,
    progress: String,
    bronze: String,
    silver: String,
    gold: String,
    platinum: String,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Trophy summary", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatPill("Level", level)
                StatPill("Points", points)
                StatPill("Progress", "$progress%")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrophyDistributionPill("Bronze", bronze, Color(0xFFAA6C39), Modifier.weight(1f))
                TrophyDistributionPill("Silver", silver, Color(0xFF98A3B3), Modifier.weight(1f))
                TrophyDistributionPill("Gold", gold, Color(0xFFD8A320), Modifier.weight(1f))
                TrophyDistributionPill("Platinum", platinum, Color(0xFF7BA7FF), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrophyDistributionPill(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TrophyGlyph(accentColor)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun TrophyGlyph(accentColor: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val cupWidth = size.width * 0.54f
        val cupHeight = size.height * 0.38f
        val cupLeft = (size.width - cupWidth) / 2f
        val cupTop = size.height * 0.16f
        val stroke = size.width * 0.1f

        drawRoundRect(
            color = accentColor,
            topLeft = Offset(cupLeft, cupTop),
            size = Size(cupWidth, cupHeight),
            cornerRadius = CornerRadius(size.width * 0.12f, size.width * 0.12f),
        )
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(size.width * 0.45f, size.height * 0.52f),
            size = Size(size.width * 0.1f, size.height * 0.18f),
            cornerRadius = CornerRadius(stroke, stroke),
        )
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(size.width * 0.3f, size.height * 0.72f),
            size = Size(size.width * 0.4f, size.height * 0.12f),
            cornerRadius = CornerRadius(stroke, stroke),
        )
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(size.width * 0.1f, size.height * 0.26f),
            size = Size(size.width * 0.14f, stroke),
            cornerRadius = CornerRadius(stroke, stroke),
        )
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(size.width * 0.76f, size.height * 0.26f),
            size = Size(size.width * 0.14f, stroke),
            cornerRadius = CornerRadius(stroke, stroke),
        )
    }
}

@Composable
private fun RecentTitlesRow(
    titles: List<RecentTitle>,
    onSelect: (RecentTitle) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        titles.forEach { title ->
            Card(
                modifier = Modifier
                    .width(220.dp)
                    .requiredHeight(224.dp)
                    .clickable { onSelect(title) },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = title.coverUrl,
                        contentDescription = title.titleName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(14.dp),
                    ) {
                        Text(
                            title.titleName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${title.platform} • ${title.playTimeHours}h",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrophyTitleCard(
    title: GameTitle,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = title.iconUrl,
                contentDescription = title.titleName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title.titleName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${title.platform} • ${title.progress}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "B ${title.earnedTrophies.bronze}  S ${title.earnedTrophies.silver}  G ${title.earnedTrophies.gold}  P ${title.earnedTrophies.platinum}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CaptureGroupCard(
    group: CaptureGroup,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val latestCapture = group.captures.maxByOrNull { it.uploadDate.sortEpochMillis() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = latestCapture?.thumbnailModel ?: group.titleImageUrl,
                contentDescription = group.titleName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(group.titleName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${group.captures.size} captures • ${latestCapture?.uploadDate.toHumanDateTime() ?: "Timestamp unavailable"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(latestCapture?.captureTypeLabel ?: "Capture")
                        latestCapture?.resolution?.takeIf { it.isNotBlank() }?.let {
                            append(" • ")
                            append(it)
                        }
                        if (group.captures.any(CaptureEntry::isCachedOnly)) {
                            append(" • Includes older saved captures")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TrophyCard(trophy: TrophyEntry) {
    val metaLine = remember(trophy) {
        buildString {
            append(trophy.trophyType ?: "Unknown")
            trophy.earned?.let {
                append(" • ")
                append(if (it) "Earned" else "Not earned")
            }
            trophy.earnedRate?.let {
                append(" • ")
                append(it)
                append("% earned")
            }
        }
    }
    val secondaryLine = remember(trophy) {
        buildString {
            trophy.earnedAt?.takeIf { it.isNotBlank() }?.let {
                append("Earned ")
                append(it.toHumanDateTime() ?: it)
            }
            trophy.progressRate?.let {
                if (isNotEmpty()) {
                    append(" • ")
                }
                append("Progress ")
                append(it)
                append("%")
            }
        }
    }

    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AsyncImage(
                model = trophy.iconUrl,
                contentDescription = trophy.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(trophy.name ?: "Hidden trophy", style = MaterialTheme.typography.titleMedium)
                val detail = trophy.detail
                if (!detail.isNullOrBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(metaLine, style = MaterialTheme.typography.bodySmall)
                if (secondaryLine.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        secondaryLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureCard(
    capture: CaptureEntry,
    onOpen: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoxWithConstraints {
                AsyncImage(
                    model = capture.previewModel,
                    contentDescription = capture.description ?: capture.titleName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxWidth * 9f / 16f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpen),
                )
            }

            Text(
                text = capture.description?.takeIf { it.isNotBlank() } ?: capture.titleName,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                buildString {
                    append(capture.captureTypeLabel)
                    capture.uploadDate?.takeIf { it.isNotBlank() }?.let {
                        append(" • ")
                        append(it.toHumanDateTime() ?: it)
                    }
                    capture.resolution?.takeIf { it.isNotBlank() }?.let {
                        append(" • ")
                        append(it)
                    }
                    capture.videoDurationSeconds?.let {
                        append(" • ")
                        append("${it}s")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                buildString {
                    when {
                        capture.localPrimaryAssetGalleryUri != null -> append("Saved to Pictures/BetterTrophies")
                        capture.localPrimaryAssetPath != null -> append("Saved full quality locally")
                        capture.primaryAssetUrl != null -> append("Full quality available")
                        else -> append("Preview only")
                    }
                    capture.fileSizeBytes?.let {
                        append(" • ")
                        append(formatFileSize(it))
                    }
                    if (capture.isCachedOnly) {
                        append(" • Cached-only")
                    }
                    if (capture.isSpoiler == true) {
                        append(" • Spoiler")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CaptureViewerDialog(
    capture: CaptureEntry,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localGalleryUri by remember(capture.ugcId) { mutableStateOf(capture.localPrimaryAssetGalleryUri) }
    val resolvedCapture = remember(capture, localGalleryUri) {
        if (localGalleryUri == capture.localPrimaryAssetGalleryUri) {
            capture
        } else {
            capture.copy(localPrimaryAssetGalleryUri = localGalleryUri)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF10141B),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = capture.description?.takeIf { it.isNotBlank() } ?: capture.titleName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )

                if (capture.isVideoCapture) {
                    AsyncImage(
                        model = capture.thumbnailModel,
                        contentDescription = capture.description ?: capture.titleName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(24.dp)),
                    )
                    Text(
                        text = "Videos open in your preferred player. Screenshots render inline at full quality here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                } else {
                    AsyncImage(
                        model = capture.fullQualityModel,
                        contentDescription = capture.description ?: capture.titleName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                    )
                }

                Text(
                    text = buildString {
                        append(capture.captureTypeLabel)
                        capture.resolution?.takeIf { it.isNotBlank() }?.let {
                            append(" • ")
                            append(it)
                        }
                        capture.fileSizeBytes?.let {
                            append(" • ")
                            append(formatFileSize(it))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )

                Text(
                    text = if (resolvedCapture.localPrimaryAssetGalleryUri.isNullOrBlank()) {
                        "Download saves the full-quality file to Pictures/BetterTrophies."
                    } else {
                        "Saved to Pictures/BetterTrophies and ready to share."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )

                if (capture.isVideoCapture) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { openCaptureExternally(context, resolvedCapture) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Play")
                        }

                        OutlinedButton(
                            onClick = { shareCapture(context, resolvedCapture) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Share")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    localGalleryUri = downloadCaptureToGallery(context, resolvedCapture) ?: localGalleryUri
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (resolvedCapture.localPrimaryAssetGalleryUri.isNullOrBlank()) "Download" else "Saved")
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { shareCapture(context, resolvedCapture) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Share")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    localGalleryUri = downloadCaptureToGallery(context, resolvedCapture) ?: localGalleryUri
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (resolvedCapture.localPrimaryAssetGalleryUri.isNullOrBlank()) "Download" else "Saved")
                        }
                    }
                }
            }
        }
    }
}

private val CaptureEntry.thumbnailModel: Any?
    get() = localThumbnailPath?.let(::File) ?: thumbnailUrl

private val CaptureEntry.previewModel: Any?
    get() = localPrimaryAssetPath?.let(::File) ?: thumbnailModel ?: primaryAssetUrl

private val CaptureEntry.fullQualityModel: Any?
    get() = localPrimaryAssetGalleryUri?.let(Uri::parse) ?: localPrimaryAssetPath?.let(::File) ?: previewModel

private val CaptureEntry.isVideoCapture: Boolean
    get() = localPrimaryAssetContentType?.startsWith("video/") == true || captureType?.lowercase() == "video"

private val CaptureEntry.captureTypeLabel: String
    get() = when (captureType?.lowercase()) {
        "video" -> "Video"
        "screenshot" -> "Screenshot"
        else -> captureType?.replaceFirstChar { it.uppercase() } ?: "Capture"
    }

private data class TrophySection(
    val title: String?,
    val trophies: List<TrophyEntry>,
)

private fun List<TrophyEntry>.sectionsFor(option: TrophySortOption): List<TrophySection> {
    return when (option) {
        TrophySortOption.Default -> listOf(TrophySection(title = null, trophies = this))
        TrophySortOption.EarnedNewest -> buildList {
            val recent7 = this@sectionsFor
                .filter { it.earnedAt.daysSinceEarned()?.let { days -> days in 0..6 } == true }
                .sortedByDescending { it.earnedAt.sortEpochMillis() }
            if (recent7.isNotEmpty()) {
                add(TrophySection("Earned in last 7 days", recent7))
            }

            val recent30 = this@sectionsFor
                .filter { it.earnedAt.daysSinceEarned()?.let { days -> days in 7..29 } == true }
                .sortedByDescending { it.earnedAt.sortEpochMillis() }
            if (recent30.isNotEmpty()) {
                add(TrophySection("Earned in last 30 days", recent30))
            }

            val older = this@sectionsFor
                .filter { it.earnedAt.daysSinceEarned()?.let { days -> days >= 30 } == true }
                .sortedByDescending { it.earnedAt.sortEpochMillis() }
            if (older.isNotEmpty()) {
                add(TrophySection("Earned more than 30 days ago", older))
            }

            val unknownEarnedDate = this@sectionsFor.filter {
                it.earned == true && it.earnedAt?.isNotBlank() != false && it.earnedAt.daysSinceEarned() == null
            }.sortedBy { it.name.orEmpty() }
            if (unknownEarnedDate.isNotEmpty()) {
                add(TrophySection("Earned date unavailable", unknownEarnedDate))
            }

            val notEarned = this@sectionsFor.filter { it.earned != true }.sortedBy { it.name.orEmpty() }
            if (notEarned.isNotEmpty()) {
                add(TrophySection("Not earned", notEarned))
            }
        }
        TrophySortOption.NotEarnedFirst -> buildList {
            val notEarned = this@sectionsFor.filter { it.earned != true }.sortedBy { it.name.orEmpty() }
            if (notEarned.isNotEmpty()) {
                add(TrophySection("Not earned", notEarned))
            }

            val earned = this@sectionsFor
                .filter { it.earned == true }
                .sortedWith(
                    compareByDescending<TrophyEntry> { it.earnedAt.sortEpochMillis() }
                        .thenBy { it.name.orEmpty() },
                )
            if (earned.isNotEmpty()) {
                add(TrophySection("Earned", earned))
            }
        }
        TrophySortOption.Rarity -> listOf(
            TrophySection(
                title = null,
                trophies = sortedWith(
                    compareBy<TrophyEntry> { it.earnedRate.raritySortKey() }
                        .thenBy { it.rare ?: Int.MAX_VALUE }
                        .thenBy { it.name.orEmpty() },
                ),
            ),
        )
    }
}

private fun String?.sortEpochMillis(): Long {
    return this.toInstantOrNull()?.toEpochMilli() ?: Long.MIN_VALUE
}

private fun String?.raritySortKey(): Float {
    return this?.toFloatOrNull() ?: Float.MAX_VALUE
}

private fun String?.daysSinceEarned(now: Instant = Instant.now()): Long? {
    val instant = this.toInstantOrNull() ?: return null
    return ChronoUnit.DAYS.between(instant, now)
}

private fun String?.toInstantOrNull(): Instant? {
    if (this.isNullOrBlank()) {
        return null
    }

    return runCatching { Instant.parse(this) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(this).toInstant() }.getOrNull()
}

private fun String?.toHumanDateTime(now: Instant = Instant.now()): String? {
    val instant = this.toInstantOrNull() ?: return this?.takeIf { it.isNotBlank() }
    return instant.toHumanDateTime(now)
}

private fun Instant.toHumanDateTime(now: Instant = Instant.now()): String {
    val zoneId = deviceZoneId()
    val localDate = atZone(zoneId).toLocalDate()
    val nowLocalDate = now.atZone(zoneId).toLocalDate()
    val absolute = formatAbsoluteDateTime(zoneId, nowLocalDate)

    if (ChronoUnit.DAYS.between(localDate, nowLocalDate) !in 0..6) {
        return absolute
    }

    val relative = DateUtils.getRelativeTimeSpanString(
        toEpochMilli(),
        now.toEpochMilli(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    return "$relative ($absolute)"
}

private fun Instant.formatAbsoluteDateTime(
    zoneId: ZoneId,
    nowLocalDate: LocalDate,
): String {
    val zoned = atZone(zoneId)
    val skeleton = when {
        zoned.toLocalDate() == nowLocalDate -> "jm"
        zoned.year == nowLocalDate.year -> "MMM d, jm"
        else -> "MMM d, y, jm"
    }
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    return zoned.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

private fun deviceZoneId(): ZoneId = TimeZone.getDefault().toZoneId()

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        else -> "${bytes / (1024L * 1024L * 1024L)} GB"
    }
}

private fun openCaptureExternally(context: Context, capture: CaptureEntry) {
    val uri = resolveShareUri(capture) ?: run {
        Toast.makeText(context, "Full-quality media is still downloading.", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, capture.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    launchIntent(context, intent, "No app can open this capture yet.")
}

private fun shareCapture(context: Context, capture: CaptureEntry) {
    val uri = resolveShareUri(capture) ?: run {
        Toast.makeText(context, "Nothing to share yet.", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND)
        .setType(capture.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    launchIntent(context, Intent.createChooser(intent, "Share capture"), "No share targets are available.")
}

private fun launchIntent(
    context: Context,
    intent: Intent,
    failureMessage: String,
) {
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        if (it is ActivityNotFoundException) {
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, it.message ?: failureMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun resolveShareUri(capture: CaptureEntry): Uri? = AppServices.captureMediaStore.shareUriFor(capture)

private suspend fun downloadCaptureToGallery(
    context: Context,
    capture: CaptureEntry,
): String? {
    val savedUri = withContext(Dispatchers.IO) {
        AppServices.captureMediaStore.savePrimaryAssetToGallery(capture)
    }
    if (savedUri == null) {
        Toast.makeText(context, "No full-quality file is available to save yet.", Toast.LENGTH_SHORT).show()
        return null
    }
    Toast.makeText(context, "Saved to Pictures/BetterTrophies.", Toast.LENGTH_SHORT).show()
    return savedUri
}

private val CaptureEntry.mimeType: String
    get() = localPrimaryAssetContentType
        ?.substringBefore(';')
        ?.takeIf { it.isNotBlank() }
        ?: when {
            isVideoCapture -> "video/*"
            else -> "image/*"
        }

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
}

private fun formatCacheTimestamp(updatedAtEpochMs: Long): String {
    val relative = DateUtils.getRelativeTimeSpanString(
        updatedAtEpochMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "Saved $relative."
}
