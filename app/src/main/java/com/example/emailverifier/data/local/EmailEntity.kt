package com.example.emailverifier.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity: one row per email address.
 *
 * @param id        auto-increment primary key - also preserves the original file order
 *                  and is the cursor used to resume a batch.
 * @param email     the raw (lower-cased, trimmed) email address.
 * @param status    one of [com.example.emailverifier.domain.model.VerificationStatus].name:
 *                  PENDING / VALID / INVALID / FAILED.
 * @param reason    short human-readable explanation, null while PENDING.
 * @param timestamp epoch millis of creation.
 */
@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val status: String = "PENDING",
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
