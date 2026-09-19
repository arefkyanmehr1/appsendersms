package com.example.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.PayLinkApplication
import com.example.core.network.ErrorType
import com.example.core.network.NetworkResult
import com.example.core.util.AppLogger
import com.example.data.model.VerifyPaymentRequest

class VerificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PayLinkApplication ?: return Result.failure()
        val repository = app.repository

        val queuedPayments = repository.getQueuedPayments()
        if (queuedPayments.isEmpty()) {
            return Result.success()
        }

        AppLogger.i("VerificationWorker processing ${queuedPayments.size} queued payments")

        var hasTransientError = false

        for (item in queuedPayments) {
            val orderId = item.orderId
            if (orderId.isNullOrBlank()) {
                repository.updateProcessedPaymentStatus(
                    id = item.id,
                    status = "FAILED",
                    errorMessage = "شماره سفارش نامشخص است"
                )
                continue
            }

            val request = VerifyPaymentRequest(
                orderId = orderId,
                amount = item.amount,
            )

            when (val result = repository.verifyPayment(request)) {
                is NetworkResult.Success -> {
                    AppLogger.i("Offline payment verified: orderId=$orderId")
                    repository.deleteProcessedPayment(item.id)
                    NotificationHelper.showPaymentVerifiedNotification(
                        context = applicationContext,
                        orderId = orderId,
                        amount = item.amount,
                        bankName = item.bankName,
                        trackingCode = item.trackingCode
                    )
                }

                is NetworkResult.Error -> {
                    when (result.errorType) {
                        ErrorType.CONFLICT -> {
                            repository.deleteProcessedPayment(item.id)
                        }
                        ErrorType.NO_INTERNET, ErrorType.TIMEOUT, ErrorType.SERVER_ERROR -> {
                            hasTransientError = true
                        }
                        else -> {
                            repository.updateProcessedPaymentStatus(
                                id = item.id,
                                status = "FAILED",
                                errorMessage = result.message
                            )
                        }
                    }
                }

                is NetworkResult.Exception -> {
                    hasTransientError = true
                }
            }
        }

        return if (hasTransientError) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
