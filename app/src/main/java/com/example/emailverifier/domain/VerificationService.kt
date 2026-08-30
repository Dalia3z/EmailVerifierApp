package com.example.emailverifier.domain

import com.example.emailverifier.data.local.EmailEntity
import com.example.emailverifier.data.repository.EmailRepository
import com.example.emailverifier.data.source.VerifierProvider
import com.example.emailverifier.domain.model.EmailFormat
import com.example.emailverifier.domain.model.VerificationStatus
import io.github.mbalatsko.emailverifier.EmailVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * The "smart verification" engine.
 *
 * - Concurrency: up to [CONCURRENCY] emails are verified at the same time on
 *   [Dispatchers.IO] (coroutines on top of the library's own parallel checks).
 * - Smart delay: a global rate limiter spaces the START of every verification by
 *   at least [RATE_DELAY_MS], so SMTP servers see ~1 new connection every 500 ms
 *   and our IP address is far less likely to be throttled or blocked.
 * - Crash-safe: every result is persisted to Room immediately after it finishes,
 *   so closing the app or losing power leaves a consistent queue that can be
 *   resumed later from the last saved point.
 * - Resilient: a timeout / IOException / any unexpected error on a single email is
 *   recorded as FAILED and the batch simply continues without stopping.
 *
 * Cancelling the enclosing Job stops the loop; emails that are still PENDING stay
 * in the database and can be resumed later.
 */
class VerificationService(
    private val repository: EmailRepository,
    private val provider: VerifierProvider = VerifierProvider,
) {

    companion object {
        /** How many emails run in parallel (5, as required). */
        const val CONCURRENCY = 5

        /** Minimum gap (ms) between the start of two verifications. */
        const val RATE_DELAY_MS = 500L
    }

    // Shared by all worker coroutines so every email gets its own 500 ms slot.
    private val rateMutex = Mutex()
    private var lastStartMs = 0L

    /** Verifies every PENDING email in the database, chunk by chunk. */
    suspend fun verifyAllPending() {
        val verifier = provider.get() // built only once, cached for the app lifetime
        val pending = repository.getPending()
        if (pending.isEmpty()) return

        // Process in chunks of CONCURRENCY; wait for a whole chunk before the next.
        pending.chunked(CONCURRENCY).forEach { chunk ->
            coroutineScope {
                val jobs = chunk.map { entity ->
                    async(Dispatchers.IO) { verifyOne(entity, verifier) }
                }
                jobs.forEach { it.await() }
            }
        }
    }

    private suspend fun verifyOne(entity: EmailEntity, verifier: EmailVerifier) {
        try {
            // ---- smart delay: never start a check before its 500 ms slot ----
            rateMutex.withLock {
                val now = System.currentTimeMillis()
                val wait = RATE_DELAY_MS - (now - lastStartMs)
                if (wait > 0) delay(wait)
                lastStartMs = System.currentTimeMillis()
            }
            currentCoroutineContext().ensureActive()

            // ---- local pre-check: reject addresses the verifier cannot handle ----
            // emailverifier-kt internally calls java.net.IDN.toASCII() while parsing
            // the domain, which throws IllegalArgumentException ("Invalid input to
            // toASCII: ...") for characters that cannot be converted (e.g. Arabic /
            // Thai / emoji in some Unicode ranges, spaces, control chars...).
            // CsvParser already filters these at import time; this check is a second
            // layer of defense for PENDING rows imported by older builds. We classify
            // such emails as a syntax error (INVALID) and continue.
            if (!EmailFormat.isValid(entity.email)) {
                repository.saveResult(
                    entity.id,
                    VerificationStatus.INVALID,
                    "Invalid email syntax (non-ASCII or unsupported characters)",
                )
                return
            }

            // ---- run the actual verification ----
            // (the library catches per-check exceptions itself and returns
            //  CheckResult.Errored, so a single bad email can never crash us here)
            val validation = verifier.verify(entity.email)
            val (status, reason) = ResultMapper.map(validation)

            // ---- persist immediately (crash-safe progress) ----
            repository.saveResult(entity.id, status, reason)
        } catch (e: CancellationException) {
            throw e // never swallow cancellation
        } catch (e: IllegalArgumentException) {
            // Defensive net for any leftover parsing error (e.g. IDN.toASCII) on
            // unusual input: record it as a syntax error (INVALID), not a crash.
            repository.saveResult(
                entity.id,
                VerificationStatus.INVALID,
                "Invalid email syntax: ${e.message ?: "unsupported characters"}",
            )
        } catch (e: Exception) {
            // Timeout / IOException / any unexpected error => mark FAILED, continue.
            val detail = when (e) {
                is IOException -> e.message ?: "I/O failure"
                else -> e.message ?: e.javaClass.simpleName
            }
            repository.saveResult(entity.id, VerificationStatus.FAILED, "Verification error: $detail")
        }
    }
}
