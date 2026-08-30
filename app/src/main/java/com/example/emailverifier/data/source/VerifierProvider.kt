package com.example.emailverifier.data.source

import io.github.mbalatsko.emailverifier.EmailVerifier
import io.github.mbalatsko.emailverifier.emailVerifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Builds a single shared [EmailVerifier] and caches it for the whole app lifetime.
 *
 * While building, the library downloads the Public Suffix List and the
 * disposable-domain list (initialization is parallelized internally), so we must
 * create the verifier only once - the library README explicitly recommends
 * reusing one instance. A [Mutex] makes the lazy initialization thread-safe.
 */
object VerifierProvider {

    private val mutex = Mutex()
    private var verifier: EmailVerifier? = null

    suspend fun get(): EmailVerifier = mutex.withLock {
        verifier ?: buildVerifier().also { verifier = it }
    }

    private suspend fun buildVerifier(): EmailVerifier = emailVerifier {
        // 1) The domain must be registrable (valid public suffix, not a bare TLD).
        registrability { enabled = true }

        // 2) The domain must have MX records (it must be able to receive mail).
        mxRecord { enabled = true }

        // 3) Reject disposable / temporary email domains.
        disposability { enabled = true }

        // 4) SMTP deliverability - the decisive "is this mailbox real?" test.
        //    The library ships it DISABLED by default, so we enable it explicitly.
        smtp {
            enabled = true
            timeoutMillis = 10_000  // generous per-connection timeout
            maxRetries = 1          // one retry per MX server
            enableAllCatchCheck = true // detect catch-all servers to avoid false positives
        }

        // Informational checks that this app does not need
        // (disabling them makes initialization faster and reduces requests).
        gravatar { enabled = false }
        free { enabled = false }
        roleBasedUsername { enabled = false }
    }
}
