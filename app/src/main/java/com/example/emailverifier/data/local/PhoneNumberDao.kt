package com.example.emailverifier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [PhoneNumberEntity].
 *
 * Mirrors [EmailDao]: immediate per-number persistence (crash-safe progress) and
 * Flow-based live counters for the UI.
 */
@Dao
interface PhoneNumberDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PhoneNumberEntity>)

    @Query(
        "UPDATE phone_numbers SET status = :status, numberType = :numberType, " +
            "formattedE164 = :formattedE164, reason = :reason WHERE id = :id",
    )
    suspend fun updateResult(
        id: Long,
        status: String,
        numberType: String?,
        formattedE164: String?,
        reason: String?,
    )

    @Query("SELECT * FROM phone_numbers WHERE status = :status ORDER BY id ASC")
    suspend fun getByStatus(status: String): List<PhoneNumberEntity>

    @Query("SELECT COUNT(*) FROM phone_numbers WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM phone_numbers WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM phone_numbers")
    fun observeTotalCount(): Flow<Int>

    @Query("DELETE FROM phone_numbers")
    suspend fun deleteAll()
}
