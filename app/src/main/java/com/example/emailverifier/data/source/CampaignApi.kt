package com.example.emailverifier.data.source

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Result of creating a campaign on the backend. */
data class CampaignInfo(
    val campaignId: String,
    val emailsQueued: Int,
)

/** Live delivery status of a campaign (EMAIL channel). */
data class CampaignStatus(
    val campaignId: String,
    val status: String,
    val total: Int,
    val queued: Int,
    val sent: Int,
    val delivered: Int,
    val failed: Int,
    val pending: Int,
)

/**
 * Minimal HTTP client for the campaign-platform backend.
 *
 * Uses HttpURLConnection + org.json (both are part of Android), so NO new
 * dependencies are needed. All methods are blocking - call them on Dispatchers.IO.
 */
object CampaignApi {

    fun createCampaign(
        baseUrl: String,
        apiKey: String,
        emails: List<String>,
        subject: String,
        html: String,
        senderName: String?,
    ): CampaignInfo {
        val body = JSONObject().apply {
            put("apiKey", apiKey)
            put("emails", emails) // becomes a JSONArray
            put("subject", subject)
            put("html", html)
            if (!senderName.isNullOrBlank()) put("senderName", senderName)
        }.toString()

        val json = request(
            method = "POST",
            urlString = "$baseUrl/api/mobile/campaigns",
            body = body,
            apiKey = apiKey,
        )
        return CampaignInfo(
            campaignId = json.getString("campaignId"),
            emailsQueued = json.optInt("emailsQueued", 0),
        )
    }

    fun getStatus(baseUrl: String, apiKey: String, campaignId: String): CampaignStatus {
        val json = request(
            method = "GET",
            urlString = "$baseUrl/api/mobile/campaigns/$campaignId?apiKey=$apiKey",
            body = null,
            apiKey = apiKey,
        )
        val email = json.getJSONObject("email")
        return CampaignStatus(
            campaignId = json.getString("campaignId"),
            status = json.getString("status"),
            total = email.optInt("total", 0),
            queued = email.optInt("queued", 0),
            sent = email.optInt("sent", 0),
            delivered = email.optInt("delivered", 0),
            failed = email.optInt("failed", 0),
            pending = email.optInt("pending", 0),
        )
    }

    private fun request(method: String, urlString: String, body: String?, apiKey: String): JSONObject {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
            }
            conn.setRequestProperty("X-Api-Key", apiKey)
            conn.connect()

            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${text.take(300)}")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
