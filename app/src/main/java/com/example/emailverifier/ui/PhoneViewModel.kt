package com.example.emailverifier.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emailverifier.data.local.EmailDatabase
import com.example.emailverifier.data.local.PhoneNumberEntity
import com.example.emailverifier.data.repository.PhoneNumberRepository
import com.example.emailverifier.data.source.PhoneCsvExporter
import com.example.emailverifier.data.source.PhoneCsvParser
import com.example.emailverifier.domain.PhoneValidationService
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

/** Immutable UI state for the Phone Validator tab. */
data class PhoneUiState(
    val phase: Phase = Phase.IDLE,
    val total: Int = 0,
    val validCount: Int = 0,
    val invalidCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val progress: Float = 0f,
    val defaultRegion: String = "",
    val message: String? = null,
    val lastExportPath: String? = null,
) {
    val processed: Int get() = validCount + invalidCount + failedCount

    enum class Phase { IDLE, VERIFYING, DONE }
}

/**
 * MVVM ViewModel for the Phone Number Validator tab.
 *
 * Exposes one [StateFlow] driven by live Room counters. Phone validation is
 * fully local (libphonenumber), so it is fast and cannot fail due to network or
 * IDN/toASCII parsing.
 */
class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val db = EmailDatabase.getInstance(application)
    private val repository = PhoneNumberRepository(db.phoneNumberDao())
    private val service = PhoneValidationService(repository)

    private var job: Job? = null

    private val _uiState = MutableStateFlow(
        PhoneUiState(defaultRegion = defaultRegion(application)),
    )
    val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()

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
                        message = "There is an unfinished batch ($pending numbers). " +
                            "You can resume it or start a new one.",
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "EmailVerifier"
    }

    /** Updates the default country code the user types in the UI. */
    fun updateDefaultRegion(region: String) {
        _uiState.update { it.copy(defaultRegion = region.trim().uppercase().take(3)) }
    }

    /** Opens + parses the user-selected file and immediately starts validation. */
    fun importAndValidate(uri: Uri, region: String) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _uiState.update { it.copy(phase = PhoneUiState.Phase.VERIFYING, message = "Importing file…") }
            try {
                // Parsing is IO-bound: never do it on the main thread.
                val parsed = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { PhoneCsvParser.parsePhones(it) }
                } ?: throw IllegalStateException("Cannot open the selected file")

                if (parsed.numbers.isEmpty()) throw IllegalStateException("No phone numbers found in the file")

                val regionNormalized = region.trim().uppercase().take(3)
                val now = System.currentTimeMillis()
                repository.clearAll()
                repository.importPhones(
                    parsed.numbers.map {
                        PhoneNumberEntity(
                            rawNumber = it,
                            defaultRegion = regionNormalized,
                            status = VerificationStatus.PENDING.name,
                            timestamp = now,
                        )
                    },
                )
                _uiState.update {
                    it.copy(
                        message = "Imported ${parsed.numbers.size} numbers " +
                            "(${parsed.skipped} lines skipped). Validating…",
                    )
                }

                service.validateAllPending()
                _uiState.update { it.copy(phase = PhoneUiState.Phase.DONE, message = "Validation finished.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("Import failed", "Import failed. Check the file and try again.", e)
            }
        }
    }

    /** Continues validation of the numbers that are still PENDING (resume). */
    fun resumeValidation() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _uiState.update { it.copy(phase = PhoneUiState.Phase.VERIFYING, message = "Resuming validation…") }
            try {
                service.validateAllPending()
                _uiState.update { it.copy(phase = PhoneUiState.Phase.DONE, message = "Validation finished.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("Validation failed", "Validation failed. Please try again.", e)
            }
        }
    }

    /** Stops the current run. Already-saved results stay; the rest remain PENDING. */
    fun stopValidation() {
        job?.cancel()
        job = null
        _uiState.update {
            it.copy(
                phase = PhoneUiState.Phase.IDLE,
                message = "Stopped. Progress is saved - you can resume anytime.",
            )
        }
    }

    /** Exports the VALID numbers to a CSV file in Downloads. */
    fun exportValid() {
        viewModelScope.launch {
            try {
                val rows = repository.getByStatus(VerificationStatus.VALID)
                    .map { Triple(it.rawNumber, it.formattedE164 ?: "", it.numberType ?: "") }
                if (rows.isEmpty()) {
                    _uiState.update { it.copy(message = "No valid numbers to export") }
                    return@launch
                }
                val path = withContext(Dispatchers.IO) {
                    PhoneCsvExporter.exportValid(getApplication(), rows)
                }
                _uiState.update {
                    it.copy(message = "Exported ${rows.size} valid numbers", lastExportPath = path)
                }
            } catch (e: Exception) {
                onError("Export valid failed", "Export failed. Please try again.", e, resetPhase = false)
            }
        }
    }

    /** Exports INVALID + FAILED numbers (with their reasons) to a CSV file in Downloads. */
    fun exportInvalid() {
        viewModelScope.launch {
            try {
                val invalid = repository.getByStatus(VerificationStatus.INVALID)
                    .map { it.rawNumber to (it.reason ?: "invalid") }
                val failed = repository.getByStatus(VerificationStatus.FAILED)
                    .map { it.rawNumber to (it.reason ?: "validation failed") }
                val rows = invalid + failed
                if (rows.isEmpty()) {
                    _uiState.update { it.copy(message = "No invalid numbers to export") }
                    return@launch
                }
                val path = withContext(Dispatchers.IO) {
                    PhoneCsvExporter.exportInvalid(getApplication(), rows)
                }
                _uiState.update {
                    it.copy(message = "Exported ${rows.size} invalid numbers", lastExportPath = path)
                }
            } catch (e: Exception) {
                onError("Export invalid failed", "Export failed. Please try again.", e, resetPhase = false)
            }
        }
    }

    /** Logs the real error (Logcat) and shows ONLY a friendly message in the UI. */
    private fun onError(tag: String, fallback: String, e: Exception, resetPhase: Boolean = true) {
        Log.e(TAG, "$tag: ${e.message}", e)
        _uiState.update { state ->
            state.copy(
                phase = if (resetPhase) PhoneUiState.Phase.IDLE else state.phase,
                message = fallback,
            )
        }
    }

    /** The ISO country code of the device (e.g. "MA"), with a sensible fallback. */
    private fun defaultRegion(app: Application): String {
        val locales = app.resources.configuration.locales
        val country = if (locales.size() > 0) locales[0].country else ""
        return country.ifBlank { "MA" }
    }
}
