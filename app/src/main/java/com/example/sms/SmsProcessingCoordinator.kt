package com.example.sms

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.background.NotificationHelper
import com.example.background.VerificationWorker
import com.example.core.network.ErrorType
import com.example.core.network.NetworkResult
import com.example.core.util.AppLogger
import com.example.data.local.entity.ProcessedPaymentEntity
import com.example.data.model.VerifyPaymentRequest
import com.example.data.repository.PayLinkRepository
import com.example.sms.matcher.MatchResult
import com.example.sms.matcher.PaymentMatcher
import com.example.sms.parser.ParserRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SmsProcessingCoordinator(
    private val context: Context,
    private val repository: PayLinkRepository
) {

    companion object {
        // Global sequential Mutex to process concurrent SMS one by one in FIFO order without race conditions
        private val processingMutex = Mutex()
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
            val invoices = when (pendingInvoicesResult) {
                is NetworkResult.Success -> pendingInvoicesResult.data
                else -> emptyList()
            }

            // Step 4: Run Match Engine with FIFO priority for identical amounts
            when (val matchResult = PaymentMatcher.match(parsedPayment, invoices)) {
                is MatchResult.Matched -> {
                    val matchedInvoice = matchResult.invoice
                    AppLogger.i("Matched invoice orderId=${matchedInvoice.orderId} for amount=${parsedPayment.amount}")

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
                            // Confirmed verified by backend
                            AppLogger.i("Backend confirmed verification for orderId=${matchedInvoice.orderId}")
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
                        }

                        is NetworkResult.Error -> {
                            when (verifyResult.errorType) {
                                ErrorType.CONFLICT -> {
                                    AppLogger.w("Payment or invoice already paid (409)")
                                    repository.recordProcessedPayment(
                                        ProcessedPaymentEntity(
                                            smsHash = parsedPayment.smsHash,
                                            amount = parsedPayment.amount,
                                            orderId = matchedInvoice.orderId,
                                            trackingCode = parsedPayment.trackingCode,
                                            cardLast4 = parsedPayment.cardLast4,
                                            bankName = parsedPayment.bankName,
                                            status = "DUPLICATE",
                                            receivedAt = parsedPayment.receivedAt,
                                            errorMessage = "پرداخت قبلاً ثبت یا تسویه شده است"
                                        )
                                    )
                                }
                                ErrorType.NO_INTERNET, ErrorType.TIMEOUT, ErrorType.SERVER_ERROR -> {
                                    // Enqueue for offline retry
                                    AppLogger.w("Network/server failure during verification. Enqueueing candidate.")
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
                                }
                                else -> {
                                    AppLogger.e("Verification failed: ${verifyResult.message}")
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
                                }
                            }
                        }

                        is NetworkResult.Exception -> {
                            // Connection exception -> Queue
                            AppLogger.w("Exception during verification. Enqueueing candidate.", verifyResult.throwable)
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
                        }
                    }
                }

                is MatchResult.ExpiredInvoice -> {
                    AppLogger.w("Matching invoice orderId=${matchResult.invoice.orderId} is expired")
                    repository.recordProcessedPayment(
                        ProcessedPaymentEntity(
                            smsHash = parsedPayment.smsHash,
                            amount = parsedPayment.amount,
                            orderId = matchResult.invoice.orderId,
                            trackingCode = parsedPayment.trackingCode,
                            cardLast4 = parsedPayment.cardLast4,
                            bankName = parsedPayment.bankName,
                            status = "FAILED",
                            receivedAt = parsedPayment.receivedAt,
                            errorMessage = "فاکتور منقضی شده است"
                        )
                    )
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
        WorkManager.getInstance(context).enqueue(request)
    }
}
