package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notified_invoices")
data class NotifiedInvoiceEntity(
    @PrimaryKey val orderId: String,
    val notifiedAt: Long = System.currentTimeMillis()
)
