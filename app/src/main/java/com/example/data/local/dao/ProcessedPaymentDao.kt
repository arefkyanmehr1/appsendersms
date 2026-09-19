package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ProcessedPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessedPaymentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: ProcessedPaymentEntity): Long

    @Query("SELECT * FROM processed_payments WHERE smsHash = :smsHash LIMIT 1")
    suspend fun findByHash(smsHash: String): ProcessedPaymentEntity?

    @Query("SELECT * FROM processed_payments WHERE status = 'QUEUED' ORDER BY receivedAt ASC")
    suspend fun getQueuedPayments(): List<ProcessedPaymentEntity>

    @Query("UPDATE processed_payments SET status = :status, verifiedAt = :verifiedAt, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, verifiedAt: Long?, errorMessage: String?)

    @Query("SELECT * FROM processed_payments ORDER BY receivedAt DESC LIMIT 100")
    suspend fun getAllList(): List<ProcessedPaymentEntity>

    @Query("SELECT * FROM processed_payments ORDER BY receivedAt DESC LIMIT 100")
    fun getAllFlow(): Flow<List<ProcessedPaymentEntity>>

    @Query("DELETE FROM processed_payments")
    suspend fun deleteAll()
}
