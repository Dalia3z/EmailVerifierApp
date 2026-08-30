package com.example.emailverifier.domain

import com.example.emailverifier.domain.model.VerificationStatus
import io.github.mbalatsko.emailverifier.EmailValidationResult
import io.github.mbalatsko.emailverifier.components.checkers.DatasetData
import io.github.mbalatsko.emailverifier.components.checkers.SmtpData
import io.github.mbalatsko.emailverifier.components.core.CheckResult

/**
 * Maps a library [EmailValidationResult] into our own (status, reason) pair.
 *
 * Decision order:
 *  1. Syntax / registrability / MX / disposable failures -> INVALID
 *     (fast, local/cheap, hard signals).
 *  2. SMTP result decides deliverability:
 *       Passed + isDeliverable          -> VALID
 *       Passed + !isDeliverable         -> INVALID (server rejected the mailbox)
 *       Failed                          -> INVALID with the server reply
 *       Errored (timeout/IOException)   -> FAILED  (the app must continue anyway)
 *       Skipped                         -> fallback to the library's heuristics
 *                                          (isLikelyDeliverable)
 */
object ResultMapper {

    fun map(result: EmailValidationResult): Pair<VerificationStatus, String> {
        // ---- 1) Hard local failures -----------------------------------------
        if (result.syntax is CheckResult.Failed) {
            return VerificationStatus.INVALID to "Invalid email syntax"
        }

        if (result.registrability is CheckResult.Failed) {
            return VerificationStatus.INVALID to "Domain is not registrable (invalid public suffix)"
        }

        if (result.mx is CheckResult.Failed) {
            return VerificationStatus.INVALID to "No MX records (domain cannot receive mail)"
        }

        val disposable = result.disposable
        if (disposable is CheckResult.Failed) {
            val matched = (disposable as CheckResult.Failed<DatasetData>).data?.matchedOn
            return VerificationStatus.INVALID to
                "Disposable / temporary domain${matched?.let { " ($it)" } ?: ""}"
        }

        // ---- 2) SMTP deliverability -----------------------------------------
        return when (val smtp = result.smtp) {
            is CheckResult.Passed -> {
                val data = (smtp as CheckResult.Passed<SmtpData>).data
                if (data.isDeliverable) {
                    VerificationStatus.VALID to "Deliverable (SMTP ${data.smtpCode})"
                } else {
                    VerificationStatus.INVALID to "SMTP rejected (${data.smtpCode}: ${data.smtpMessage})"
                }
            }

            is CheckResult.Failed -> {
                val data = (smtp as CheckResult.Failed<SmtpData>).data
                if (data != null && data.smtpCode != 0) {
                    VerificationStatus.INVALID to "SMTP rejected (${data.smtpCode}: ${data.smtpMessage})"
                } else {
                    VerificationStatus.INVALID to "SMTP rejected"
                }
            }

            is CheckResult.Errored -> {
                val error = smtp.error
                VerificationStatus.FAILED to "SMTP error: ${error.message ?: error.javaClass.simpleName}"
            }

            is CheckResult.Skipped -> {
                // SMTP was skipped (disabled/offline): use the library heuristics.
                if (result.isLikelyDeliverable()) {
                    VerificationStatus.VALID to "Likely deliverable (SMTP skipped)"
                } else {
                    VerificationStatus.INVALID to "Not likely deliverable (SMTP skipped)"
                }
            }
        }
    }
}
