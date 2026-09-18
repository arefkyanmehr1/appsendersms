package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: T?
)

@JsonClass(generateAdapter = true)
data class AccountStatusData(
    @Json(name = "account") val account: AccountInfo?,
    @Json(name = "connection") val connection: ConnectionInfo?,
    @Json(name = "invoices") val invoices: InvoiceSummary?,
    @Json(name = "transactions") val transactions: TransactionSummary?,
    @Json(name = "webhooks") val webhooks: WebhookSummary?
)

@JsonClass(generateAdapter = true)
data class AccountInfo(
    @Json(name = "merchant_title") val merchantTitle: String?,
    @Json(name = "username") val username: String?,
    @Json(name = "role") val role: String?,
    @Json(name = "commission_percent") val commissionPercent: Double?,
    @Json(name = "credit_balance") val creditBalance: Long?,
    @Json(name = "is_active") val isActive: Int?,
    @Json(name = "invoice_timeout_minutes") val invoiceTimeoutMinutes: Int?
)

@JsonClass(generateAdapter = true)
data class ConnectionInfo(
    @Json(name = "last_heartbeat") val lastHeartbeat: String?,
    @Json(name = "battery_level") val batteryLevel: Int?,
    @Json(name = "device_id") val deviceId: String?,
    @Json(name = "status") val status: String?
)

@JsonClass(generateAdapter = true)
data class InvoiceSummary(
    @Json(name = "pending_count") val pendingCount: Int = 0,
    @Json(name = "paid_count") val paidCount: Int = 0,
    @Json(name = "expired_count") val expiredCount: Int = 0,
    @Json(name = "total_count") val totalCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class TransactionSummary(
    @Json(name = "verified_count") val verifiedCount: Int = 0,
    @Json(name = "verified_amount") val verifiedAmount: Long = 0L
)

@JsonClass(generateAdapter = true)
data class WebhookSummary(
    @Json(name = "pending_count") val pendingCount: Int = 0,
    @Json(name = "sent_count") val sentCount: Int = 0,
    @Json(name = "failed_count") val failedCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class PendingInvoicesData(
    @Json(name = "count") val count: Int = 0,
    @Json(name = "total_pending") val totalPending: Int = 0,
    @Json(name = "limit") val limit: Int = 50,
    @Json(name = "server_time") val serverTime: String?,
    @Json(name = "invoices") val invoices: List<PendingInvoice> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PendingInvoice(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "order_id") val orderId: String = "",
    @Json(name = "base_amount") val baseAmount: Long = 0L,
    @Json(name = "payable_amount") val payableAmount: Long = 0L,
    @Json(name = "expected_amount") val expectedAmount: Long = 0L,
    @Json(name = "amount") val amount: Long = 0L,
    @Json(name = "status") val status: String = "pending",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "remaining_seconds") val remainingSeconds: Long = 0L,
    @Json(name = "paid_at") val paidAt: String? = null,
    @Json(name = "webhook_status") val webhookStatus: String? = null,
    @Json(name = "customer_name") val customerName: String? = null,
    @Json(name = "customer_phone") val customerPhone: String? = null,
    @Json(name = "customer_username") val customerUsername: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "extra_data") val extraData: String? = null
) {
    /**
     * Resolves the actual payable amount considering payable_amount, base_amount or amount
     */
    val effectiveAmount: Long
        get() = when {
            payableAmount > 0L -> payableAmount
            expectedAmount > 0L -> expectedAmount
            amount > 0L -> amount
            baseAmount > 0L -> baseAmount
            else -> 0L
        }
}

@JsonClass(generateAdapter = true)
data class InvoiceDetail(
    @Json(name = "id") val id: Long,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "base_amount") val baseAmount: Long,
    @Json(name = "payable_amount") val payableAmount: Long,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "paid_at") val paidAt: String?,
    @Json(name = "card_last4") val cardLast4: String?,
    @Json(name = "bank_ref_id") val bankRefId: String?,
    @Json(name = "webhook_status") val webhookStatus: String?,
    @Json(name = "customer_name") val customerName: String? = null,
    @Json(name = "customer_phone") val customerPhone: String? = null,
    @Json(name = "customer_username") val customerUsername: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "extra_data") val extraData: String? = null
)

@JsonClass(generateAdapter = true)
data class SimpleActionResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class RejectInvoiceRequest(
    @Json(name = "order_id") val orderId: String,
    @Json(name = "action") val action: String = "reject",
    @Json(name = "status") val status: String = "rejected",
    @Json(name = "reason") val reason: String = "سفارش توسط پذیرنده از اپلیکیشن رد شد.",
    @Json(name = "amount") val amount: Long? = null
)

@JsonClass(generateAdapter = true)
data class VerifyPaymentRequest(
    @Json(name = "order_id") val orderId: String,
    @Json(name = "amount") val amount: Long,
    @Json(name = "bank_name") val bankName: String?,
    @Json(name = "tracking_code") val trackingCode: String?,
    @Json(name = "card_last4") val cardLast4: String?,
    @Json(name = "raw_sms_hash") val rawSmsHash: String
)

@JsonClass(generateAdapter = true)
data class VerifyPaymentData(
    @Json(name = "transaction_id") val transactionId: Long? = null,
    @Json(name = "order_id") val orderId: String? = null,
    @Json(name = "amount") val amount: Long? = null,
    @Json(name = "status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "battery_level") val batteryLevel: Int,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "platform") val platform: String = "android"
)

@JsonClass(generateAdapter = true)
data class TransactionHistoryData(
    @Json(name = "page") val page: Int = 1,
    @Json(name = "limit") val limit: Int = 20,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "transactions") val transactions: List<TransactionItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TransactionItem(
    @Json(name = "id") val id: Long,
    @Json(name = "order_id") val orderId: String?,
    @Json(name = "amount") val amount: Long,
    @Json(name = "commission_amount") val commissionAmount: Long? = null,
    @Json(name = "bank_name") val bankName: String?,
    @Json(name = "tracking_code") val trackingCode: String?,
    @Json(name = "card_last4") val cardLast4: String?,
    @Json(name = "status") val status: String,
    @Json(name = "raw_sms_hash") val rawSmsHash: String?,
    @Json(name = "received_at") val receivedAt: String?
)
