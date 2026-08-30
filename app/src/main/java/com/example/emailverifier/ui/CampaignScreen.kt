package com.example.emailverifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Campaigns tab: sends a professional email to the VALID emails produced by the
 * Email tab, through the campaign-platform backend, and shows live status.
 */
@Composable
fun CampaignScreen(
    modifier: Modifier = Modifier,
    viewModel: CampaignViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh the number of valid emails every time this tab becomes visible.
    LaunchedEffect(Unit) { viewModel.refreshValidCount() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Campaigns",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Send a professional email to the ${state.validCount} validated emails " +
                "from the Email tab.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // ---- backend connection settings ----
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            label = { Text("Backend URL (https://your-domain.com)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = viewModel::updateApiKey,
            label = { Text("API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = viewModel::saveSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save settings")
        }

        // ---- campaign content ----
        OutlinedTextField(
            value = state.senderName,
            onValueChange = viewModel::updateSenderName,
            label = { Text("Sender name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.subject,
            onValueChange = viewModel::updateSubject,
            label = { Text("Email subject") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.html,
            onValueChange = viewModel::updateHtml,
            label = { Text("HTML body") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- phase-dependent actions / progress ----
        when (state.phase) {
            CampaignUiState.Phase.IDLE -> {
                Button(
                    onClick = viewModel::sendCampaign,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Text("  Send to ${state.validCount} valid emails")
                }
            }

            CampaignUiState.Phase.SENDING -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Sending campaign…", style = MaterialTheme.typography.bodyMedium)
            }

            CampaignUiState.Phase.ACTIVE -> {
                CampaignProgressCard(state)
                OutlinedButton(
                    onClick = viewModel::stop,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop polling")
                }
            }

            CampaignUiState.Phase.DONE -> {
                CampaignProgressCard(state)
                Button(
                    onClick = viewModel::reset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("New campaign")
                }
            }
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Live progress card while a campaign is running / finished. */
@Composable
private fun CampaignProgressCard(state: CampaignUiState) {
    val progress = if (state.total == 0) 0f else state.done.toFloat() / state.total
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Campaign ${state.campaignId?.take(8) ?: ""} · ${state.campaignStatus ?: "…"}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "Done ${state.done}/${state.total} (${(progress * 100).toInt()}%)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem("Sent", state.sent.toString())
                StatItem("Delivered", state.delivered.toString(), MaterialTheme.colorScheme.primary)
                StatItem("Failed", state.failed.toString(), MaterialTheme.colorScheme.error)
                StatItem("Pending", state.pending.toString())
                StatItem("Queued", state.queued.toString())
            }
            if (state.phase == CampaignUiState.Phase.ACTIVE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Polling…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
