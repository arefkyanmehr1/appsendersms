package com.example.sms

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.background.NotificationHelper
import com.example.background.VerificationWorker
import com.example.core.network.ErrorType
import com.example.core.network.NetworkResult
import com.example.core.util.AppLogger
import com.example.data.local.entity.ProcessedPaymentEntity
import com.example.data.model.PendingInvoice
import com.example.data.model.VerifyPaymentRequest
import com.example.data.repository.PayLinkRepository
import com.example.sms.matcher.MatchResult
import com.example.sms.matcher.PaymentMatcher
import com.example.sms.parser.ParserRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class SmsProcessingCoordinator(
    private val context: Context,
    private val repository: PayLinkRepository
) {

    companion object {
        // Global sequential Mutex to process concurrent SMS one by one in FIFO order without race conditions
        private val processingMutex = Mutex()

        // Set of Order IDs that have been verified or settled to prevent double-matching in high-concurrency bursts
        val settledOrderIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

        fun markOrderSettled(orderId: String) {
            settledOrderIds.add(orderId)
        }

        fun clearSettledMemory() {
            settledOrderIds.clear()
        }
    }

    suspend fun processIncomingSms(sender: String?, body: String): Unit = withContext(Dispatchers.IO) {
        processingMutex.withLock {
            AppLogger.i("Incoming SMS received for processing from: $sender")

            // Step 1: Parse candidate
            val parsedPayment = ParserRegistry.parse(sender, body)
            if (parsedPayment == null) {
                AppLogger.d("SMS does not match any payment pattern. Ignored.")
                return@withLock
            }

            AppLogger.i("Payment candidate identified: amount=${parsedPayment.amount}, bank=${parsedPayment.bankName}, hash=${parsedPayment.smsHash.take(8)}...")

            // Step 2: Check local duplicate protection
            val existing = repository.findProcessedPaymentByHash(parsedPayment.smsHash)
            if (existing != null) {
                AppLogger.w("Payment with hash ${parsedPayment.smsHash.take(8)}... was already processed locally.")
                return@withLock
            }

            // Step 3: Fetch pending invoices (API or local cache fallback)
            val pendingInvoicesResult = repository.getPendingInvoices(limit = 50)
            val allInvoices = when (pendingInvoicesResult) {
                is NetworkResult.Success -> pendingInvoicesResult.data
                else -> emptyList()
            }

            // Filter out invoices that are already settled in memory to guarantee zero collision for identical amounts
            var remainingCandidates: List<PendingInvoice> = allInvoices.filterNot { invoice ->
                settledOrderIds.contains(invoice.orderId)
            }

            var paymentProcessed = false

            // Multi-attempt matching loop: if an invoice was already settled on backend (duplicate),
            // immediately advance to the next candidate with the same price!
            while (!paymentProcessed && remainingCandidates.isNotEmpty()) {
                when (val matchResult = PaymentMatcher.match(parsedPayment, remainingCandidates)) {
                    is MatchResult.Matched -> {
                        val matchedInvoice = matchResult.invoice
                        AppLogger.i("Matched invoice candidate orderId=${matchedInvoice.orderId} for amount=${parsedPayment.amount}")

                        val verifyReq = VerifyPaymentRequest(
                            orderId = matchedInvoice.orderId,
                            amount = parsedPayment.amount,
                            bankName = parsedPayment.bankName,
                            trackingCode = parsedPayment.trackingCode,
                            cardLast4 = parsedPayment.cardLast4,
                            rawSmsHash = parsedPayment.smsHash
                        )

                        // Attempt verification
                        when (val verifyResult = repository.verifyPayment(verifyReq)) {
                            is NetworkResult.Success -> {
                                val verifyData = verifyResult.data
                                val isBackendDuplicate = verifyData.duplicate == true

                                if (isBackendDuplicate) {
                                    // This particular invoice was already paid on the server.
                                    // Mark it settled and immediately try the next pending invoice with the same amount!
                                    AppLogger.w("Invoice ${matchedInvoice.orderId} was already paid on server (duplicate). Advancing to next invoice.")
                                    markOrderSettled(matchedInvoice.orderId)
                                    remainingCandidates = remainingCandidates.filterNot { it.orderId == matchedInvoice.orderId }
                                    continue
                                }

                                // Confirmed fresh verification by backend!
                                AppLogger.i("Backend confirmed verification for orderId=${matchedInvoice.orderId}")
                                markOrderSettled(matchedInvoice.orderId)

                                repository.recordProcessedPayment(
                                    ProcessedPaymentEntity(
                                        smsHash = parsedPayment.smsHash,
                                        amount = parsedPayment.amount,
                                        orderId = matchedInvoice.orderId,
                                        trackingCode = parsedPayment.trackingCode,
                                        cardLast4 = parsedPayment.cardLast4,
                                        bankName = parsedPayment.bankName,
                                        status = "VERIFIED",
                                        receivedAt = parsedPayment.receivedAt,
                                        verifiedAt = System.currentTimeMillis()
                                    )
                                )

                                // Trigger user notification
                                NotificationHelper.showPaymentVerifiedNotification(
                                    context = context,
                                    orderId = matchedInvoice.orderId,
                                    amount = parsedPayment.amount,
                                    bankName = parsedPayment.bankName,
                                    trackingCode = parsedPayment.trackingCode
                                )

                                // Refresh pending list immediately so the next queued SMS matches the subsequent invoice
                                repository.getPendingInvoices(limit = 50)
                                paymentProcessed = true
                            }

                            is NetworkResult.Error -> {
                                when (verifyResult.errorType) {
                                    ErrorType.CONFLICT -> {
                                        // Invoice already paid or expired on server.
                                        // Advance to next identical amount candidate!
                                        AppLogger.w("Invoice ${matchedInvoice.orderId} returned 409 Conflict. Trying next available order.")
                                        markOrderSettled(matchedInvoice.orderId)
                                        remainingCandidates = remainingCandidates.filterNot { it.orderId == matchedInvoice.orderId }
                                        continue
                                    }
                                    ErrorType.NO_INTERNET, ErrorType.TIMEOUT, ErrorType.SERVER_ERROR -> {
                                        // Enqueue for offline retry
                                        AppLogger.w("Network/server failure during verification. Enqueueing candidate.")
                                        markOrderSettled(matchedInvoice.orderId)
                                        repository.recordProcessedPayment(
                                            ProcessedPaymentEntity(
                                                smsHash = parsedPayment.smsHash,
                                                amount = parsedPayment.amount,
                                                orderId = matchedInvoice.orderId,
                                                trackingCode = parsedPayment.trackingCode,
                                                cardLast4 = parsedPayment.cardLast4,
                                                bankName = parsedPayment.bankName,
                                                status = "QUEUED",
                                                receivedAt = parsedPayment.receivedAt,
                                                errorMessage = "در انتظار اتصال به اینترنت جهت ارسال"
                                            )
                                        )
                                        scheduleVerificationWorker(context)
                                        paymentProcessed = true
                                    }
                                    else -> {
                                        AppLogger.e("Verification failed: ${verifyResult.message}")
                                        markOrderSettled(matchedInvoice.orderId)
                                        repository.recordProcessedPayment(
                                            ProcessedPaymentEntity(
                                                smsHash = parsedPayment.smsHash,
                                                amount = parsedPayment.amount,
                                                orderId = matchedInvoice.orderId,
                                                trackingCode = parsedPayment.trackingCode,
                                                cardLast4 = parsedPayment.cardLast4,
                                                bankName = parsedPayment.bankName,
                                                status = "FAILED",
                                                receivedAt = parsedPayment.receivedAt,
                                                errorMessage = verifyResult.message
                                            )
                                        )
                                        paymentProcessed = true
                                    }
                                }
                            }

                            is NetworkResult.Exception -> {
                                // Connection exception -> Queue
                                AppLogger.w("Exception during verification. Enqueueing candidate.", verifyResult.throwable)
                                markOrderSettled(matchedInvoice.orderId)
                                repository.recordProcessedPayment(
                                    ProcessedPaymentEntity(
                                        smsHash = parsedPayment.smsHash,
                                        amount = parsedPayment.amount,
                                        orderId = matchedInvoice.orderId,
                                        trackingCode = parsedPayment.trackingCode,
                                        cardLast4 = parsedPayment.cardLast4,
                                        bankName = parsedPayment.bankName,
                                        status = "QUEUED",
                                        receivedAt = parsedPayment.receivedAt,
                                        errorMessage = "در انتظار اینترنت برای تأیید"
                                    )
                                )
                                scheduleVerificationWorker(context)
                                paymentProcessed = true
                            }
                        }
                    }

                    is MatchResult.ExpiredInvoice -> {
                        AppLogger.w("Matching invoice orderId=${matchResult.invoice.orderId} is expired, advancing.")
                        remainingCandidates = remainingCandidates.filterNot { it.orderId == matchResult.invoice.orderId }
                    }

                    is MatchResult.NoMatch -> {
                        AppLogger.d("No matching invoice found for payment: ${matchResult.reason}")
                        repository.recordProcessedPayment(
                            ProcessedPaymentEntity(
                                smsHash = parsedPayment.smsHash,
                                amount = parsedPayment.amount,
                                orderId = null,
                                trackingCode = parsedPayment.trackingCode,
                                cardLast4 = parsedPayment.cardLast4,
                                bankName = parsedPayment.bankName,
                                status = "NO_MATCH",
                                receivedAt = parsedPayment.receivedAt,
                                errorMessage = matchResult.reason
                            )
                        )
                        paymentProcessed = true
                    }
                }
            }
        }
    }

    private fun scheduleVerificationWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<VerificationWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "PayLinkVerificationQueue",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
