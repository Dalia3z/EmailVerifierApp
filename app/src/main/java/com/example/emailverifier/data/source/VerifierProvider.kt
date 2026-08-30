package com.example.emailverifier.data.source

import io.github.mbalatsko.emailverifier.EmailVerifier
import io.github.mbalatsko.emailverifier.emailVerifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Builds a single shared [EmailVerifier] and caches it for the whole app lifetime.
 *
 * A [Mutex] makes the lazy initialization thread-safe.
 *
 * IMPORTANT: the dataset checks (PSL + disposable domains) are configured with
 * `offline = true`, i.e. they are loaded from the resources BUNDLED inside the
 * library jar (offline-data/psl.txt and offline-data/disposable.txt, verified to
 * exist in the artifact). Building the verifier therefore does NOT touch the
 * network, so a slow/blocked connection can never produce a spurious
 * "check your internet connection" error at startup. Only the per-email checks
 * (MX lookup via DNS-over-HTTPS + SMTP) require internet, and failures there are
 * recorded per-row (FAILED) instead of stopping the app.
 */
object VerifierProvider {

    private val mutex = Mutex()
    private var verifier: EmailVerifier? = null

    // Shared HTTP client with explicit timeouts so no request can hang forever.
    private val httpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
            }
        }
    }

    suspend fun get(): EmailVerifier = mutex.withLock {
        verifier ?: buildVerifier().also { verifier = it }
    }

    private suspend fun buildVerifier(): EmailVerifier = emailVerifier {
        // Use the shared client (with timeouts + retry) for the remaining calls.
        httpClient = this@VerifierProvider.httpClient

        // 1) The domain must be registrable (valid public suffix, not a bare TLD).
        //    offline = true -> bundled PSL resource, no download required.
        registrability {
            enabled = true
            offline = true
        }

        // 2) The domain must have MX records (it must be able to receive mail).
        mxRecord { enabled = true }

        // 3) Reject disposable / temporary email domains.
        //    offline = true -> bundled disposable list, no download required.
        disposability {
            enabled = true
            offline = true
        }

        // 4) SMTP deliverability - the decisive "is this mailbox real?" test.
        //    The library ships it DISABLED by default, so we enable it explicitly.
        smtp {
            enabled = true
            timeoutMillis = 10_000  // generous per-connection timeout
            maxRetries = 1          // one retry per MX server
            enableAllCatchCheck = true // detect catch-all servers to avoid false positives
        }

        // Informational checks that this app does not need.
        gravatar { enabled = false }
        free { enabled = false }
        roleBasedUsername { enabled = false }
    }
}

