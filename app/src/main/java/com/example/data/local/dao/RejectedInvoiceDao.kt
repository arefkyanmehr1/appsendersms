package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.RejectedInvoiceEntity

@Dao
interface RejectedInvoiceDao {

    @Query("SELECT COUNT(*) FROM rejected_invoices WHERE orderId = :orderId")
    suspend fun isRejected(orderId: String): Int

    @Query("SELECT orderId FROM rejected_invoices")
    suspend fun getAllRejectedOrderIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markRejected(entity: RejectedInvoiceEntity)

    @Query("DELETE FROM rejected_invoices WHERE rejectedAt < :cutoffTimestamp")
    suspend fun cleanupOld(cutoffTimestamp: Long)
}
