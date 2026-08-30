package com.example.emailverifier.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emailverifier.data.local.EmailDatabase
import com.example.emailverifier.data.repository.EmailRepository
import com.example.emailverifier.data.source.CsvExporter
import com.example.emailverifier.data.source.CsvParser
import com.example.emailverifier.domain.VerificationService
import com.example.emailverifier.domain.model.VerificationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immutable UI state that Compose collects. Everything the screen needs to render.
 */
data class UiState(
    val phase: Phase = Phase.IDLE,
    val total: Int = 0,
    val validCount: Int = 0,
    val invalidCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val progress: Float = 0f,
    val message: String? = null,
    val lastExportPath: String? = null,
) {
    val processed: Int get() = validCount + invalidCount + failedCount

    enum class Phase { IDLE, VERIFYING, DONE }
}

/**
 * MVVM ViewModel.
 *
 * Exposes a single [StateFlow] driven by live Room counters, so the progress bar
 * updates instantly after every email is saved. It also owns the verification job
 * so the algorithm can be stopped and resumed at any time.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = EmailDatabase.getInstance(application)
    private val repository = EmailRepository(db.emailDao())
    private val service = VerificationService(repository)

    private var verifyJob: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Live counters: every DB write is reflected immediately in the UI.
        viewModelScope.launch {
            combine(
                repository.observeTotal(),
                repository.observeCount(VerificationStatus.VALID),
                repository.observeCount(VerificationStatus.INVALID),
                repository.observeCount(VerificationStatus.FAILED),
                repository.observeCount(VerificationStatus.PENDING),
            ) { total, valid, invalid, failed, pending ->
                val processed = valid + invalid + failed
                _uiState.update { state ->
                    state.copy(
                        total = total,
                        validCount = valid,
                        invalidCount = invalid,
                        failedCount = failed,
                        pendingCount = pending,
                        progress = if (total == 0) 0f else processed.toFloat() / total,
                    )
                }
            }.collect()
        }

        // If a previous run was interrupted, tell the user they can resume.
        viewModelScope.launch {
            val pending = repository.countByStatus(VerificationStatus.PENDING)
            if (pending > 0) {
                _uiState.update {
                    it.copy(
                        message = "There is an unfinished batch ($pending emails). " +
                            "You can resume it or start a new one.",
                    )
                }
            }
        }
    }

    /** Opens + parses the user-selected file and immediately starts verification. */
    fun importAndVerify(uri: Uri) {
        if (verifyJob?.isActive == true) return
        verifyJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = UiState.Phase.VERIFYING, message = "Importing file…") }
            try {
                // Parsing 10k lines is IO-bound: never do it on the main thread.
                val emails = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { CsvParser.parseEmails(it) }
                } ?: throw IllegalStateException("Cannot open the selected file")

                if (emails.isEmpty()) throw IllegalStateException("No email addresses found in the file")

                // Start a fresh batch: wipe the old database, insert the new list.
                repository.clearAll()
                repository.importEmails(emails)
                _uiState.update { it.copy(message = "Imported ${emails.size} emails. Verifying…") }

                service.verifyAllPending()
                _uiState.update { it.copy(phase = UiState.Phase.DONE, message = "Verification finished.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(phase = UiState.Phase.IDLE, message = e.message ?: "Import failed") }
            }
        }
    }

    /** Continues verification of the emails that are still PENDING (resume). */
    fun resumeVerification() {
        if (verifyJob?.isActive == true) return
        verifyJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = UiState.Phase.VERIFYING, message = "Resuming verification…") }
            try {
                service.verifyAllPending()
                _uiState.update { it.copy(phase = UiState.Phase.DONE, message = "Verification finished.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(phase = UiState.Phase.IDLE, message = e.message ?: "Verification failed") }
            }
        }
    }

    /** Stops the current run. Everything already saved stays; the rest remains PENDING. */
    fun stopVerification() {
        verifyJob?.cancel()
        verifyJob = null
        _uiState.update {
            it.copy(
                phase = UiState.Phase.IDLE,
                message = "Stopped. Progress is saved - you can resume anytime.",
            )
        }
    }

    /** Exports the VALID addresses to a CSV file in Downloads. */
    fun exportValid() {
        viewModelScope.launch {
            try {
                val emails = repository.getByStatus(VerificationStatus.VALID).map { it.email }
                if (emails.isEmpty()) {
                    _uiState.update { it.copy(message = "No valid emails to export") }
                    return@launch
                }
                val path = withContext(Dispatchers.IO) {
                    CsvExporter.exportValid(getApplication(), emails)
                }
                _uiState.update {
                    it.copy(message = "Exported ${emails.size} valid emails", lastExportPath = path)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Export failed") }
            }
        }
    }

    /** Exports INVALID + FAILED addresses (with their reasons) to a CSV file in Downloads. */
    fun exportInvalid() {
        viewModelScope.launch {
            try {
                val invalid = repository.getByStatus(VerificationStatus.INVALID)
                    .map { it.email to (it.reason ?: "undeliverable") }
                val failed = repository.getByStatus(VerificationStatus.FAILED)
                    .map { it.email to (it.reason ?: "verification failed") }
                val rows = invalid + failed
                if (rows.isEmpty()) {
                    _uiState.update { it.copy(message = "No invalid emails to export") }
                    return@launch
                }
                val path = withContext(Dispatchers.IO) {
                    CsvExporter.exportInvalid(getApplication(), rows)
                }
                _uiState.update {
                    it.copy(message = "Exported ${rows.size} invalid emails", lastExportPath = path)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Export failed") }
            }
        }
    }
}
