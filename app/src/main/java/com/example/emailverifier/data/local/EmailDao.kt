package com.example.emailverifier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [EmailEntity].
 *
 * All writes are suspend functions, so they run on Room's own executor and never
 * block the main thread. Every single result is persisted immediately (crash-safe
 * progress), and the Flow queries give the UI live counters.
 */
@Dao
interface EmailDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<EmailEntity>)

    @Query("UPDATE emails SET status = :status, reason = :reason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, reason: String?)

    @Query("SELECT * FROM emails WHERE status = :status ORDER BY id ASC")
    suspend fun getByStatus(status: String): List<EmailEntity>

    @Query("SELECT COUNT(*) FROM emails WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM emails WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM emails")
    fun observeTotalCount(): Flow<Int>

    @Query("DELETE FROM emails")
    suspend fun deleteAll()
}
