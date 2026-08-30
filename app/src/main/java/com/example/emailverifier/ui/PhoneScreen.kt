package com.example.emailverifier.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Which export button is currently clicked (used by the legacy permission flow). */
private enum class PhoneExportAction { VALID, INVALID }

/**
 * Phone Number Validator tab: imports a CSV/TXT of phone numbers, validates them
 * locally with libphonenumber (fast, no network), shows live progress and lets the
 * user export valid / invalid results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(
    modifier: Modifier = Modifier,
    viewModel: PhoneViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF file picker: reading the input file needs NO storage permission.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importAndValidate(it, state.defaultRegion) } }

    // Legacy WRITE_EXTERNAL_STORAGE permission - only relevant on API <= 28.
    var exportAction by remember { mutableStateOf(PhoneExportAction.VALID) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            when (exportAction) {
                PhoneExportAction.VALID -> viewModel.exportValid()
                PhoneExportAction.INVALID -> viewModel.exportInvalid()
            }
        }
    }

    fun requestExport(action: PhoneExportAction) {
        exportAction = action
        val needsLegacyPermission =
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            when (action) {
                PhoneExportAction.VALID -> viewModel.exportValid()
                PhoneExportAction.INVALID -> viewModel.exportInvalid()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Default country code used to parse local numbers (e.g. "06..." in Morocco).
        OutlinedTextField(
            value = state.defaultRegion,
            onValueChange = viewModel::updateDefaultRegion,
            label = { Text("Default country code (e.g. MA)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        PhoneStatusCard(state)

        when (state.phase) {
            PhoneUiState.Phase.IDLE -> {
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text("  Select CSV/TXT file & validate")
                }

                if (state.pendingCount > 0) {
                    OutlinedButton(
                        onClick = viewModel::resumeValidation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("  Resume (${state.pendingCount} pending)")
                    }
                }
            }

            PhoneUiState.Phase.VERIFYING -> {
                Button(
                    onClick = viewModel::stopValidation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("  Stop & save progress")
                }
            }

            PhoneUiState.Phase.DONE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { requestExport(PhoneExportAction.VALID) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Text("  Valid (${state.validCount})")
                    }
                    Button(
                        onClick = { requestExport(PhoneExportAction.INVALID) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Text("  Invalid (${state.invalidCount + state.failedCount})")
                    }
                }
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text("  Start a new batch")
                }
            }
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        state.lastExportPath?.let {
            Text(
                "Exported to: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Card showing phone batch counters, the live progress bar and the running state. */
@Composable
private fun PhoneStatusCard(state: PhoneUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Batch status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (state.total == 0) {
                Text("No data yet. Select a CSV/TXT file containing phone numbers to begin.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatItem("Total", state.total.toString())
                    StatItem("Valid", state.validCount.toString(), MaterialTheme.colorScheme.primary)
                    StatItem("Invalid", state.invalidCount.toString(), MaterialTheme.colorScheme.error)
                    StatItem("Failed", state.failedCount.toString(), MaterialTheme.colorScheme.tertiary)
                    StatItem("Pending", state.pendingCount.toString())
                }

                // Live progress bar (0..1).
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "Checked: ${state.processed} / ${state.total}  (${(state.progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (state.phase == PhoneUiState.Phase.VERIFYING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Validating…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
