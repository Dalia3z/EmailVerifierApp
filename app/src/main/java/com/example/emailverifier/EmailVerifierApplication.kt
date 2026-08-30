package com.example.emailverifier

import android.app.Application

/**
 * Application class.
 *
 * It is the root of the (intentionally simple) manual dependency wiring:
 * [com.example.emailverifier.ui.MainViewModel] obtains the Room database and the
 * repository on demand through [com.example.emailverifier.data.local.EmailDatabase].
 *
 * Declared in AndroidManifest as `android:name=".EmailVerifierApplication"`.
 */
class EmailVerifierApplication : Application()
