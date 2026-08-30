package com.example.emailverifier.data.repository

import com.example.emailverifier.data.local.EmailDao
import com.example.emailverifier.data.local.EmailEntity
import com.example.emailverifier.domain.model.VerificationStatus
import kotlinx.coroutines.flow.Flow

/**
 * The single gateway between the domain layer and the Room database.
 */
class EmailRepository(private val dao: EmailDao) {

    /** Inserts a fresh batch of emails as PENDING (done in one transaction). */
    suspend fun importEmails(emails: List<String>) {
        val now = System.currentTimeMillis()
        dao.insertAll(
            emails.map {
                EmailEntity(email = it, status = VerificationStatus.PENDING.name, timestamp = now)
            },
        )
    }

    /** Persists one verification result immediately (crash-safe progress). */
    suspend fun saveResult(id: Long, status: VerificationStatus, reason: String?) =
        dao.updateStatus(id, status.name, reason)

    /** All emails that still wait to be verified (resume cursor). */
    suspend fun getPending(): List<EmailEntity> = dao.getByStatus(VerificationStatus.PENDING.name)

    suspend fun getByStatus(status: VerificationStatus): List<EmailEntity> =
        dao.getByStatus(status.name)

    suspend fun countByStatus(status: VerificationStatus): Int =
        dao.countByStatus(status.name)

    fun observeCount(status: VerificationStatus): Flow<Int> =
        dao.observeCountByStatus(status.name)

    fun observeTotal(): Flow<Int> = dao.observeTotalCount()

    suspend fun clearAll() = dao.deleteAll()
}
