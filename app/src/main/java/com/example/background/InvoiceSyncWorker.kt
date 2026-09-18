package com.example.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.PayLinkApplication
import com.example.core.network.NetworkResult
import com.example.core.util.AppLogger

class InvoiceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PayLinkApplication ?: return Result.failure()
        val repository = app.repository

        if (!app.secureStorage.hasApiKey()) {
            return Result.success()
        }

        AppLogger.d("Executing periodic InvoiceSyncWorker")
        return when (val result = repository.getPendingInvoices(limit = 50)) {
            is NetworkResult.Success -> {
                AppLogger.d("Pending invoices synced: ${result.data.size} items")
                Result.success()
            }
            is NetworkResult.Error -> Result.retry()
            is NetworkResult.Exception -> Result.retry()
        }
    }
}
