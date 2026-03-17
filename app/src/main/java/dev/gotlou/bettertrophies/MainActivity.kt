package dev.gotlou.bettertrophies

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.gotlou.bettertrophies.ui.theme.BetterTrophiesTheme

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
                        MainScreen.TrophyDetail -> viewModel.showGamesScreen()
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
                onPasteSignInUrl = viewModel::useSignInUrl,
                onCancelStoredTokenEdit = viewModel::cancelStoredTokenEdit,
                onClearStoredToken = viewModel::clearStoredToken,
                onClearLogs = viewModel::clearLogs,
                onShowGames = viewModel::showGamesScreen,
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
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    state: MainUiState,
    onNpssoChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onPasteSignInUrl: () -> Unit,
    onCancelStoredTokenEdit: () -> Unit,
    onClearStoredToken: () -> Unit,
    onClearLogs: () -> Unit,
    onShowGames: () -> Unit,
    onOpenRecentTitle: (RecentTitle) -> Unit,
) {
    val showAuthModule = state.isRestoringStoredNpsso || !state.hasStoredNpsso || state.isEditingStoredNpsso

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
) {
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

        if (state.isLoadingTrophies) {
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
            items(state.trophies, key = { it.trophyId }) { trophy ->
                TrophyCard(trophy = trophy)
            }
        }
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
                StatPill("Bronze", bronze)
                StatPill("Silver", silver)
                StatPill("Gold", gold)
                StatPill("Platinum", platinum)
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
                    .clickable { onSelect(title) },
            ) {
                Column {
                    AsyncImage(
                        model = title.coverUrl,
                        contentDescription = title.titleName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp),
                    )
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(title.titleName, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(
                            "${title.platform} • ${title.playTimeHours}h",
                            style = MaterialTheme.typography.bodySmall,
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
private fun TrophyCard(trophy: TrophyEntry) {
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
                Text(
                    buildString {
                        append(trophy.trophyType ?: "Unknown")
                        trophy.earned?.let {
                            append(" • ")
                            append(if (it) "Earned" else "Not earned")
                        }
                        trophy.earnedRate?.let {
                            append(" • ")
                            append(it)
                            append("%")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
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
