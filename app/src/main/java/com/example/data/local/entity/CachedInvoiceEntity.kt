package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_invoices",
    indices = [
        Index(value = ["orderId"], unique = true),
        Index(value = ["status"])
    ]
)
data class CachedInvoiceEntity(
    @PrimaryKey val id: Long,
    val orderId: String,
    val baseAmount: Long,
    val payableAmount: Long,
    val expectedAmount: Long,
    val status: String,
    val createdAt: String?,
    val expiresAt: String?,
    val remainingSeconds: Long,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerUsername: String? = null,
    val description: String? = null,
    val extraData: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
