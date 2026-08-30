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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Which export button is currently clicked (used by the legacy permission flow). */
private enum class ExportAction { VALID, INVALID }

/**
 * Root screen: Material 3 tabs to switch between the Email Verifier and the
 * Phone Number Validator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Email Verifier") })
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Email Verifier") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Phone Validator") },
                    )
                }
            }
        },
    ) { innerPadding ->
        if (selectedTab == 0) {
            EmailVerifierContent(modifier = Modifier.padding(innerPadding))
        } else {
            PhoneScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

/**
 * Email verification tab (the original single-screen content). Reads
 * [MainViewModel.uiState] via StateFlow and renders the status card (live counters
 * + progress bar), the phase-dependent actions, the SAF file picker and the legacy
 * WRITE permission launcher (API <= 28).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailVerifierContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF file picker: reading the input file needs NO storage permission.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importAndVerify) }

    // Legacy WRITE_EXTERNAL_STORAGE permission - only relevant on API <= 28.
    var exportAction by remember { mutableStateOf(ExportAction.VALID) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            when (exportAction) {
                ExportAction.VALID -> viewModel.exportValid()
                ExportAction.INVALID -> viewModel.exportInvalid()
            }
        }
    }

    fun requestExport(action: ExportAction) {
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
                ExportAction.VALID -> viewModel.exportValid()
                ExportAction.INVALID -> viewModel.exportInvalid()
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
            // Build identity (run number + commit sha) so we can always tell which
            // APK is installed: generated by the CI workflow into assets/build-info.txt.
            Text(
                "Build: ${state.buildInfo}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StatusCard(state)
            when (state.phase) {
                UiState.Phase.IDLE -> {
                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Text("  Select CSV/TXT file & verify")
                    }

                    if (state.pendingCount > 0) {
                        OutlinedButton(
                            onClick = viewModel::resumeVerification,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("  Resume (${state.pendingCount} pending)")
                        }
                    }
                }

                UiState.Phase.VERIFYING -> {
                    Button(
                        onClick = viewModel::stopVerification,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Text("  Stop & save progress")
                    }
                }

                UiState.Phase.DONE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { requestExport(ExportAction.VALID) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Text("  Valid (${state.validCount})")
                        }
                        Button(
                            onClick = { requestExport(ExportAction.INVALID) },
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

/** Card showing batch counters, the live progress bar and the running state. */
@Composable
private fun StatusCard(state: UiState) {
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
                Text("No data yet. Select a CSV/TXT file containing email addresses to begin.")
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

                if (state.phase == UiState.Phase.VERIFYING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Verifying…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** One small "value + label" item used inside status cards (shared with PhoneScreen). */
@Composable
internal fun StatItem(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
