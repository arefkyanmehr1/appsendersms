package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rejected_invoices")
data class RejectedInvoiceEntity(
    @PrimaryKey val orderId: String,
    val reason: String = "رد شده توسط پذیرنده",
    val rejectedAt: Long = System.currentTimeMillis()
)
