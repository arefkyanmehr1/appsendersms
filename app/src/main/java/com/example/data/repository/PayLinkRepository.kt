package com.example.data.repository

import android.content.Context
import com.example.background.NotificationHelper
import com.example.core.network.ErrorType
import com.example.core.network.NetworkResult
import com.example.core.security.SecureApiKeyStorage
import com.example.core.util.AppLogger
import com.example.core.util.CurrencyUtils
import com.example.core.util.DeviceUtils
import com.example.data.api.ApiClient
import com.example.data.api.PayLinkApi
import com.example.data.local.PayLinkDatabase
import com.example.data.local.entity.CachedInvoiceEntity
import com.example.data.local.entity.ProcessedPaymentEntity
import com.example.data.model.AccountStatusData
import com.example.data.model.HeartbeatRequest
import com.example.data.model.InvoiceDetail
import com.example.data.model.PendingInvoice
import com.example.data.model.TransactionHistoryData
import com.example.data.model.VerifyPaymentData
import com.example.data.model.VerifyPaymentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.Response

class PayLinkRepository(
    private val context: Context,
    private val secureStorage: SecureApiKeyStorage,
    private val database: PayLinkDatabase
) {

    private val api: PayLinkApi by lazy {
        ApiClient.getApi(secureStorage)
    }

    private val _isAccountActive = MutableStateFlow(true)
    val isAccountActive: StateFlow<Boolean> = _isAccountActive.asStateFlow()

    private val _lastHeartbeatSuccess = MutableStateFlow(false)
    val lastHeartbeatSuccess: StateFlow<Boolean> = _lastHeartbeatSuccess.asStateFlow()

    val cachedPendingInvoices: Flow<List<CachedInvoiceEntity>> =
        database.cachedInvoiceDao().getPendingInvoicesFlow()

    val processedPaymentsFlow: Flow<List<ProcessedPaymentEntity>> =
        database.processedPaymentDao().getAllFlow()

    suspend fun getAccountStatus(): NetworkResult<AccountStatusData> = withContext(Dispatchers.IO) {
        if (!DeviceUtils.isNetworkAvailable(context)) {
            return@withContext NetworkResult.Error(0, "عدم اتصال به اینترنت", ErrorType.NO_INTERNET)
        }
        try {
            val response = api.getAccountStatus()
            handleResponse(response) { body ->
                val accountData = body.data
                val isActive = accountData?.account?.isActive ?: 1
                _isAccountActive.value = (isActive == 1)
                if (accountData != null) {
                    NetworkResult.Success(accountData)
                } else {
                    NetworkResult.Error(200, body.message ?: "اطلاعات حساب دریافت نشد", ErrorType.UNKNOWN)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to get account status", e)
            mapException(e)
        }
    }

    suspend fun getPendingInvoices(limit: Int = 50): NetworkResult<List<PendingInvoice>> =
        withContext(Dispatchers.IO) {
            // Read current local cache first
            val localCached = try {
                database.cachedInvoiceDao().getPendingInvoicesList().map {
                    PendingInvoice(
                        id = it.id,
                        orderId = it.orderId,
                        baseAmount = it.baseAmount,
                        payableAmount = it.payableAmount,
                        expectedAmount = it.expectedAmount,
                        status = it.status,
                        createdAt = it.createdAt,
                        expiresAt = it.expiresAt,
                        remainingSeconds = it.remainingSeconds,
                        customerName = it.customerName,
                        customerPhone = it.customerPhone,
                        customerUsername = it.customerUsername,
                        description = it.description,
                        extraData = it.extraData
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }

            if (!DeviceUtils.isNetworkAvailable(context)) {
                return@withContext NetworkResult.Success(localCached)
            }

            try {
                // Periodic cleanup of rejected tracking table (older than 48 hours)
                val cutoff48h = System.currentTimeMillis() - (48 * 3600 * 1000L)
                database.rejectedInvoiceDao().cleanupOld(cutoff48h)

                val response = api.getPendingInvoices(limit)
                val body = response.body()

                if (response.isSuccessful && body?.data != null) {
                    val rawInvoices = body.data.invoices ?: emptyList()

                    // Filter out any invoice that the merchant has already rejected locally
                    val activeInvoices = rawInvoices.filter { inv ->
                        database.rejectedInvoiceDao().isRejected(inv.orderId) == 0
                    }

                    // Detect brand new pending invoices to notify merchant
                    val notificationPrefs = com.example.core.util.NotificationPreferences(context)
                    val configuredTimeout = notificationPrefs.invoiceTimeoutMinutes

                    for (invoice in activeInvoices) {
                        if (invoice.status.equals("pending", ignoreCase = true)) {
                            val alreadyNotified = database.notifiedInvoiceDao().isNotified(invoice.orderId) > 0
                            if (!alreadyNotified) {
                                val effectiveAmt = invoice.effectiveAmount
                                AppLogger.i("New pending invoice detected: ${invoice.orderId} - amount: $effectiveAmt")
                                val safeRemaining = com.example.core.util.InvoiceCountdownHelper
                                    .calculateSafeRemainingSeconds(invoice, configuredTimeout)
                                val minutesLeft = (safeRemaining / 60).toInt().coerceAtLeast(1)

                                NotificationHelper.showNewInvoicePendingNotification(
                                    context = context,
                                    orderId = invoice.orderId,
                                    amount = effectiveAmt,
                                    expiresInMinutes = minutesLeft
                                )
                                database.notifiedInvoiceDao().markNotified(
                                    com.example.data.local.entity.NotifiedInvoiceEntity(orderId = invoice.orderId)
                                )
                            }
                        }
                    }

                    // Periodic cleanup of notified tracking table for items older than 24 hours
                    val cutoff24h = System.currentTimeMillis() - 86400000L
                    database.notifiedInvoiceDao().cleanupOld(cutoff24h)

                    // Update local Room cache
                    val cacheEntities = activeInvoices.map {
                        CachedInvoiceEntity(
                            id = it.id,
                            orderId = it.orderId,
                            baseAmount = it.baseAmount,
                            payableAmount = it.effectiveAmount,
                            expectedAmount = it.expectedAmount,
                            status = it.status,
                            createdAt = it.createdAt,
                            expiresAt = it.expiresAt,
                            remainingSeconds = it.remainingSeconds,
                            customerName = it.customerName,
                            customerPhone = it.customerPhone,
                            customerUsername = it.customerUsername,
                            description = it.description,
                            extraData = it.extraData
                        )
                    }
                    database.cachedInvoiceDao().replaceAll(cacheEntities)
                    NetworkResult.Success(activeInvoices)
                } else {
                    // If server returned 500 or temporary error, fall back to local cache gracefully
                    if (localCached.isNotEmpty()) {
                        AppLogger.w("Server returned code ${response.code()} for pending invoices. Gracefully using local cache.")
                        NetworkResult.Success(localCached)
                    } else {
                        handleResponse(response) {
                            NetworkResult.Success(it.data?.invoices ?: emptyList())
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to sync pending invoices, checking cache fallback", e)
                if (localCached.isNotEmpty()) {
                    NetworkResult.Success(localCached)
                } else {
                    mapException(e)
                }
            }
        }

    suspend fun getInvoiceStatus(orderId: String): NetworkResult<InvoiceDetail> =
        withContext(Dispatchers.IO) {
            if (!DeviceUtils.isNetworkAvailable(context)) {
                return@withContext NetworkResult.Error(0, "عدم اتصال به اینترنت", ErrorType.NO_INTERNET)
            }
            try {
                val response = api.getInvoiceStatus(orderId)
                handleResponse(response) { body ->
                    if (body.data != null) {
                        NetworkResult.Success(body.data)
                    } else {
                        NetworkResult.Error(200, body.message ?: "فاکتور یافت نشد", ErrorType.NOT_FOUND)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to get invoice status", e)
                mapException(e)
            }
        }

    suspend fun verifyPayment(request: VerifyPaymentRequest): NetworkResult<VerifyPaymentData> =
        withContext(Dispatchers.IO) {
            if (!DeviceUtils.isNetworkAvailable(context)) {
                return@withContext NetworkResult.Error(0, "عدم اتصال به اینترنت", ErrorType.NO_INTERNET)
            }
            try {
                val response = api.verifyPayment(request)
                handleResponse(response) { body ->
                    if (body.success && body.data != null) {
                        NetworkResult.Success(body.data)
                    } else {
                        NetworkResult.Error(200, body.message ?: "خطا در تایید پرداخت", ErrorType.UNKNOWN)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to verify payment", e)
                mapException(e)
            }
        }

    suspend fun rejectInvoice(orderId: String, reason: String = "سفارش توسط پذیرنده رد شد"): NetworkResult<Boolean> =
        withContext(Dispatchers.IO) {
            // First: Immediately mark as rejected in persistent local database
            try {
                database.rejectedInvoiceDao().markRejected(
                    com.example.data.local.entity.RejectedInvoiceEntity(
                        orderId = orderId,
                        reason = reason
                    )
                )
                // Also store in processed_payments with REJECTED status so it's tracked in transaction history
                database.processedPaymentDao().insert(
                    com.example.data.local.entity.ProcessedPaymentEntity(
                        smsHash = "rejected_$orderId",
                        amount = 0L,
                        orderId = orderId,
                        bankName = "رد شده توسط پذیرنده",
                        status = "REJECTED",
                        receivedAt = System.currentTimeMillis(),
                        errorMessage = reason
                    )
                )
                database.cachedInvoiceDao().deleteByOrderId(orderId)
            } catch (e: Exception) {
                AppLogger.e("Error marking invoice as rejected locally", e)
            }

            if (!DeviceUtils.isNetworkAvailable(context)) {
                // Locally rejected successfully
                return@withContext NetworkResult.Success(true)
            }

            try {
                val response = api.rejectInvoice(
                    com.example.data.model.RejectInvoiceRequest(
                        orderId = orderId,
                        action = "reject",
                        status = "rejected",
                        reason = reason
                    )
                )
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    NetworkResult.Success(true)
                } else {
                    // Even if backend server returned non-200 or 500, local rejection was recorded
                    AppLogger.w("Backend responded with code ${response.code()} on reject, local rejection succeeded.")
                    NetworkResult.Success(true)
                }
            } catch (e: Exception) {
                AppLogger.w("Exception while notifying backend of invoice rejection, local rejection succeeded.", e)
                NetworkResult.Success(true)
            }
        }

    suspend fun sendHeartbeat(): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        if (!secureStorage.hasApiKey()) {
            return@withContext NetworkResult.Error(401, "کلید API موجود نیست", ErrorType.UNAUTHORIZED)
        }
        if (!DeviceUtils.isNetworkAvailable(context)) {
            return@withContext NetworkResult.Error(0, "عدم اتصال به اینترنت", ErrorType.NO_INTERNET)
        }

        try {
            val req = HeartbeatRequest(
                deviceId = DeviceUtils.getInstallationId(context),
                batteryLevel = DeviceUtils.getBatteryLevel(context),
                appVersion = DeviceUtils.getAppVersion(context),
                platform = "android"
            )
            val response = api.sendHeartbeat(req)
            val success = response.isSuccessful && (response.body()?.success == true)
            _lastHeartbeatSuccess.value = success
            if (success) {
                NetworkResult.Success(true)
            } else {
                val code = response.code()
                val errType = when (code) {
                    401 -> ErrorType.UNAUTHORIZED
                    403 -> {
                        _isAccountActive.value = false
                        ErrorType.ACCOUNT_DISABLED
                    }
                    else -> ErrorType.SERVER_ERROR
                }
                NetworkResult.Error(code, "ارسال Heartbeat ناموفق بود", errType)
            }
        } catch (e: Exception) {
            _lastHeartbeatSuccess.value = false
            AppLogger.e("Heartbeat error", e)
            mapException(e)
        }
    }

    suspend fun verifyInvoiceManually(
        orderId: String,
        amount: Long,
        trackingCode: String? = null,
        cardLast4: String? = null
    ): NetworkResult<VerifyPaymentData> = withContext(Dispatchers.IO) {
        val safeTracking = if (!trackingCode.isNullOrBlank()) {
            CurrencyUtils.normalizePersianArabicDigits(trackingCode.trim())
        } else {
            "MANUAL-${System.currentTimeMillis().toString().takeLast(6)}"
        }

        val cleanCard = cardLast4?.let { CurrencyUtils.normalizePersianArabicDigits(it.trim()) }
            ?.filter { it.isDigit() }
            ?.takeIf { it.length == 4 }
            ?: "0000"

        val request = VerifyPaymentRequest(
            orderId = orderId,
            amount = amount,
            bankName = "تأیید دستی پذیرنده",
            trackingCode = safeTracking,
            cardLast4 = cleanCard,
            rawSmsHash = com.example.core.security.HashUtils.sha256Hex("manual_${orderId}_${System.currentTimeMillis()}")
        )

        // Remove from local pending cache immediately for snappy UI
        try {
            database.cachedInvoiceDao().deleteByOrderId(orderId)
        } catch (_: Exception) {}

        val result = verifyPayment(request)

        // Record in processed payments table
        try {
            database.processedPaymentDao().insert(
                ProcessedPaymentEntity(
                    smsHash = request.rawSmsHash,
                    amount = amount,
                    orderId = orderId,
                    trackingCode = safeTracking,
                    bankName = "تأیید دستی پذیرنده",
                    cardLast4 = cleanCard,
                    status = "VERIFIED",
                    receivedAt = System.currentTimeMillis(),
                    verifiedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            AppLogger.e("Error recording manual payment locally", e)
        }

        result
    }

    suspend fun getTransactionHistory(
        page: Int = 1,
        limit: Int = 20
    ): NetworkResult<TransactionHistoryData> = withContext(Dispatchers.IO) {
        // 1. Read local processed transactions (manual verifications, matched payments, etc.)
        val localEntities: List<com.example.data.local.entity.ProcessedPaymentEntity> = try {
            database.processedPaymentDao().getAllList()
        } catch (e: Exception) {
            emptyList()
        }

        // 2. Read local rejected invoices so they definitely show up in transactions history
        val rejectedEntities: List<com.example.data.local.entity.RejectedInvoiceEntity> = try {
            database.rejectedInvoiceDao().getAllRejected()
        } catch (e: Exception) {
            emptyList()
        }

        val localItems: MutableList<com.example.data.model.TransactionItem> = mutableListOf()

        for (entity in localEntities) {
            val cleanStatus = when (entity.status.uppercase()) {
                "VERIFIED" -> "verified"
                "REJECTED" -> "rejected"
                "FAILED" -> "failed"
                "CANCELLED" -> "cancelled"
                "EXPIRED" -> "expired"
                else -> entity.status.lowercase()
            }
            val timeMillis = entity.verifiedAt ?: entity.receivedAt
            localItems.add(
                com.example.data.model.TransactionItem(
                    id = entity.id,
                    orderId = entity.orderId,
                    amount = entity.amount,
                    bankName = entity.bankName,
                    trackingCode = entity.trackingCode,
                    cardLast4 = entity.cardLast4,
                    status = cleanStatus,
                    rawSmsHash = entity.smsHash,
                    receivedAt = com.example.core.util.PersianDateUtils.formatToPersianDateTime(timeMillis)
                )
            )
        }

        val knownLocalOrders = localItems.mapNotNull { it.orderId }.toSet()
        var offsetCounter = 1L
        for (rejected in rejectedEntities) {
            if (!knownLocalOrders.contains(rejected.orderId)) {
                val uniqueId = Math.abs(rejected.orderId.hashCode().toLong()) * 1000L + (offsetCounter++)
                localItems.add(
                    com.example.data.model.TransactionItem(
                        id = uniqueId,
                        orderId = rejected.orderId,
                        amount = 0L,
                        bankName = null,
                        trackingCode = null,
                        cardLast4 = null,
                        status = "rejected",
                        rawSmsHash = null,
                        receivedAt = com.example.core.util.PersianDateUtils.formatToPersianDateTime(rejected.rejectedAt)
                    )
                )
            }
        }

        if (!DeviceUtils.isNetworkAvailable(context)) {
            val paged = localItems.drop((page - 1) * limit).take(limit)
            return@withContext NetworkResult.Success(
                TransactionHistoryData(page = page, limit = limit, total = localItems.size, transactions = paged)
            )
        }

        try {
            val response = api.getTransactionHistory(page, limit)
            handleResponse(response) { body ->
                val serverData = body.data ?: TransactionHistoryData(page = page, limit = limit, total = 0)
                val serverList = serverData.transactions

                // Merge server transactions with any local rejected or manual transactions not present on server
                val knownOrderIds = serverList.mapNotNull { it.orderId }.toSet()
                val additionalLocal = localItems.filter { it.orderId != null && !knownOrderIds.contains(it.orderId) }

                val seenOrderIds = mutableSetOf<String>()
                val mergedList = mutableListOf<com.example.data.model.TransactionItem>()
                for (item in (serverList + additionalLocal)) {
                    val oid = item.orderId
                    if (!oid.isNullOrBlank()) {
                        if (seenOrderIds.add(oid)) {
                            mergedList.add(item)
                        }
                    } else {
                        mergedList.add(item)
                    }
                }
                mergedList.sortByDescending { it.id }
                val totalCount = maxOf(serverData.total, mergedList.size)

                NetworkResult.Success(
                    serverData.copy(
                        total = totalCount,
                        transactions = mergedList
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to get transaction history from server, falling back to local records", e)
            if (localItems.isNotEmpty()) {
                val paged = localItems.drop((page - 1) * limit).take(limit)
                NetworkResult.Success(
                    TransactionHistoryData(page = page, limit = limit, total = localItems.size, transactions = paged)
                )
            } else {
                mapException(e)
            }
        }
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        if (!DeviceUtils.isNetworkAvailable(context)) return@withContext false
        try {
            val response = api.health()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    // Local DB Operations
    suspend fun recordProcessedPayment(entity: ProcessedPaymentEntity): Long = withContext(Dispatchers.IO) {
        database.processedPaymentDao().insert(entity)
    }

    suspend fun findProcessedPaymentByHash(hash: String): ProcessedPaymentEntity? = withContext(Dispatchers.IO) {
        database.processedPaymentDao().findByHash(hash)
    }

    suspend fun getQueuedPayments(): List<ProcessedPaymentEntity> = withContext(Dispatchers.IO) {
        database.processedPaymentDao().getQueuedPayments()
    }

    suspend fun updateProcessedPaymentStatus(
        id: Long,
        status: String,
        verifiedAt: Long? = null,
        errorMessage: String? = null
    ) = withContext(Dispatchers.IO) {
        database.processedPaymentDao().updateStatus(id, status, verifiedAt, errorMessage)
    }

    suspend fun clearAllArchives(): Boolean = withContext(Dispatchers.IO) {
        try {
            database.processedPaymentDao().deleteAll()
            database.rejectedInvoiceDao().clearAll()
            com.example.sms.SmsProcessingCoordinator.clearSettledMemory()
            AppLogger.i("Archives and local processed histories cleared successfully.")
            true
        } catch (e: Exception) {
            AppLogger.e("Failed to clear archives", e)
            false
        }
    }

    suspend fun resetRevenueStats(): Boolean = withContext(Dispatchers.IO) {
        try {
            database.processedPaymentDao().deleteAll()
            com.example.sms.SmsProcessingCoordinator.clearSettledMemory()
            AppLogger.i("Revenue stats reset locally.")
            true
        } catch (e: Exception) {
            AppLogger.e("Failed to reset revenue stats", e)
            false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        secureStorage.clearApiKey()
        database.cachedInvoiceDao().deleteAll()
        database.processedPaymentDao().deleteAll()
        database.notifiedInvoiceDao().deleteAll()
        _isAccountActive.value = true
        _lastHeartbeatSuccess.value = false
    }

    private suspend fun <T, R> handleResponse(
        response: Response<T>,
        onSuccess: suspend (T) -> NetworkResult<R>
    ): NetworkResult<R> {
        val code = response.code()
        if (response.isSuccessful) {
            val body = response.body()
            return if (body != null) {
                onSuccess(body)
            } else {
                NetworkResult.Error(code, "پاسخ سرور خالی است", ErrorType.UNKNOWN)
            }
        }

        val errType = when (code) {
            400 -> ErrorType.UNKNOWN
            401 -> ErrorType.UNAUTHORIZED
            403 -> {
                _isAccountActive.value = false
                ErrorType.ACCOUNT_DISABLED
            }
            404 -> ErrorType.NOT_FOUND
            409 -> ErrorType.CONFLICT
            422 -> ErrorType.VALIDATION_ERROR
            500, 502, 503, 504 -> ErrorType.SERVER_ERROR
            else -> ErrorType.UNKNOWN
        }

        val rawBody = response.errorBody()?.string()
        val errMsg = extractCleanErrorMessage(rawBody, code)
        return NetworkResult.Error(code, errMsg, errType)
    }

    private fun extractCleanErrorMessage(rawError: String?, code: Int): String {
        if (rawError.isNullOrBlank()) return "خطای سرور: $code"
        return try {
            val jsonObject = org.json.JSONObject(rawError)
            if (jsonObject.has("message")) {
                val msg = jsonObject.optString("message", "")
                if (msg.isNotBlank()) msg else rawError
            } else {
                rawError
            }
        } catch (_: Exception) {
            rawError
        }
    }

    private fun <T> mapException(e: Exception): NetworkResult<T> {
        return if (e is java.net.SocketTimeoutException) {
            NetworkResult.Error(0, "اتمام مهلت اتصال به سرور", ErrorType.TIMEOUT)
        } else if (e is java.net.UnknownHostException || e is java.io.IOException) {
            NetworkResult.Error(0, "عدم برقراری ارتباط با سرور", ErrorType.NO_INTERNET)
        } else {
            NetworkResult.Exception(e)
        }
    }
}
