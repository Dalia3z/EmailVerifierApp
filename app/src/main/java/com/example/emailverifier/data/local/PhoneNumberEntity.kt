package com.example.emailverifier.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity: one row per phone number.
 *
 * @param id             auto-increment primary key - also preserves the original file
 *                       order and is the resume cursor.
 * @param rawNumber      the number exactly as it appeared in the file (cleaned).
 * @param defaultRegion  ISO 3166-1 alpha-2 country code used to parse local numbers
 *                       (e.g. "MA"). Ignored by libphonenumber for "+..." numbers.
 * @param status         one of [com.example.emailverifier.domain.model.VerificationStatus].name:
 *                       PENDING / VALID / INVALID / FAILED.
 * @param numberType     phone line type when valid (MOBILE / FIXED_LINE / ...), else null.
 * @param formattedE164  the number formatted as E.164 (+2126...) when valid, else null.
 * @param reason         human-readable explanation: for VALID "Mobile · +2126...",
 *                       for INVALID the parse/validation error.
 * @param timestamp      epoch millis of creation.
 */
@Entity(tableName = "phone_numbers")
data class PhoneNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawNumber: String,
    val defaultRegion: String,
    val status: String = "PENDING",
    val numberType: String? = null,
    val formattedE164: String? = null,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
