package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processed_payments",
    indices = [
        Index(value = ["smsHash"], unique = true),
        Index(value = ["orderId"]),
        Index(value = ["status"])
    ]
)
data class ProcessedPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsHash: String,
    val amount: Long,
    val orderId: String? = null,
    val trackingCode: String? = null,
    val cardLast4: String? = null,
    val bankName: String? = null,
    val status: String, // QUEUED, VERIFIED, DUPLICATE, ALREADY_PAID, FAILED, AMBIGUOUS, NO_MATCH
    val receivedAt: Long,
    val verifiedAt: Long? = null,
    val errorMessage: String? = null
)
