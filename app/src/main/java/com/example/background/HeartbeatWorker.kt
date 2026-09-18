package com.example.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.PayLinkApplication
import com.example.core.network.ErrorType
import com.example.core.network.NetworkResult
import com.example.core.util.AppLogger

class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PayLinkApplication ?: return Result.failure()
        val repository = app.repository

        if (!app.secureStorage.hasApiKey()) {
            return Result.success()
        }

        AppLogger.d("Executing periodic HeartbeatWorker")
        return when (val result = repository.sendHeartbeat()) {
            is NetworkResult.Success -> {
                AppLogger.d("Heartbeat sent successfully")
                Result.success()
            }
            is NetworkResult.Error -> {
                if (result.errorType == ErrorType.UNAUTHORIZED || result.errorType == ErrorType.ACCOUNT_DISABLED) {
                    AppLogger.w("Heartbeat aborted: ${result.errorType}")
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
            is NetworkResult.Exception -> Result.retry()
        }
    }
}
