package dev.gotlou.bettertrophies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AuthDashboardSection(
    state: MainUiState,
    onNpssoChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onPasteSignInUrl: () -> Unit,
    onCancelStoredTokenEdit: () -> Unit,
    onClearLogs: () -> Unit,
) {
    when {
        state.isRestoringStoredNpsso && state.dashboard == null -> {
            StartupStatusCard(
                logs = state.logLines,
                onClearLogs = onClearLogs,
            )
        }

        !state.hasStoredNpsso || state.isEditingStoredNpsso -> {
            NPSSOEntryCard(
                npsso = state.npsso,
                hasStoredNpsso = state.hasStoredNpsso,
                loading = state.isLoading,
                error = state.error,
                signInUrl = state.signInUrl,
                logs = state.logLines,
                onNpssoChanged = onNpssoChanged,
                onConnect = onConnect,
                onPasteSignInUrl = onPasteSignInUrl,
                onCancelEdit = onCancelStoredTokenEdit,
                onClearLogs = onClearLogs,
            )
        }
    }
}

@Composable
private fun StartupStatusCard(
    logs: List<String>,
    onClearLogs: () -> Unit,
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
            Text("Checking saved sign-in", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Looking for a stored NPSSO token before deciding whether the sign-in form is needed.",
                style = MaterialTheme.typography.bodyMedium,
            )
            CircularProgressIndicator()
            ActivityLogPanel(
                logs = logs,
                onClearLogs = onClearLogs,
            )
        }
    }
}

@Composable
private fun NPSSOEntryCard(
    npsso: String,
    hasStoredNpsso: Boolean,
    loading: Boolean,
    error: String?,
    signInUrl: String,
    logs: List<String>,
    onNpssoChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onPasteSignInUrl: () -> Unit,
    onCancelEdit: () -> Unit,
    onClearLogs: () -> Unit,
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
            Text(
                if (hasStoredNpsso) "Replace stored NPSSO token" else "Connect a PlayStation account",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (hasStoredNpsso) {
                    "Enter a replacement NPSSO token to refresh the saved credentials used by the app."
                } else {
                    "Paste an NPSSO token to load your profile, trophy summary, titles, and earned trophies."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = npsso,
                onValueChange = onNpssoChanged,
                label = { Text("NPSSO token") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConnect, enabled = !loading && npsso.isNotBlank()) {
                    Text(if (loading) "Loading..." else if (hasStoredNpsso) "Save and reload" else "Load trophies")
                }
                AssistChip(
                    onClick = onPasteSignInUrl,
                    label = { Text("Show sign-in URL") },
                    colors = AssistChipDefaults.assistChipColors(),
                )
                if (hasStoredNpsso) {
                    AssistChip(
                        onClick = onCancelEdit,
                        label = { Text("Cancel") },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
            }
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = signInUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            ActivityLogPanel(
                logs = logs,
                onClearLogs = onClearLogs,
            )
        }
    }
}

@Composable
private fun ActivityLogPanel(
    logs: List<String>,
    onClearLogs: () -> Unit,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activity log", style = MaterialTheme.typography.titleMedium)
            AssistChip(
                onClick = onClearLogs,
                label = { Text("Clear") },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(12.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "No activity yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    logs.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
