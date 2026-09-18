package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.NotifiedInvoiceEntity

@Dao
interface NotifiedInvoiceDao {

    @Query("SELECT orderId FROM notified_invoices")
    suspend fun getAllNotifiedOrderIds(): List<String>

    @Query("SELECT COUNT(*) FROM notified_invoices WHERE orderId = :orderId")
    suspend fun isNotified(orderId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markNotified(entity: NotifiedInvoiceEntity)

    @Query("DELETE FROM notified_invoices WHERE notifiedAt < :cutoff")
    suspend fun cleanupOld(cutoff: Long)

    @Query("DELETE FROM notified_invoices")
    suspend fun deleteAll()
}
