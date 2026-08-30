package com.example.emailverifier.data.source

import android.content.Context

/**
 * Stores the campaign-platform backend URL + API key.
 *
 * Uses plain SharedPreferences for simplicity. For production hardening, switch
 * to EncryptedSharedPreferences (androidx.security:security-crypto) - same API.
 */
object BackendSettings {

    private const val PREFS = "backend_settings"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"

    fun save(context: Context, baseUrl: String, apiKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun baseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, "") ?: ""

    fun apiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""
}
