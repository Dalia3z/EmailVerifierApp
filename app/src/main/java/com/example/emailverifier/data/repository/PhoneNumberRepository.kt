package com.example.emailverifier.data.repository

import com.example.emailverifier.data.local.PhoneNumberDao
import com.example.emailverifier.data.local.PhoneNumberEntity
import com.example.emailverifier.domain.model.VerificationStatus
import kotlinx.coroutines.flow.Flow

/**
 * The single gateway between the domain layer and the phone_numbers Room table.
 */
class PhoneNumberRepository(private val dao: PhoneNumberDao) {

    suspend fun importPhones(phones: List<PhoneNumberEntity>) = dao.insertAll(phones)

    /** Persists one validation result immediately (crash-safe progress). */
    suspend fun saveResult(
        id: Long,
        status: VerificationStatus,
        numberType: String?,
        formattedE164: String?,
        reason: String?,
    ) = dao.updateResult(id, status.name, numberType, formattedE164, reason)

    suspend fun getPending(): List<PhoneNumberEntity> =
        dao.getByStatus(VerificationStatus.PENDING.name)

    suspend fun getByStatus(status: VerificationStatus): List<PhoneNumberEntity> =
        dao.getByStatus(status.name)

    suspend fun countByStatus(status: VerificationStatus): Int =
        dao.countByStatus(status.name)

    fun observeCount(status: VerificationStatus): Flow<Int> =
        dao.observeCountByStatus(status.name)

    fun observeTotal(): Flow<Int> = dao.observeTotalCount()

    suspend fun clearAll() = dao.deleteAll()
}
