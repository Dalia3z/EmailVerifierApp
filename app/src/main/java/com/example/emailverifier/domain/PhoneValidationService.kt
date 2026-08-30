package com.example.emailverifier.domain

import com.example.emailverifier.data.local.PhoneNumberEntity
import com.example.emailverifier.data.repository.PhoneNumberRepository
import com.example.emailverifier.domain.model.VerificationStatus
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Local phone-number validation engine (no network, no IDN -> no toASCII crashes).
 *
 * Uses Google libphonenumber to:
 *  - validate the number (isValidNumber),
 *  - detect the line type (MOBILE / FIXED_LINE / ...),
 *  - format it to the standard international E.164 form (+2126...).
 *
 * Validation is fast and purely local, so batches are processed concurrently
 * WITHOUT the 500 ms delay used for emails (no SMTP rate-limit concern).
 * Every result is persisted to Room immediately (crash-safe, resumable).
 */
class PhoneValidationService(
    private val repository: PhoneNumberRepository,
) {

    companion object {
        /** How many numbers run in parallel. */
        const val CONCURRENCY = 5
    }

    // PhoneNumberUtil is officially thread-safe, so one shared instance is enough.
    private val phoneUtil = PhoneNumberUtil.getInstance()

    /** Validates every PENDING number in the database, chunk by chunk. */
    suspend fun validateAllPending() {
        val pending = repository.getPending()
        if (pending.isEmpty()) return

        pending.chunked(CONCURRENCY).forEach { chunk ->
            coroutineScope {
                val jobs = chunk.map { entity ->
                    async(Dispatchers.IO) { validateOne(entity) }
                }
                jobs.forEach { it.await() }
            }
        }
    }

    private suspend fun validateOne(entity: PhoneNumberEntity) {
        try {
            val result = validate(entity.rawNumber, entity.defaultRegion)
            repository.saveResult(
                entity.id,
                result.status,
                result.numberType,
                result.formattedE164,
                result.reason,
            )
        } catch (e: CancellationException) {
            throw e // never swallow cancellation
        } catch (e: Exception) {
            // Defensive: an unexpected error must never stop the batch.
            repository.saveResult(
                entity.id,
                VerificationStatus.FAILED,
                null,
                null,
                "Validation error: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    /** Validates one raw number against the given default region (e.g. "MA"). */
    fun validate(rawNumber: String, defaultRegion: String): PhoneValidationResult {
        val number = try {
            phoneUtil.parse(rawNumber, defaultRegion.uppercase())
        } catch (e: NumberParseException) {
            // NOT_A_NUMBER / TOO_SHORT / TOO_LONG / INVALID_COUNTRY_CODE ...
            return PhoneValidationResult(
                status = VerificationStatus.INVALID,
                reason = e.errorType.name.lowercase().replace('_', ' '),
            )
        }

        if (!phoneUtil.isValidNumber(number)) {
            return PhoneValidationResult(
                status = VerificationStatus.INVALID,
                reason = "Not a valid number",
            )
        }

        val type = phoneUtil.getNumberType(number).name
        val e164 = phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        return PhoneValidationResult(
            status = VerificationStatus.VALID,
            numberType = type,
            formattedE164 = e164,
            reason = "$type · $e164",
        )
    }
}

/** Result of validating one phone number. */
data class PhoneValidationResult(
    val status: VerificationStatus,
    val numberType: String? = null,
    val formattedE164: String? = null,
    val reason: String? = null,
)
