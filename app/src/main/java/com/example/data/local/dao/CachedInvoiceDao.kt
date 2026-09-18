package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.CachedInvoiceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedInvoiceDao {

    @Query("SELECT * FROM cached_invoices WHERE status = 'pending' ORDER BY expiresAt ASC")
    fun getPendingInvoicesFlow(): Flow<List<CachedInvoiceEntity>>

    @Query("SELECT * FROM cached_invoices WHERE status = 'pending' ORDER BY expiresAt ASC")
    suspend fun getPendingInvoicesList(): List<CachedInvoiceEntity>

    @Query("SELECT orderId FROM cached_invoices")
    suspend fun getAllOrderIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(invoices: List<CachedInvoiceEntity>)

    @Query("DELETE FROM cached_invoices")
    suspend fun deleteAll()

    @Query("DELETE FROM cached_invoices WHERE orderId = :orderId")
    suspend fun deleteByOrderId(orderId: String)

    @Transaction
    suspend fun replaceAll(invoices: List<CachedInvoiceEntity>) {
        deleteAll()
        insertAll(invoices)
    }
}
