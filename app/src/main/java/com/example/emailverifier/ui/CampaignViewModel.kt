package com.example.emailverifier.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emailverifier.data.local.EmailDatabase
import com.example.emailverifier.data.repository.EmailRepository
import com.example.emailverifier.data.source.BackendSettings
import com.example.emailverifier.data.source.CampaignApi
import com.example.emailverifier.domain.model.VerificationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Default professional HTML template (the server replaces {{unsubscribe_url}}). */
private const val DEFAULT_TEMPLATE = """
    <div style="font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1c1e">
      <h2 style="color:#1a6feb">Hello!</h2>
      <p>We're excited to share our latest update with you.</p>
      <p style="color:#555">This is a professional campaign email sent through the unified app.</p>
      <p><a href="{{unsubscribe_url}}" style="color:#999;font-size:12px">Unsubscribe</a></p>
    </div>
""".trimIndent()

/** Immutable UI state for the Campaigns tab. */
data class CampaignUiState(
    val phase: Phase = Phase.IDLE,
    val validCount: Int = 0,
    val baseUrl: String = "",
    val apiKey: String = "",
    val senderName: String = "",
    val subject: String = "",
    val html: String = DEFAULT_TEMPLATE,
    val campaignId: String? = null,
    val campaignStatus: String? = null,
    val total: Int = 0,
    val queued: Int = 0,
    val sent: Int = 0,
    val delivered: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val message: String? = null,
) {
    val done: Int get() = sent + delivered + failed

    enum class Phase { IDLE, SENDING, ACTIVE, DONE }
}

/**
 * MVVM ViewModel for the Campaigns tab: connects the verified emails with the
 * campaign-platform backend (send professional email + live status polling).
 */
class CampaignViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmailRepository(EmailDatabase.getInstance(application).emailDao())

    private var pollJob: Job? = null

    private val _uiState = MutableStateFlow(
        CampaignUiState(
            baseUrl = BackendSettings.baseUrl(application),
            apiKey = BackendSettings.apiKey(application),
        ),
    )
    val uiState: StateFlow<CampaignUiState> = _uiState.asStateFlow()

    init {
        refreshValidCount()
    }

    /** Refreshes the number of VALID emails available for sending. */
    fun refreshValidCount() {
        viewModelScope.launch {
            val valid = repository.countByStatus(VerificationStatus.VALID)
            _uiState.update { it.copy(validCount = valid) }
        }
    }

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value.trim().trimEnd('/')) }
    fun updateApiKey(value: String) = _uiState.update { it.copy(apiKey = value.trim()) }
    fun updateSenderName(value: String) = _uiState.update { it.copy(senderName = value) }
    fun updateSubject(value: String) = _uiState.update { it.copy(subject = value) }
    fun updateHtml(value: String) = _uiState.update { it.copy(html = value) }

    /** Persists the backend URL + API key locally. */
    fun saveSettings() {
        val s = _uiState.value
        if (s.baseUrl.isBlank() || s.apiKey.isBlank()) {
            _uiState.update { it.copy(message = "Backend URL and API key are required.") }
            return
        }
        BackendSettings.save(getApplication(), s.baseUrl, s.apiKey)
        _uiState.update { it.copy(message = "Settings saved.") }
    }

    /** Sends a professional email to all VALID emails via the campaign backend. */
    fun sendCampaign() {
        if (pollJob?.isActive == true) return
        val s = _uiState.value
        when {
            s.baseUrl.isBlank() || s.apiKey.isBlank() ->
                _uiState.update { it.copy(message = "Set Backend URL and API key first.") }
            s.validCount == 0 ->
                _uiState.update { it.copy(message = "No valid emails to send. Verify a list in the Email tab first.") }
            s.subject.isBlank() || s.html.isBlank() ->
                _uiState.update { it.copy(message = "Subject and HTML body are required.") }
            else -> startCampaign(s)
        }
    }

    private fun startCampaign(s: CampaignUiState) {
        pollJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = CampaignUiState.Phase.SENDING, message = "Sending campaign…") }
            try {
                val emails = withContext(Dispatchers.IO) {
                    repository.getByStatus(VerificationStatus.VALID).map { it.email }
                }
                val info = withContext(Dispatchers.IO) {
                    CampaignApi.createCampaign(
                        baseUrl = s.baseUrl,
                        apiKey = s.apiKey,
                        emails = emails,
                        subject = s.subject,
                        html = s.html,
                        senderName = s.senderName,
                    )
                }
                _uiState.update {
                    it.copy(
                        phase = CampaignUiState.Phase.ACTIVE,
                        campaignId = info.campaignId,
                        total = info.emailsQueued,
                        message = "Campaign queued (${info.emailsQueued} emails).",
                    )
                }
                pollStatus(info.campaignId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("Send failed", "Could not reach the campaign server. Check the URL and API key.", e)
            }
        }
    }

    /** Polls the backend every 5s until the campaign reaches a terminal state. */
    private suspend fun pollStatus(campaignId: String) {
        while (isActive) {
            delay(5_000)
            try {
                val s = _uiState.value
                val status = withContext(Dispatchers.IO) {
                    CampaignApi.getStatus(s.baseUrl, s.apiKey, campaignId)
                }
                _uiState.update {
                    it.copy(
                        campaignStatus = status.status,
                        total = status.total,
                        queued = status.queued,
                        sent = status.sent,
                        delivered = status.delivered,
                        failed = status.failed,
                        pending = status.pending,
                    )
                }
                val current = _uiState.value
                if (current.phase == CampaignUiState.Phase.ACTIVE &&
                    current.total > 0 &&
                    current.done >= current.total
                ) {
                    _uiState.update {
                        it.copy(
                            phase = CampaignUiState.Phase.DONE,
                            message = "Campaign finished: ${current.delivered} delivered, " +
                                "${current.failed} failed, ${current.sent} sent.",
                        )
                    }
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Transient network errors are fine - keep polling.
                Log.w("EmailVerifier", "poll failed: ${e.message}")
            }
        }
    }

    /** Stops polling and returns to the idle form. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        _uiState.update { it.copy(phase = CampaignUiState.Phase.IDLE, message = "Stopped.") }
    }

    /** Resets the campaign screen after it finished. */
    fun reset() {
        pollJob?.cancel()
        pollJob = null
        _uiState.update {
            it.copy(
                phase = CampaignUiState.Phase.IDLE,
                campaignId = null,
                campaignStatus = null,
                total = 0, queued = 0, sent = 0, delivered = 0, failed = 0, pending = 0,
                message = null,
            )
        }
        refreshValidCount()
    }

    private fun onError(tag: String, fallback: String, e: Exception) {
        Log.e("EmailVerifier", "$tag: ${e.message}", e)
        _uiState.update { it.copy(phase = CampaignUiState.Phase.IDLE, message = fallback) }
    }
}
